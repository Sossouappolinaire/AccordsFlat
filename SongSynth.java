package bj.radio;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

/**
 * Synthétiseur : fabrique l'accompagnement d'une chanson (piano, basse, batterie, cordes, chœur)
 * à partir de la tonalité, de la mesure, du rythme (style) et du tempo.
 * Aucun fichier audio n'est nécessaire : tout est calculé en direct.
 */
public class SongSynth {

    public static final int SR = 22050;
    public static final String[] STYLES = {"Adoration", "Cantique", "Salsa", "Zouk", "Gospel", "Pop"};

    public static class Params {
        public int root;                 // 0 = Do ... 11 = Si
        public boolean minor;
        public int beats = 4, unit = 4;  // mesure : 4/4, 3/4, 2/2, 6/8...
        public String style = "Adoration";
        public int bpm = 80;             // noires par minute
        public boolean piano = true, bass = true, drums = false, strings = false;
        public int women, men, children; // chœur
        public int bars = 8;             // nombre total de mesures (intro + couplets + fin)
        public int[] barKind;            // par mesure : 0 chant, 1 solo de piano, 2 solo de batterie
        public boolean[] fill;           // par mesure : roulement de batterie sur la 2e moitié de la mesure
    }

    public static int defaultBpm(String style) {
        switch (style) {
            case "Adoration": return 68;
            case "Cantique": return 88;
            case "Salsa": return 100;
            case "Zouk": return 98;
            case "Gospel": return 110;
            default: return 100;
        }
    }

    private static final Random RND = new Random(7);

    // ---------- Harmonie ----------
    // {décalage en demi-tons depuis la tonique, qualité (0 majeur, 1 mineur)}
    private static int[][] progression(Params p) {
        if (!p.minor) {
            switch (p.style) {
                case "Cantique": return new int[][]{{0, 0}, {5, 0}, {0, 0}, {7, 0}};
                case "Salsa": return new int[][]{{0, 0}, {5, 0}, {7, 0}, {5, 0}};
                case "Zouk": return new int[][]{{9, 1}, {5, 0}, {0, 0}, {7, 0}};
                case "Gospel": return new int[][]{{0, 0}, {9, 1}, {2, 1}, {7, 0}};
                default: return new int[][]{{0, 0}, {7, 0}, {9, 1}, {5, 0}};
            }
        }
        switch (p.style) {
            case "Cantique": return new int[][]{{0, 1}, {5, 1}, {0, 1}, {7, 0}};
            case "Salsa": return new int[][]{{0, 1}, {5, 1}, {7, 0}, {0, 1}};
            case "Zouk": return new int[][]{{0, 1}, {8, 0}, {10, 0}, {7, 0}};
            case "Gospel": return new int[][]{{0, 1}, {3, 0}, {5, 1}, {7, 0}};
            default: return new int[][]{{0, 1}, {8, 0}, {3, 0}, {10, 0}};
        }
    }

