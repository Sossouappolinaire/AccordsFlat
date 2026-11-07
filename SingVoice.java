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
        String pend = "";
        for (String w : text.replace('\'', ' ').replace('’', ' ').split("\\s+")) {
            String c = clean(w);
            if (c.isEmpty()) continue;
            if (!hasVowel(c)) { pend += c; continue; } // « l' », « d' »... collés au mot suivant
            for (String sy : splitSyllables(pend + c)) words.add(sy);
            pend = "";
        }
        if (!pend.isEmpty() && !words.isEmpty()) words.set(words.size() - 1, words.get(words.size() - 1) + pend);
        List<Note> out = new ArrayList<>();
        if (words.isEmpty()) return out;

        double[] weight = new double[words.size()];
        double total = 0;
        for (int i = 0; i < weight.length; i++) {
            weight[i] = 1;
            if (i == weight.length - 1) weight[i] = 2.2; // dernière syllabe tenue plus longtemps
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

    // ------------------------------------------------------------------ Chant (PSOLA)

    /** Hauteur (Hz) d'une trame de 35 ms commençant en s ; 0 si la trame n'est pas voisée. */
    static double f0Frame(float[] x, int s, int sr, float peak) {
        int frame = (int) (0.035 * sr);
        int minLag = sr / 450, maxLag = sr / 70;
        if (s < 0 || s + frame + maxLag + 2 >= x.length) return 0;
        double e0 = 0;
        for (int i = 0; i < frame; i++) e0 += x[s + i] * x[s + i];
        if (e0 / frame < Math.pow(peak * 0.08, 2)) return 0;
        double[] nc = new double[maxLag + 2];
        double best = 0;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double c = 0, e1 = 0;
            for (int i = 0; i < frame; i++) {
                c += x[s + i] * x[s + i + lag];
                e1 += x[s + i + lag] * x[s + i + lag];
            }
            nc[lag] = c / Math.sqrt(e0 * e1 + 1e-12);
            if (nc[lag] > best) best = nc[lag];
        }
        if (best < 0.55) return 0;
        for (int lag = minLag + 1; lag < maxLag; lag++) {
            if (nc[lag] >= 0.9 * best && nc[lag] >= nc[lag - 1] && nc[lag] >= nc[lag + 1])
                return (double) sr / lag;
        }
        return 0;
    }

    /** Repères de période (pitch marks) dans x[a..b[ ; period[i] = période locale en échantillons. */
    static double[][] pitchMarks(float[] x, int a, int b, int sr, float peak) {
        int hop = (int) (0.005 * sr);
        int nf = Math.max(1, (b - a) / hop + 1);
        double[] f = new double[nf];
        List<Double> ok = new ArrayList<>();
        for (int k = 0; k < nf; k++) {
            f[k] = f0Frame(x, a + k * hop - hop * 3, sr, peak);
            if (f[k] > 0) ok.add(f[k]);
        }
        if (ok.isEmpty()) return null;
        java.util.Collections.sort(ok);
        double med = ok.get(ok.size() / 2);
        for (int k = 0; k < nf; k++) if (f[k] <= 0 || f[k] > med * 1.6 || f[k] < med / 1.6) f[k] = med;
        List<Integer> marks = new ArrayList<>();
        List<Double> pers = new ArrayList<>();
        double T0 = sr / f[0];
        int best = a;
        for (int i = a; i < Math.min(b, a + (int) T0 + 1); i++) if (x[i] > x[best]) best = i;
        int pos = best;
        while (pos < b) {
            double T = sr / f[Math.min(nf - 1, (pos - a) / hop)];
            marks.add(pos);
            pers.add(T);
            int nxt = pos + (int) Math.round(T);
            int lo = Math.max(pos + 1, nxt - (int) (T * 0.2)), hi = Math.min(b - 1, nxt + (int) (T * 0.2));
            if (lo > hi) break;
            int bp = lo;
            for (int i = lo; i <= hi; i++) if (x[i] > x[bp]) bp = i;
            pos = bp;
        }
        if (marks.size() < 3) return null;
        double[][] r = new double[2][marks.size()];
        for (int i = 0; i < marks.size(); i++) {
            r[0][i] = marks.get(i);
            r[1][i] = pers.get(i);
        }
        return r;
    }

    /** Plus longue zone voisée (la voyelle) : {début, fin} en échantillons, ou null. */
    static int[] vowelRegion(float[] x, int sr, float peak) {
        int hop = (int) (0.01 * sr);
        int nf = x.length / hop;
        boolean[] v = new boolean[nf];
        for (int k = 0; k < nf; k++) v[k] = f0Frame(x, k * hop, sr, peak) > 0;
        for (int k = 1; k + 1 < nf; k++) if (!v[k] && v[k - 1] && v[k + 1]) v[k] = true; // comble les trous
        int bestA = -1, bestN = 0, cur = -1;
        for (int k = 0; k <= nf; k++) {
            boolean on = k < nf && v[k];
            if (on && cur < 0) cur = k;
            if (!on && cur >= 0) {
                if (k - cur > bestN) { bestN = k - cur; bestA = cur; }
                cur = -1;
            }
        }
        if (bestN < 4) return null; // moins de 40 ms : pas de voyelle exploitable
        int a = bestA * hop + (int) (0.012 * sr);
        int b = (bestA + bestN) * hop + (int) (0.02 * sr);
        b = Math.min(b, x.length);
        if (b - a < 0.04 * sr) return null;
        return new int[]{a, b};
    }

    /**
     * Fait chanter un mot : la consonne reste parlée, la voyelle est tenue sur la note
     * (hauteur FIXE grâce à PSOLA, léger vibrato sur les notes longues) pendant toute la durée voulue.
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
        float peak = 0;
        for (float v : x) peak = Math.max(peak, Math.abs(v));
        if (peak <= 0) return new float[0];
        float[] y = null;
        try {
            y = singPsola(x, rate, target, dur, peak);
        } catch (RuntimeException e) {
            y = null;
        }
        if (y == null) y = singResample(x, rate, target, dur);
        finish(y, rate);
        return y;
    }

    private static float[] singPsola(float[] x, int rate, double target, double dur, float peak) {
        int[] reg = vowelRegion(x, rate, peak);
        if (reg == null) return null;
        int a = reg[0], b = reg[1];
        double[][] pm = pitchMarks(x, a, b, rate, peak);
        if (pm == null) return null;
        double[] marks = pm[0], per = pm[1];
        int head = a, tail = x.length - b;
        int want = (int) (dur * rate);
        int nucOut = Math.max((int) (0.07 * rate), want - head - tail);
        double T0 = rate / target;
        float[] out = new float[head + nucOut + tail + (int) (T0 * 4) + 8];
        // consonne d'attaque (parlée) : on la garde telle quelle
        for (int i = 0; i < head; i++) out[i] = x[i];
        // voyelle tenue
        double stretchRatio = (double) nucOut / (b - a);
        double t = 0; // position de sortie (échantillons depuis le début de la voyelle)
        int markCount = marks.length;
        double vibDepth = Math.min(1.0, Math.max(0.0, (nucOut / (double) rate - 0.25) / 0.4)) * 0.011;
        while (t < nucOut) {
            double u = t / nucOut;
            double an = stretchRatio > 1.6 ? 0.15 + 0.7 * u : u; // longue note : on reste dans la partie stable
            double apos = a + an * (b - a);
            int k = 0;
            double bd = 1e18;
            for (int i = 0; i < markCount; i++) {
                double d = Math.abs(marks[i] - apos);
                if (d < bd) { bd = d; k = i; }
            }
            int half = (int) Math.round(per[k]);
            int len = half * 2 + 1;
            int outCenter = head + (int) t;
            for (int i = -half; i <= half; i++) {
                int si = (int) marks[k] + i, oi = outCenter + i;
                if (si < 0 || si >= x.length || oi < 0 || oi >= out.length) continue;
                double w = 0.5 + 0.5 * Math.cos(Math.PI * i / (half + 1));
                out[oi] += (float) (x[si] * w);
            }
            double sec = t / rate;
            double vib = 1.0 + vibDepth * Math.sin(2 * Math.PI * 5.4 * sec);
            t += T0 / vib;
        }
        // fin de mot (consonne finale) collée après la voyelle
        int dst = head + nucOut;
        for (int i = 0; i < tail; i++) {
            float g = i < 64 ? i / 64f : 1f;
            if (dst + i < out.length) out[dst + i] += x[b + i] * g;
        }
        return Arrays.copyOf(out, Math.min(out.length, Math.max(head + nucOut + tail, (int) (dur * rate))));
    }

    /** Ancienne méthode (rééchantillonnage) si la voyelle n'a pas pu être repérée. */
    private static float[] singResample(float[] x, int rate, double target, double dur) {
        double f0 = detectF0(x, rate);
        if (f0 <= 0) f0 = 140;
        double r = Math.max(0.45, Math.min(2.6, target / f0));
        float[] shifted = resampleBy(x, r);
        int want = Math.max((int) (dur * rate), (int) (shifted.length * 0.6));
        return stretch(shifted, rate, want);
    }

    /** Enveloppe douce + normalisation du mot. */
    private static void finish(float[] y, int rate) {
        int fi = (int) (0.008 * rate), fo = (int) (0.06 * rate);
        for (int i = 0; i < y.length; i++) {
            float g = 1f;
            if (i < fi) g = i / (float) fi;
            if (y.length - i < fo) g = Math.min(g, (y.length - i) / (float) fo);
            y[i] *= g;
        }
        float peak = 0;
        for (float v : y) peak = Math.max(peak, Math.abs(v));
        if (peak > 0) {
            float k = 0.5f / peak;
            for (int i = 0; i < y.length; i++) y[i] *= k;
        }
    }

    // ------------------------------------------------------------------ Syllabes françaises

    static boolean isV(String s, int i) {
        char c = s.charAt(i);
        if (c == 'u' && i > 0 && s.charAt(i - 1) == 'q') return false; // « qu » = consonne
        return "aeiouyàâäéèêëîïôöùûüœ".indexOf(c) >= 0;
    }

    static boolean hasVowel(String w) {
        String s = w.toLowerCase();
        for (int i = 0; i < s.length(); i++) if (isV(s, i)) return true;
        return false;
    }

    /** Coupe un mot en syllabes (règles simples du français). */
    public static List<String> splitSyllables(String word) {
        String s = word.toLowerCase();
        int n = s.length();
        List<int[]> g = new ArrayList<>();
        for (int i = 0; i < n; ) {
            if (isV(s, i)) {
                int j = i;
                while (j < n && isV(s, j)) j++;
                if (s.substring(i, j).equals("uia")) { g.add(new int[]{i, i + 1}); g.add(new int[]{i + 1, j}); }
                else g.add(new int[]{i, j});
                i = j;
            } else i++;
        }
        List<String> out = new ArrayList<>();
        if (g.size() <= 1) { out.add(word); return out; }
        // « e » muet final : n'a pas sa propre syllabe
        int[] last = g.get(g.size() - 1);
        String lg = s.substring(last[0], last[1]);
        if (g.size() > 1 && (lg.equals("e") && (last[1] == n || s.substring(last[1]).equals("s") || s.substring(last[1]).equals("nt")))) {
            g.remove(g.size() - 1);
        }
        int start = 0;
        for (int k = 0; k + 1 < g.size(); k++) {
            int e1 = g.get(k)[1], s2 = g.get(k + 1)[0];
            int cut;
            int nc = s2 - e1;
            if (nc <= 1) cut = s2 - nc;
            else {
                String two = s.substring(s2 - 2, s2);
                boolean digraph = two.equals("ch") || two.equals("ph") || two.equals("th") || two.equals("gn") || two.equals("qu");
                boolean liquid = "lr".indexOf(two.charAt(1)) >= 0 && "bcdfgptv".indexOf(two.charAt(0)) >= 0;
                cut = (digraph || liquid) ? s2 - 2 : e1 + 1;
                if (nc >= 3 && !(digraph || liquid)) {
                    String tr = s.substring(s2 - 3, s2);
                    if (tr.endsWith("ch") || tr.endsWith("ph") || tr.endsWith("gn") || tr.endsWith("th")) cut = s2 - 2;
                    else if ("lr".indexOf(tr.charAt(2)) >= 0 && "bcdfgptv".indexOf(tr.charAt(1)) >= 0) cut = s2 - 2;
                    else cut = e1 + 1;
                }
            }
            cut = Math.max(start + 1, Math.min(cut, n - 1));
            out.add(word.substring(start, cut));
            start = cut;
        }
        out.add(word.substring(start));
        return out;
    }

    // ------------------------------------------------------------------ Synthèse de chant (formants)

    /** Un morceau de syllabe : formants cibles et mélange voix / bruit. */
    private static class Seg {
        double f1, f2, f3, dur, voice, noise;
        boolean hp = true;   // bruit aigu (s, f, ch…) plutôt que souffle
        Seg(double f1, double f2, double f3, double dur, double voice, double noise) {
            this.f1 = f1; this.f2 = f2; this.f3 = f3; this.dur = dur; this.voice = voice; this.noise = noise;
        }
    }

    /** Formants {F1, F2, F3} d'une voyelle française ; nasale = F1 plus bas et son voilé. */
    private static double[] vowelFormants(String g, String coda, boolean nasal) {
        double[] r;
        if (g.contains("ou") || g.equals("oû")) r = new double[]{320, 800, 2600};
        else if (g.contains("eu") || g.contains("œ")) r = new double[]{420, 1500, 2500};
        else if (g.contains("eau") || g.contains("au") || g.equals("o") || g.equals("ô")) r = new double[]{450, 850, 2700};
        else if (g.contains("ai") || g.contains("ei") || g.equals("è") || g.equals("ê") || g.equals("ë")) r = new double[]{560, 1900, 2600};
        else if (g.equals("é") || g.equals("ée") || g.equals("ez") || g.equals("er")) r = new double[]{400, 2200, 2800};
        else if (g.startsWith("ui") || g.equals("u") || g.equals("û") || g.equals("ü") || g.equals("ù")) r = new double[]{300, 1750, 2150};
        else if (g.equals("i") || g.equals("î") || g.equals("ï") || g.equals("y") || g.equals("ie") || g.equals("ia")) r = new double[]{290, 2250, 3000};
        else if (g.equals("e")) r = coda.isEmpty() ? new double[]{450, 1500, 2500} : new double[]{540, 1850, 2600};
        else if (g.contains("oi") || g.contains("a") || g.equals("à") || g.equals("â")) r = new double[]{780, 1250, 2800};
        else if (g.contains("o")) r = new double[]{470, 900, 2700};
        else r = new double[]{700, 1200, 2700};
        if (nasal) {
            if (g.startsWith("a") || g.startsWith("e") || g.equals("â")) r = new double[]{620, 1000, 2600};
            else if (g.startsWith("o")) r = new double[]{480, 850, 2600};
            else if (g.startsWith("i") || g.equals("y")) r = new double[]{550, 1700, 2600};
            else r = new double[]{480, 1400, 2500};
        }
        return r;
    }

    /** Segment(s) d'une consonne ; null si muette. */
    private static void addConsonant(List<Seg> l, String c, String next) {
        switch (c) {
            case "f": case "ph": l.add(new Seg(1200, 2400, 3500, 0.075, 0, 0.30)); break;
            case "s": case "ç": case "z": case "x": l.add(new Seg(1400, 4500, 6000, 0.08, c.equals("z") ? 0.25 : 0, 0.36)); break;
            case "c": {
                boolean soft = next.startsWith("e") || next.startsWith("i") || next.startsWith("y");
                if (soft) l.add(new Seg(1400, 4500, 6000, 0.08, 0, 0.36));
                else { l.add(new Seg(500, 1500, 2500, 0.012, 0, 0)); l.add(new Seg(1500, 2500, 3500, 0.02, 0, 0.5)); }
                break;
            }
            case "ch": case "j": l.add(new Seg(1200, 2600, 3200, 0.08, c.equals("j") ? 0.25 : 0, 0.32)); break;
            case "v": l.add(new Seg(300, 1100, 2200, 0.06, 0.45, 0.16)); break;
            case "p": case "t": case "k": case "q": case "qu":
                l.add(new Seg(400, 1200, 2400, 0.014, 0, 0));
                l.add(new Seg(1500, 3000, 4000, 0.018, 0, c.equals("t") ? 0.55 : 0.45)); break;
            case "b": case "d": case "g":
                l.add(new Seg(250, 1000, 2200, 0.02, 0.35, 0));
                l.add(new Seg(900, 2000, 3000, 0.012, 0.3, 0.3)); break;
            case "m": l.add(new Seg(250, 1000, 2200, 0.07, 0.55, 0)); break;
            case "n": case "gn": l.add(new Seg(260, 1800, 2600, 0.065, 0.55, 0)); break;
            case "l": l.add(new Seg(360, 1300, 2700, 0.05, 0.75, 0)); break;
            case "r": l.add(new Seg(430, 1250, 1900, 0.05, 0.7, 0.05)); break;
            case "w": l.add(new Seg(310, 800, 2500, 0.04, 0.8, 0)); break;
            default: break;
        }
    }

    private static List<String> consonantUnits(String s) {
        List<String> u = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String two = i + 1 < s.length() ? s.substring(i, i + 2) : "";
            if (two.equals("ch") || two.equals("ph") || two.equals("gn") || two.equals("qu")) { u.add(two); i++; }
            else if (c == 'h' || c == '\'' || c == '’') continue;
            else u.add(String.valueOf(c));
        }
        return u;
    }

    /**
     * Chante une syllabe : consonne d'attaque, voyelle tenue sur la note (hauteur fixe, léger glissando
     * d'attaque et vibrato), consonne finale. Voix entièrement synthétique (formants).
     *
     * @param syl    la syllabe écrite (ex. « mon », « sau », « ta »)
     * @param midi   note
     * @param dur    durée (s)
     * @param scale  timbre : 0,88 homme · 1,0 femme · 1,12 enfant
     */
    public static float[] sungSyllable(String syl, int midi, double dur, double scale) {
        int rate = SongSynth.SR;
        String s = syl.toLowerCase();
        int a = -1, b = -1;
        for (int i = 0; i < s.length(); i++) if (isV(s, i)) { if (a < 0) a = i; b = i + 1; }
        String onset, grp, coda;
        if (a < 0) { onset = s; grp = "e"; coda = ""; }
        else { onset = s.substring(0, a); grp = s.substring(a, b); coda = s.substring(b); }
        boolean nasal = false;
        if (!coda.isEmpty() && (coda.charAt(0) == 'n' || coda.charAt(0) == 'm') && !grp.equals("ou")) {
            String rest = coda.substring(1);
            if (rest.isEmpty() || !isV(rest + "a", 0)) { nasal = true; coda = rest.length() > 0 && "bcdfgjklmnpqrstvwxz".indexOf(rest.charAt(0)) >= 0 && rest.length() > 1 ? rest.substring(1) : ""; }
        }
        if (grp.equals("er") || grp.equals("ez")) coda = "";
        if ((coda.equals("r") || coda.equals("z") || coda.equals("t") || coda.equals("s") || coda.equals("rs")) && (grp.equals("e") || grp.equals("é"))) {
            if (grp.equals("e") && !coda.isEmpty() && s.endsWith("er")) { grp = "é"; coda = ""; }
        }
        double[] vf = vowelFormants(grp, coda, nasal);

        List<Seg> segs = new ArrayList<>();
        List<String> on = consonantUnits(onset);
        for (int i = 0; i < on.size(); i++) addConsonant(segs, on.get(i), grp);
        double onTotal = 0;
        for (Seg g : segs) onTotal += g.dur;
        double maxOn = dur * 0.35;
        if (onTotal > maxOn && onTotal > 0) for (Seg g : segs) g.dur *= maxOn / onTotal;
        List<Seg> tail = new ArrayList<>();
        List<String> cu = consonantUnits(coda);
        boolean tailOk = dur > 0.22;
        if (tailOk) for (int i = 0; i < cu.size() && i < 2; i++) addConsonant(tail, cu.get(i), "");
        double tTotal = 0;
        for (Seg g : tail) tTotal += g.dur;
        double maxT = dur * 0.2;
        if (tTotal > maxT && tTotal > 0) for (Seg g : tail) g.dur *= maxT / tTotal;
        onTotal = 0;
        for (Seg g : segs) onTotal += g.dur;
        tTotal = 0;
        for (Seg g : tail) tTotal += g.dur;
        double vd = Math.max(0.05, dur - onTotal - tTotal);
        Seg vow = new Seg(vf[0], vf[1], vf[2], vd, nasal ? 0.8 : 1.0, 0);
        segs.add(vow);
        segs.addAll(tail);

        int n = Math.max(1, (int) (dur * rate));
        float[] y = new float[n];
        double f0 = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
        double c1 = vf[0] * scale, c2 = vf[1] * scale, c3 = vf[2] * scale; // formants courants
        double vG = 0, nG = 0;
        double[] z1 = new double[3], z2 = new double[3];
        double[] A = new double[3], B = new double[3], C = new double[3];
        double[] bw = {85, 110, 150};
        double[] gain = {1.0, 1.9, 1.8};
        double ph = 0, prevN = 0, lp = 0, prevFlow = 0, prevD = 0;
        long seed = syl.hashCode() * 2654435761L + midi;
        Random rnd = new Random(seed);
        int pos = 0;
        for (int si = 0; si < segs.size(); si++) {
            Seg g = segs.get(si);
            int len = (int) Math.round(g.dur * rate);
            if (si == segs.size() - 1) len = n - pos;
            double t1 = g.f1 * scale, t2 = g.f2 * scale, t3 = g.f3 * scale;
            for (int i = 0; i < len && pos < n; i++, pos++) {
                if ((pos & 15) == 0) {
                    c1 += (t1 - c1) * 0.2; c2 += (t2 - c2) * 0.2; c3 += (t3 - c3) * 0.2;
                    double[] ff = {c1, c2, Math.min(c3, rate * 0.45)};
                    for (int j = 0; j < 3; j++) {
                        double r = Math.exp(-Math.PI * bw[j] / rate);
                        A[j] = 2 * r * Math.cos(2 * Math.PI * Math.min(ff[j], rate * 0.45) / rate);
                        B[j] = -r * r;
                        C[j] = 1 - r;
                    }
                }
                vG += (g.voice - vG) * 0.01;
                nG += (g.noise - nG) * 0.05;
                double sec = pos / (double) rate;
                double scoop = Math.pow(2.0, -1.0 / 12.0 * Math.max(0, 1 - sec / 0.07));
                double vib = 1 + Math.min(1.0, Math.max(0, (sec - 0.18) / 0.35)) * 0.012 * Math.sin(2 * Math.PI * 5.4 * sec);
                ph += f0 * scoop * vib / rate;
                ph -= Math.floor(ph);
                // onde glottale de Rosenberg (ouverture lente, fermeture rapide) dérivée : timbre de voix riche
                double flow = ph < 0.6 ? 0.5 * (1 - Math.cos(Math.PI * ph / 0.6))
                        : ph < 0.7 ? Math.cos(Math.PI * 0.5 * (ph - 0.6) / 0.1) : 0;
                double d = (flow - prevFlow) * 22.0;
                prevFlow = flow;
                double src = d + 1.2 * (d - prevD) + (rnd.nextDouble() - 0.5) * 0.03; // légère accentuation des aigus
                prevD = d;
                double o = 0;
                for (int j = 0; j < 3; j++) {
                    double v = C[j] * src * vG + A[j] * z1[j] + B[j] * z2[j];
                    z2[j] = z1[j];
                    z1[j] = v;
                    o += v * gain[j];
                }
                double w = rnd.nextGaussian();
                double hpn = w - prevN;
                prevN = w;
                lp += (w - lp) * 0.3;
                o += nG * (g.hp ? hpn : lp) * 0.6;
                y[pos] = (float) o;
            }
        }
        // attaque / relâchement doux
        int fi = (int) (0.006 * rate), fo = (int) Math.min(0.06 * rate, n * 0.3);
        for (int i = 0; i < n; i++) {
            float gg = 1f;
            if (i < fi) gg = i / (float) fi;
            if (n - i < fo) gg = Math.min(gg, (n - i) / (float) fo);
            y[i] *= gg;
        }
        float peak = 0;
        for (float v : y) peak = Math.max(peak, Math.abs(v));
        if (peak > 0) { float k = 0.5f / peak; for (int i = 0; i < n; i++) y[i] *= k; }
        return y;
    }

    // ------------------------------------------------------------------ Voix de secours « la la la »
    /**
     * Voix chantée entièrement synthétique (voyelle « a » : onde riche filtrée par deux formants).
     * Sert quand la voix du téléphone n'est pas disponible : la chanson est toujours créée.
     *
     * @param kind 0 = grave (homme), 1 = médium, 2 = aigu (enfant)
     */
    public static float[] vowel(int midi, double dur, int kind) {
        int rate = SongSynth.SR;
        int n = Math.max(1, (int) (dur * rate));
        float[] y = new float[n];
        double f = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
        double[] fm = kind == 0 ? new double[]{650, 1080} : kind == 1 ? new double[]{800, 1250} : new double[]{950, 1500};
        double[] bw = {90, 110};
        double[] y1 = new double[2], y2 = new double[2];
        double[] cA = new double[2], cB = new double[2], cC = new double[2];
        for (int j = 0; j < 2; j++) {
            double r = Math.exp(-Math.PI * bw[j] / rate);
            cA[j] = 2 * r * Math.cos(2 * Math.PI * fm[j] / rate);
            cB[j] = -r * r;
            cC[j] = 1 - r;
        }
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double sec = i / (double) rate;
            double vib = 1 + Math.min(1.0, sec / 0.3) * 0.009 * Math.sin(2 * Math.PI * 5.3 * sec);
            ph += f * vib / rate;
            ph -= Math.floor(ph);
            double src = (1 - 2 * ph) * (1 - ph * 0.2); // dent de scie adoucie
            double o = 0;
            for (int j = 0; j < 2; j++) {
                double v = cC[j] * src + cA[j] * y1[j] + cB[j] * y2[j];
                y2[j] = y1[j];
                y1[j] = v;
                o += v * (j == 0 ? 1.0 : 0.6);
            }
            y[i] = (float) o;
        }
        finish(y, rate);
        return y;
    }
}
