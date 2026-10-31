package bj.radio;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Transforme la voix de synthèse (qui ne fait que lire) en voix qui chante :
 * chaque mot est lu séparément, puis sa hauteur est calée sur une note de la mélodie
 * et sa durée sur le rythme de la chanson.
 */
public class SingVoice {

    /** Un mot à chanter : texte, note (MIDI), début et durée en secondes. */
    public static class Note {
        public String word;
        public int midi;
        public double start, dur;
    }

    public static class Wav {
        public float[] pcm;
        public int sr;
    }

    // ------------------------------------------------------------------ Mélodie

    /** Nombre approximatif de syllabes d'un mot français. */
    static int syllables(String w) {
        String s = w.toLowerCase();
        int n = 0;
        boolean in = false;
        for (int i = 0; i < s.length(); i++) {
            boolean v = "aeiouyàâäéèêëîïôöùûüœ".indexOf(s.charAt(i)) >= 0;
            if (v && !in) n++;
            in = v;
        }
        if (n > 1 && (s.endsWith("e") || s.endsWith("es") || s.endsWith("ent"))) n--;
        return Math.max(1, n);
    }

    /** Garde seulement les lettres, chiffres et apostrophes. */
    static String clean(String w) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '\'' || c == '’') sb.append(c);
        }
        return sb.toString();
    }

    private static int nearest(List<Integer> pool, int target) {
        int best = pool.get(0);
        for (int m : pool) if (Math.abs(m - target) < Math.abs(best - target)) best = m;
        return best;
    }

    /**
     * Compose la mélodie d'une phrase.
     *
     * @param text      la phrase
     * @param startSec  début de la phrase (s)
     * @param lineSec   durée disponible (s)
     * @param barSec    durée d'une mesure (s)
     * @param firstBar  numéro de la première mesure de la phrase
     * @param lo,hi     tessiture (MIDI)
     * @param seed      graine pour varier la mélodie d'une phrase à l'autre
     */
    public static List<Note> melody(SongSynth.Params p, String text, double startSec, double lineSec,
                                    double barSec, int firstBar, int lo, int hi, long seed, boolean endOfVerse) {
        List<String> words = new ArrayList<>();
        for (String w : text.split("\\s+")) {
            String c = clean(w);
            if (!c.isEmpty()) words.add(c);
        }
        List<Note> out = new ArrayList<>();
        if (words.isEmpty()) return out;

        double[] weight = new double[words.size()];
        double total = 0;
        for (int i = 0; i < weight.length; i++) {
            weight[i] = syllables(words.get(i));
            if (i == weight.length - 1) weight[i] *= 1.8; // dernier mot tenu plus longtemps
            total += weight[i];
        }
        double usable = lineSec * 0.94;
        int[] scale = p.minor ? new int[]{0, 2, 3, 5, 7, 8, 10} : new int[]{0, 2, 4, 5, 7, 9, 11};
        List<Integer> scaleMidi = new ArrayList<>();
        for (int m = lo; m <= hi; m++) {
            int rel = ((m - p.root) % 12 + 12) % 12;
            for (int d : scale) if (d == rel) { scaleMidi.add(m); break; }
        }
        if (scaleMidi.isEmpty()) scaleMidi.add((lo + hi) / 2);

        Random rnd = new Random(seed);
        int cur = scaleMidi.get(scaleMidi.size() / 2);
        double t = 0;
        int lastBar = -1;
        for (int i = 0; i < words.size(); i++) {
            double d = usable * weight[i] / total;
            int bar = firstBar + (int) Math.min(Math.max(0, (t + 0.001) / barSec), 999);
            int[] pcs = SongSynth.chordPcs(p, Math.min(bar, p.bars - 1));
            List<Integer> chord = new ArrayList<>();
            for (int m = lo; m <= hi; m++) {
                int pc = ((m % 12) + 12) % 12;
                for (int c : pcs) if (c == pc) { chord.add(m); break; }
            }
            if (chord.isEmpty()) chord.add(cur);

            int note;
            boolean last = i == words.size() - 1;
            if (last && endOfVerse) {
                note = nearest(chord, (lo + hi) / 2); // retombe sur une note de l'accord
            } else if (i == 0 || bar != lastBar) {
                note = nearest(chord, cur + rnd.nextInt(5) - 2);
            } else {
                int idx = 0;
                for (int k = 0; k < scaleMidi.size(); k++)
                    if (Math.abs(scaleMidi.get(k) - cur) < Math.abs(scaleMidi.get(idx) - cur)) idx = k;
                int step = rnd.nextInt(5) - 2; // -2..+2 degrés
                if (last) step = -1;
                idx = Math.max(0, Math.min(scaleMidi.size() - 1, idx + step));
                note = scaleMidi.get(idx);
            }
            lastBar = bar;
            cur = note;

            Note n = new Note();
            n.word = words.get(i);
            n.midi = note;
            n.start = startSec + t;
            n.dur = d;
            out.add(n);
            t += d;
        }
        return out;
    }

    // ------------------------------------------------------------------ Lecture d'un WAV

    private static int le16(byte[] b, int o) { return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8); }
    private static int le32(byte[] b, int o) { return le16(b, o) | (le16(b, o + 2) << 16); }

    /** Lit un WAV PCM 16 bits (mono ou stéréo) produit par la synthèse vocale. */
    public static Wav readWav(File f) throws IOException {
        byte[] b;
        try (FileInputStream in = new FileInputStream(f)) {
            b = new byte[(int) f.length()];
            int off = 0;
            while (off < b.length) {
                int r = in.read(b, off, b.length - off);
                if (r < 0) break;
                off += r;
            }
        }
        if (b.length < 44 || b[0] != 'R' || b[1] != 'I') throw new IOException("WAV invalide");
        int ch = 1, sr = 22050, bits = 16, dataOff = -1, dataLen = 0;
        int o = 12;
        while (o + 8 <= b.length) {
            String id = new String(b, o, 4);
            int len = le32(b, o + 4);
            if (id.equals("fmt ")) {
                ch = le16(b, o + 10);
                sr = le32(b, o + 12);
                bits = le16(b, o + 22);
            } else if (id.equals("data")) {
                dataOff = o + 8;
                dataLen = len;
                if (dataLen <= 0 || dataOff + dataLen > b.length) dataLen = b.length - dataOff;
                break;
            }
            if (len < 0) break;
            o += 8 + len + (len & 1);
        }
        if (dataOff < 0 || bits != 16 || ch < 1) throw new IOException("Format WAV non pris en charge");
        int frames = dataLen / (2 * ch);
        float[] pcm = new float[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < ch; c++) sum += (short) le16(b, dataOff + (i * ch + c) * 2);
            pcm[i] = sum / (float) ch / 32768f;
        }
        Wav w = new Wav();
        w.pcm = pcm;
        w.sr = sr;
        return w;
    }

    // ------------------------------------------------------------------ Traitement du signal

    /** Retire le silence au début et à la fin. */
    static float[] trim(float[] x) {
        float peak = 0;
        for (float v : x) peak = Math.max(peak, Math.abs(v));
        if (peak < 1e-4f) return new float[0];
        float th = peak * 0.04f;
        int a = 0, z = x.length - 1;
        while (a < z && Math.abs(x[a]) < th) a++;
        while (z > a && Math.abs(x[z]) < th) z--;
        a = Math.max(0, a - 64);
        z = Math.min(x.length - 1, z + 64);
        return Arrays.copyOfRange(x, a, z + 1);
    }

    /** Hauteur moyenne (Hz) de la voix par autocorrélation ; 0 si introuvable. */
    static double detectF0(float[] x, int sr) {
        int frame = (int) (0.04 * sr), hop = (int) (0.01 * sr);
        int minLag = sr / 450, maxLag = sr / 70;
        List<Double> f0s = new ArrayList<>();
        float peak = 0;
        for (float v : x) peak = Math.max(peak, Math.abs(v));
        for (int s = 0; s + frame + maxLag < x.length; s += hop) {
            double e0 = 0;
            for (int i = 0; i < frame; i++) e0 += x[s + i] * x[s + i];
            if (e0 / frame < Math.pow(peak * 0.15, 2)) continue;
            double[] nc = new double[maxLag + 2];
            double best = 0;
            for (int lag = minLag; lag <= maxLag; lag++) {
                double c = 0, e1 = 0;
                for (int i = 0; i < frame; i += 2) {
                    c += x[s + i] * x[s + i + lag];
                    e1 += x[s + i + lag] * x[s + i + lag];
                }
                nc[lag] = c / Math.sqrt(e0 / 2 * e1 + 1e-12);
                if (nc[lag] > best) best = nc[lag];
            }
            // plus petit décalage qui est un sommet proche du meilleur (évite l'erreur d'octave)
            int bestLag = 0;
            for (int lag = minLag + 1; lag < maxLag; lag++) {
                if (nc[lag] >= 0.88 * best && nc[lag] >= nc[lag - 1] && nc[lag] >= nc[lag + 1]) { bestLag = lag; break; }
            }
            if (best > 0.6 && bestLag > 0) f0s.add((double) sr / bestLag);
        }
        if (f0s.size() < 3) return 0;
        java.util.Collections.sort(f0s);
        return f0s.get(f0s.size() / 2);
    }

    /** Change la hauteur d'un facteur r (et la durée de 1/r) par rééchantillonnage. */
    static float[] resampleBy(float[] x, double r) {
        int n = (int) (x.length / r);
        float[] y = new float[Math.max(1, n)];
        for (int i = 0; i < y.length; i++) {
            double pos = i * r;
            int a = (int) pos;
            int b = Math.min(a + 1, x.length - 1);
            double fr = pos - a;
            y[i] = (float) (x[Math.min(a, x.length - 1)] * (1 - fr) + x[b] * fr);
        }
        return y;
    }

    /** Allonge ou raccourcit sans changer la hauteur (WSOLA). */
    static float[] stretch(float[] x, int sr, int outLen) {
        if (outLen < 16) return new float[Math.max(outLen, 0)];
        int N = (int) (0.03 * sr) & ~1;
        int H = N / 2;
        int S = (int) (0.012 * sr);
        if (x.length < N * 2) {
            // trop court : simple étirement linéaire
            float[] y = new float[outLen];
            for (int i = 0; i < outLen; i++) {
                double pos = (double) i * (x.length - 1) / Math.max(1, outLen - 1);
                int a = (int) pos;
                int b = Math.min(a + 1, x.length - 1);
                y[i] = (float) (x[a] * (1 - (pos - a)) + x[b] * (pos - a));
            }
            return y;
        }
        double ratio = (double) x.length / outLen;
        float[] win = new float[N];
        for (int i = 0; i < N; i++) win[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * (i + 0.5) / N));
        float[] out = new float[outLen + N];
        int frames = outLen / H + 1;
        int prev = 0;
        for (int k = 0; k < frames; k++) {
            int outPos = k * H;
            int nominal = (int) Math.round(k * H * ratio);
            int pos;
            if (k == 0) {
                pos = 0;
            } else {
                int target = prev + H; // continuation naturelle de la trame précédente
                int lo = Math.max(0, nominal - S), hi = Math.min(x.length - N, nominal + S);
                if (target + N > x.length) target = x.length - N;
                if (hi < lo) { lo = hi = Math.max(0, Math.min(x.length - N, nominal)); }
                double bestC = -1e30;
                pos = lo;
                for (int c = lo; c <= hi; c += 2) {
                    double s = 0;
                    for (int i = 0; i < N; i += 4) s += x[c + i] * x[target + i];
                    if (s > bestC) { bestC = s; pos = c; }
                }
            }
            if (pos + N > x.length) pos = Math.max(0, x.length - N);
            for (int i = 0; i < N && outPos + i < out.length; i++) out[outPos + i] += x[pos + i] * win[i];
            prev = pos;
        }
        return Arrays.copyOf(out, outLen);
    }

    /**
     * Fait chanter un mot : hauteur calée sur la note, durée calée sur le rythme.
     *
     * @param word  voix parlée du mot
     * @param sr    fréquence d'échantillonnage du mot
     * @param midi  note à chanter
     * @param dur   durée voulue (s)
     * @return l'échantillon chanté à SongSynth.SR Hz
     */
    public static float[] sing(float[] word, int sr, int midi, double dur) {
        float[] x = trim(word);
        if (x.length < sr / 50) return new float[0];
        if (sr != SongSynth.SR) x = AudioDecoder.resample(x, sr, SongSynth.SR);
        int rate = SongSynth.SR;
        double target = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
        double f0 = detectF0(x, rate);
        if (f0 <= 0) f0 = 140; // voix parlée moyenne
        double r = Math.max(0.45, Math.min(2.6, target / f0));
        float[] shifted = resampleBy(x, r);
        int want = (int) (dur * rate);
        // on ne comprime pas trop (les consonnes resteraient inintelligibles)
        want = Math.max(want, (int) (shifted.length * 0.6));
        float[] y = stretch(shifted, rate, want);
        // enveloppe douce (évite les claquements)
        int fi = (int) (0.012 * rate), fo = (int) (0.05 * rate);
        for (int i = 0; i < y.length; i++) {
            float g = 1f;
            if (i < fi) g = i / (float) fi;
            if (y.length - i < fo) g = Math.min(g, (y.length - i) / (float) fo);
            y[i] *= g;
        }
        // normalise le mot
        float peak = 0;
        for (float v : y) peak = Math.max(peak, Math.abs(v));
        if (peak > 0) {
            float k = 0.5f / peak;
            for (int i = 0; i < y.length; i++) y[i] *= k;
        }
        return y;
    }
}