    private static double freq(double midi) {
        return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0);
    }

    // ---------- Rythmes ----------
    private static int[] beatStarts(int spb, int beatSteps) {
        int n = spb / beatSteps;
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i * beatSteps;
        return r;
    }

    private static int[] all(int spb) {
        int[] r = new int[spb];
        for (int i = 0; i < spb; i++) r[i] = i;
        return r;
    }

    private static int[] pianoPattern(String style, int spb, int beatSteps) {
        if (spb == 8) {
            switch (style) {
                case "Cantique": case "Pop": return new int[]{0, 2, 4, 6};
                case "Salsa": return new int[]{0, 3, 4, 7};
                case "Zouk": return new int[]{1, 3, 5, 7};
                case "Gospel": return new int[]{0, 2, 3, 4, 6};
                default: return new int[]{0};
            }
        }
        int[] b = beatStarts(spb, beatSteps);
        if (style.equals("Zouk")) {
            int[] r = new int[b.length];
            for (int i = 0; i < b.length; i++) r[i] = b[i] + 1;
            return r;
        }
        return b;
    }

    private static int[] bassPattern(String style, int spb, int beatSteps) {
        if (style.equals("Adoration")) return new int[]{0};
        if (spb == 8) {
            switch (style) {
                case "Salsa": return new int[]{0, 3, 4, 7};
                case "Zouk": return new int[]{0, 2, 4, 6};
                case "Gospel": return new int[]{0, 3, 4, 6};
                default: return new int[]{0, 4};
            }
        }
        return beatStarts(spb, beatSteps);
    }

    /** Retourne {grosse caisse, caisse claire, charleston, claves}. */
    private static int[][] drumPattern(String style, int spb, int beatSteps) {
        if (spb == 8) {
            switch (style) {
                case "Adoration": return new int[][]{{0}, {}, {2, 6}, {}};
                case "Cantique": return new int[][]{{0, 4}, {}, {0, 2, 4, 6}, {}};
                case "Salsa": return new int[][]{{0, 4}, {3, 7}, {1, 3, 5, 7}, {0, 3, 6}};
                case "Zouk": return new int[][]{{0, 2, 4, 6}, {4}, {1, 3, 5, 7}, {}};
                case "Gospel": return new int[][]{{0, 3, 4}, {2, 6}, all(8), {}};
                default: return new int[][]{{0, 4}, {2, 6}, all(8), {}};
            }
        }
        int[] b = beatStarts(spb, beatSteps);
        int[] kick, snare;
        if (b.length == 4) { kick = new int[]{b[0], b[2]}; snare = new int[]{b[1], b[3]}; }
        else {
            kick = new int[]{b[0]};
            snare = new int[b.length - 1];
            for (int i = 1; i < b.length; i++) snare[i - 1] = b[i];
        }
        return new int[][]{kick, snare, all(spb), {}};
    }

    private static boolean has(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    // ---------- Instruments ----------
    private static void piano(float[] b, int start, int midi, double dur, double amp) {
        double f = freq(midi);
        double[] hA = {1.0, 0.55, 0.30, 0.15};
        double decay = 2.0 + midi / 35.0;
        int len = (int) ((dur + 0.5) * SR);
        for (int i = 0; i < len; i++) {
            int k = start + i;
            if (k >= b.length) break;
            double t = i / (double) SR;
            double env = Math.exp(-t * decay) * Math.min(1.0, t / 0.004);
            if (t > dur) env *= Math.exp(-(t - dur) * 14.0);
            double s = 0;
            for (int h = 0; h < hA.length; h++) s += hA[h] * Math.sin(2 * Math.PI * f * (h + 1) * t);
            b[k] += (float) (amp * env * s);
        }
    }

    private static void bass(float[] b, int start, int midi, double dur, double amp) {
        double f = freq(midi);
        int len = (int) ((dur + 0.2) * SR);
        for (int i = 0; i < len; i++) {
            int k = start + i;
            if (k >= b.length) break;
            double t = i / (double) SR;
            double env = Math.min(1.0, t / 0.01) * Math.exp(-t * 2.5);
            if (t > dur) env *= Math.exp(-(t - dur) * 25.0);
            double s = Math.sin(2 * Math.PI * f * t) + 0.45 * Math.sin(4 * Math.PI * f * t)
                    + 0.15 * Math.sin(6 * Math.PI * f * t);
            b[k] += (float) (amp * env * s);
        }
    }

    /** Voix de pad : cordes (formant = false) ou voix « aaah » du chœur (formant = true). */
    private static void voice(float[] b, int start, double f, double dur, double amp,
                              double f1, double f2, double phase, boolean formant, double attack) {
        int nh = (int) Math.max(2, Math.min(formant ? 9 : 6, 6000.0 / f));
        double[] w = new double[nh + 1];
        for (int h = 1; h <= nh; h++) {
            double fh = f * h;
            double g = 1.0 / h;
            if (formant) {
                g *= Math.exp(-Math.pow((fh - f1) / 280.0, 2)) + 0.6 * Math.exp(-Math.pow((fh - f2) / 380.0, 2)) + 0.04;
            }
            w[h] = g;
        }
        int len = (int) ((dur + 0.4) * SR);
        for (int i = 0; i < len; i++) {
            int k = start + i;
            if (k >= b.length) break;
            double t = i / (double) SR;
            double env = Math.min(1.0, t / attack);
            if (t > dur) env *= Math.exp(-(t - dur) * 9.0);
            double vib = 1.0 + (formant ? 0.005 : 0.002) * Math.sin(2 * Math.PI * 5.2 * t + phase);
            double s = 0;
            for (int h = 1; h <= nh; h++) s += w[h] * Math.sin(2 * Math.PI * f * h * vib * t + phase * h);
            b[k] += (float) (amp * env * s);
        }
    }

    private static void kick(float[] b, int start, double amp) {
        int len = (int) (0.28 * SR);
        double ph = 0;
        for (int i = 0; i < len; i++) {
            int k = start + i;
            if (k >= b.length) break;
            double t = i / (double) SR;
            double f = 45 + 95 * Math.exp(-t * 32);
            ph += 2 * Math.PI * f / SR;
            b[k] += (float) (amp * Math.sin(ph) * Math.exp(-t * 13));
        }
    }

    private static void snare(float[] b, int start, double amp) {
        int len = (int) (0.22 * SR);
        for (int i = 0; i < len; i++) {
            int k = start + i;
            if (k >= b.length) break;
            double t = i / (double) SR;
            double n = RND.nextDouble() * 2 - 1;
            b[k] += (float) (amp * (0.7 * n * Math.exp(-t * 22) + 0.4 * Math.sin(2 * Math.PI * 190 * t) * Math.exp(-t * 30)));
        }
    }

    private static void hat(float[] b, int start, double amp) {
        int len = (int) (0.06 * SR);
        double prev = 0;
        for (int i = 0; i < len; i++) {
            int k = start + i;
            if (k >= b.length) break;
            double t = i / (double) SR;
            double n = RND.nextDouble() * 2 - 1;
            double hp = n - prev;
            prev = n;
            b[k] += (float) (amp * hp * Math.exp(-t * 80));
        }
    }

    private static void clave(float[] b, int start, double amp) {
        int len = (int) (0.08 * SR);
        for (int i = 0; i < len; i++) {
            int k = start + i;
            if (k >= b.length) break;
            double t = i / (double) SR;
            b[k] += (float) (amp * Math.sin(2 * Math.PI * 2500 * t) * Math.exp(-t * 55));
        }
    }

    private static void tom(float[] b, int start, double f0, double amp) {
        int len = (int) (0.35 * SR);
        double ph = 0;
        for (int i = 0; i < len; i++) {
            int k = start + i;
            if (k >= b.length) break;
            double t = i / (double) SR;
            double f = f0 * (1.0 + 0.6 * Math.exp(-t * 22));
            ph += 2 * Math.PI * f / SR;
            b[k] += (float) (amp * Math.sin(ph) * Math.exp(-t * 8));
        }
    }

    private static void crash(float[] b, int start, double amp) {
        int len = (int) (1.1 * SR);
        double prev = 0;
        for (int i = 0; i < len; i++) {
            int k = start + i;
            if (k >= b.length) break;
            double t = i / (double) SR;
            double n = RND.nextDouble() * 2 - 1;
            double hp = n - prev;
            prev = n;
            b[k] += (float) (amp * hp * Math.exp(-t * 3.2));
        }
    }

    /** Roulement : « hits » coups répartis sur durSec, de plus en plus forts (caisse claire ou toms qui descendent). */
    private static void roll(float[] b, int startSample, double durSec, int hits, double a0, double a1, boolean toms) {
        double[] tomF = {230, 170, 120};
        for (int i = 0; i < hits; i++) {
            int at = startSample + (int) (i * durSec / hits * SR);
            double a = a0 + (a1 - a0) * i / Math.max(1, hits - 1);
            if (toms) tom(b, at, tomF[Math.min(2, i * 3 / hits)], a);
            else snare(b, at, a);
        }
    }

    /** Une mesure de solo de batterie ; la dernière mesure du solo est un grand roulement terminé par une cymbale. */
    private static void drumSolo(float[] mix, int barStart, double stepSec, double barSec, int spb,
                                 int[][] dr, int pos, int runLen, boolean runLast) {
        if (runLast) {
            roll(mix, barStart, barSec / 2, spb, 0.12, 0.32, false);
            roll(mix, barStart + (int) (barSec / 2 * SR), barSec / 2, spb, 0.35, 0.6, true);
            kick(mix, barStart, 0.55);
            int next = barStart + (int) (barSec * SR);
            crash(mix, next, 0.35);
            kick(mix, next, 0.6);
            return;
        }
        int[] sn = dr[1].length > 0 ? dr[1] : new int[]{spb / 2};
        boolean busy = pos >= runLen / 2;
        for (int s = 0; s < spb; s++) {
            int at = barStart + (int) (s * stepSec * SR);
            if (has(dr[0], s)) kick(mix, at, 0.55);
            if (has(sn, s)) snare(mix, at, 0.3);
            hat(mix, at, 0.09);
            if (has(dr[3], s)) clave(mix, at, 0.18);
            if (busy) {
                snare(mix, at + (int) (stepSec * SR * 0.5), 0.12);
                if (s % 2 == 1) tom(mix, at, 150 + (s % 3) * 40, 0.3);
            }
        }
        if (pos == 0) crash(mix, barStart, 0.3);
    }

    // ---------- Rendu complet ----------
    public static short[] render(Params p) {
        int spb = p.unit == 8 ? p.beats : (p.unit == 2 ? p.beats * 4 : p.beats * 2); // pas (croches) par mesure
        int beatSteps = p.unit == 8 ? 3 : (p.unit == 2 ? 4 : 2);
        double barSec = p.beats * (4.0 / p.unit) * 60.0 / p.bpm;
        double stepSec = barSec / spb;
        int n = (int) ((p.bars * barSec + 1.5) * SR);
        float[] mix = new float[n];

        int[][] prog = progression(p);
        int[] pianoSteps = pianoPattern(p.style, spb, beatSteps);
        int[] bassSteps = bassPattern(p.style, spb, beatSteps);
        int[][] dr = drumPattern(p.style, spb, beatSteps);
        boolean arp = p.style.equals("Adoration");
        boolean choir = p.women + p.men + p.children > 0;

        int[] kindArr = new int[p.bars];
        boolean[] fillArr = new boolean[p.bars];
        for (int i = 0; i < p.bars; i++) {
            if (p.barKind != null && i < p.barKind.length) kindArr[i] = p.barKind[i];
            if (p.fill != null && i < p.fill.length) fillArr[i] = p.fill[i];
        }
        // position de chaque mesure dans son « bloc » (suite de mesures de même nature)
        int[] posInRun = new int[p.bars], runLen = new int[p.bars];
        for (int i = 0; i < p.bars; ) {
            int j = i;
            while (j < p.bars && kindArr[j] == kindArr[i]) j++;
            for (int k = i; k < j; k++) { posInRun[k] = k - i; runLen[k] = j - i; }
            i = j;
        }
        Random solo = new Random(11);
        int[] scale = p.minor ? new int[]{0, 3, 5, 7, 10} : new int[]{0, 2, 4, 7, 9};
        int melPos = 5;
        int melBase = 57 + p.root;

        for (int bar = 0; bar < p.bars; bar++) {
            boolean last = bar == p.bars - 1;
            int kind = kindArr[bar];
            boolean runLast = posInRun[bar] == runLen[bar] - 1;
            int[] ch = last ? new int[]{0, p.minor ? 1 : 0} : prog[bar % prog.length];
            int pc = (p.root + ch[0]) % 12;
            int[] iv = ch[1] == 0 ? new int[]{0, 4, 7} : new int[]{0, 3, 7};
            int barStart = (int) (bar * barSec * SR);
            int rootMidi = 48 + pc;

            // Piano : accompagnement
            if (kind == 0 && p.piano) {
                if (last) {
                    piano(mix, barStart, rootMidi, barSec * 1.4, 0.16);
                    for (int t : iv) piano(mix, barStart, rootMidi + 12 + t, barSec * 1.4, 0.16);
                } else if (arp) {
                    piano(mix, barStart, rootMidi, barSec, 0.2);
                    for (int s = 0; s < spb; s++) {
                        int note = rootMidi + 12 + iv[s % 3] + ((s / 3) % 2 == 1 ? 12 : 0);
                        piano(mix, barStart + (int) (s * stepSec * SR), note, stepSec * 3, 0.15);
                    }
                } else {
                    piano(mix, barStart, rootMidi, barSec * 0.9, 0.18);
                    for (int s : pianoSteps) {
                        int at = barStart + (int) (s * stepSec * SR);
                        for (int t : iv) piano(mix, at, rootMidi + 12 + t, beatSteps * stepSec * 0.9, 0.13);
                    }
                }
            }

            // Piano : solo (main gauche en accords, main droite improvise sur la gamme pentatonique)
            if (kind == 1) {
                piano(mix, barStart, rootMidi, barSec * 0.9, 0.16);
                int[] ps = arp ? new int[]{0, beatSteps * 2 % spb} : pianoSteps;
                if (!runLast) {
                    for (int s : ps) {
                        int at = barStart + (int) (s * stepSec * SR);
                        for (int t : iv) piano(mix, at, rootMidi + 12 + t, beatSteps * stepSec * 0.9, 0.08);
                    }
                }
                if (runLast) {
                    piano(mix, barStart, rootMidi + 24, barSec * 1.4, 0.22);
                    piano(mix, barStart, rootMidi + 12 + iv[1], barSec * 1.4, 0.14);
                } else {
                    for (int s = 0; s < spb; s++) {
                        if (s != 0 && solo.nextInt(100) >= 78) continue;
                        melPos = Math.max(3, Math.min(9, melPos + solo.nextInt(5) - 2));
                        int midi = melBase + 12 * (melPos / 5) + scale[melPos % 5];
                        if (s == 0 && solo.nextInt(100) < 50) midi = rootMidi + 24 + iv[solo.nextInt(3)];
                        piano(mix, barStart + (int) (s * stepSec * SR), midi,
                                stepSec * (solo.nextBoolean() ? 1.6 : 0.9), 0.17);
                    }
                }
            }

            // Basse
            if (p.bass && kind != 2) {
                int bm = 36 + pc;
                if (last) {
                    bass(mix, barStart, bm, barSec * 1.2, 0.3);
                } else {
                    for (int i = 0; i < bassSteps.length; i++) {
                        int note = (i % 2 == 1 && !p.style.equals("Cantique")) ? bm + 7 : bm;
                        double d = p.style.equals("Adoration") ? barSec : beatSteps * stepSec * 0.9;
                        bass(mix, barStart + (int) (bassSteps[i] * stepSec * SR), note, d, 0.3);
                    }
                }
            }

            // Batterie
            if (kind == 2) {
                drumSolo(mix, barStart, stepSec, barSec, spb, dr, posInRun[bar], runLen[bar], runLast);
            } else if (p.drums && bar > 0) {
                boolean fill = fillArr[bar] && !last;
                for (int s = 0; s < spb; s++) {
                    int at = barStart + (int) (s * stepSec * SR);
                    if (last) { if (s == 0) kick(mix, at, 0.5); continue; }
                    if (fill && s >= spb / 2) continue;
                    if (has(dr[0], s)) kick(mix, at, p.style.equals("Adoration") ? 0.35 : 0.5);
                    if (has(dr[1], s)) snare(mix, at, p.style.equals("Salsa") ? 0.12 : 0.3);
                    if (has(dr[2], s)) hat(mix, at, p.style.equals("Adoration") ? 0.05 : 0.09);
                    if (has(dr[3], s)) clave(mix, at, 0.18);
                }
                if (fill) {
                    roll(mix, barStart + (int) (barSec / 2 * SR), barSec / 2, spb, 0.25, 0.5, true);
                    crash(mix, barStart + (int) (barSec * SR), 0.3);
                }
            }

            // Cordes
            if (p.strings && kind != 2) {
                for (int t : iv) {
                    voice(mix, barStart, freq(rootMidi + 12 + t), barSec * 1.02, 0.045, 0, 0, t, false, 0.4);
                }
            }

            // Chœur (« aaah »), seulement pendant le chant (pas pendant l'intro ni les solos)
            if (choir && bar > 0 && kind == 0) {
                int[] counts = {p.women, p.men, p.children};
                int[] base = {rootMidi + 12, rootMidi, rootMidi + 24};
                double[][] form = {{850, 1500}, {700, 1100}, {1000, 1700}};
                for (int g = 0; g < 3; g++) {
                    int c = counts[g];
                    if (c <= 0) continue;
                    int r = Math.min(c, 3);
                    double amp = 0.05 * Math.sqrt(c) / r;
                    for (int v = 0; v < r; v++) {
                        double detune = Math.pow(2.0, ((v - 1) * 7.0) / 1200.0);
                        double f = freq(base[g] + iv[(v + g) % 3]) * detune;
                        voice(mix, barStart, f, barSec * 1.02, amp, form[g][0], form[g][1], v * 1.7 + g, true, 0.25);
                    }
                }
            }
        }

        float peak = 0;
        for (float v : mix) peak = Math.max(peak, Math.abs(v));
        float gain = peak > 0 ? 0.9f / peak : 1f;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) out[i] = (short) Math.max(-32767, Math.min(32767, mix[i] * gain * 32767f));
        return out;
    }

    /** Écrit un fichier WAV (mono, 16 bits). */
    public static void writeWav(OutputStream os, short[] pcm) throws IOException {
        int dataLen = pcm.length * 2;
        byte[] h = new byte[44];
        put(h, 0, "RIFF");
        le32(h, 4, 36 + dataLen);
        put(h, 8, "WAVE");
        put(h, 12, "fmt ");
        le32(h, 16, 16);
        h[20] = 1;
        h[22] = 1;
        le32(h, 24, SR);
        le32(h, 28, SR * 2);
        h[32] = 2;
        h[34] = 16;
        put(h, 36, "data");
        le32(h, 40, dataLen);
        os.write(h);
        byte[] d = new byte[dataLen];
        for (int i = 0; i < pcm.length; i++) {
            d[2 * i] = (byte) (pcm[i] & 0xFF);
            d[2 * i + 1] = (byte) ((pcm[i] >> 8) & 0xFF);
        }
        os.write(d);
        os.flush();
    }

    private static void put(byte[] a, int off, String s) {
        for (int i = 0; i < s.length(); i++) a[off + i] = (byte) s.charAt(i);
    }

    private static void le32(byte[] a, int off, int v) {
        a[off] = (byte) v;
        a[off + 1] = (byte) (v >> 8);
        a[off + 2] = (byte) (v >> 16);
        a[off + 3] = (byte) (v >> 24);
    }
}
