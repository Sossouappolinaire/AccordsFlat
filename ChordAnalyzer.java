package com.accords.app;

import java.util.*;

/** Analyse harmonique : notes dominantes, tonalité, tempo, accords et grille. Java pur. */
public class ChordAnalyzer {

    public static final int SR = 22050;
    static final String[] EN = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    static final String[] FR = {"Do", "Do#", "Ré", "Ré#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si"};

    static final int[][] TPL = {{0, 4, 7}, {0, 3, 7}, {0, 4, 7, 10}, {0, 3, 7, 10}, {0, 4, 7, 11}};
    static final String[] SUF = {"", "m", "7", "m7", "maj7"};
    static final double[] W = {1.0, 1.0, 0.95, 0.95, 0.95};

    static final double[] MAJ = {6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88};
    static final double[] MIN = {6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17};

    /** Résultat structuré de l'analyse (non transposé). */
    public static class Analysis {
        public int keyRoot;
        public boolean minor;
        public double bpm, t0, half, dur;
        public String[] chords;      // un accord par demi-mesure (2 temps)
        public double[] pcShare;     // % de présence de chaque note (Do..Si)
        public String[] topChords;
    }

    /** Grille texte + position de chaque demi-mesure dans le texte (pour l'illumination). */
    public static class Grid {
        public String text;
        public int[] start, end;
    }

    // ---------- Outils de transposition et d'affichage ----------

    public static String transposeChord(String c, int semi) {
        if (c == null || c.isEmpty() || c.equals("N.C.")) return c;
        int len = (c.length() > 1 && c.charAt(1) == '#') ? 2 : 1;
        int idx = Arrays.asList(EN).indexOf(c.substring(0, len));
        if (idx < 0) return c;
        return EN[((idx + semi) % 12 + 12) % 12] + c.substring(len);
    }

    /** Index de la demi-mesure jouée à l'instant sec, ou -1. */
    public static int halfIndex(Analysis a, double sec) {
        if (sec < a.t0) return -1;
        int i = (int) Math.floor((sec - a.t0) / a.half);
        return i >= a.chords.length ? -1 : i;
    }

    public static Grid buildGrid(Analysis a, int semi) {
        int nH = a.chords.length, measures = nH / 2;
        Grid gr = new Grid();
        gr.start = new int[nH];
        gr.end = new int[nH];
        StringBuilder g = new StringBuilder();
        for (int m = 0; m < measures; m++) {
            if (m % 4 == 0) g.append(String.format(Locale.US, "%3d ", m + 1));
            g.append("| ");
            String c1 = transposeChord(a.chords[2 * m], semi), c2 = transposeChord(a.chords[2 * m + 1], semi);
            int cell0 = g.length();
            if (c1.equals(c2)) {
                gr.start[2 * m] = gr.start[2 * m + 1] = cell0;
                gr.end[2 * m] = gr.end[2 * m + 1] = cell0 + c1.length();
                g.append(c1);
            } else {
                gr.start[2 * m] = cell0;
                gr.end[2 * m] = cell0 + c1.length();
                g.append(c1).append(' ');
                gr.start[2 * m + 1] = g.length();
                g.append(c2);
                gr.end[2 * m + 1] = g.length();
            }
            while (g.length() - cell0 < 9) g.append(' ');
            if (m % 4 == 3 || m == measures - 1) g.append("|\n");
        }
        gr.text = g.toString();
        return gr;
    }

    public static String summary(Analysis a, int semi) {
        String key = FR[((a.keyRoot + semi) % 12 + 12) % 12] + (a.minor ? " mineur" : " majeur");
        Integer[] idx = new Integer[12];
        for (int i = 0; i < 12; i++) idx[i] = i;
        final double[] s = a.pcShare;
        Arrays.sort(idx, (p, q) -> Double.compare(s[q], s[p]));
        StringBuilder notes = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            notes.append(FR[((idx[i] + semi) % 12 + 12) % 12]).append(" (").append(Math.round(s[idx[i]])).append("%)");
            if (i < 6) notes.append(", ");
        }
        StringBuilder top = new StringBuilder();
        for (String c : a.topChords) {
            if (top.length() > 0) top.append(", ");
            top.append(transposeChord(c, semi));
        }
        int d = (int) a.dur;
        return "TONALITÉ : " + key + "\n"
                + "TEMPO : " + a.bpm + " BPM (mesure à 4 temps)\n"
                + "DURÉE : " + d / 60 + " min " + d % 60 + " s\n\n"
                + "NOTES LES PLUS PRÉSENTES :\n" + notes + "\n\n"
                + "ACCORDS LES PLUS UTILISÉS :\n" + top + "\n";
    }

    // ---------- Traitement du signal ----------

    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    double xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    double ncr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = ncr;
                }
            }
        }
    }

    static double[] hann(int n) {
        double[] w = new double[n];
        for (int i = 0; i < n; i++) w[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1));
        return w;
    }

    static String matchChord(float[] c) {
        double norm = 0;
        for (float v : c) norm += v * v;
        if (norm < 1e-9) return "N.C.";
        norm = Math.sqrt(norm);
        double best = -1;
        String name = "N.C.";
        for (int root = 0; root < 12; root++) {
            for (int t = 0; t < TPL.length; t++) {
                double s = 0;
                for (int iv : TPL[t]) s += c[(root + iv) % 12] / norm;
                s = s / Math.sqrt(TPL[t].length) * W[t];
                if (s > best) { best = s; name = EN[root] + SUF[t]; }
            }
        }
        return name;
    }

    static double corr(double[] x, double[] prof, int rot) {
        double mx = 0, my = 0;
        for (int i = 0; i < 12; i++) { mx += x[i]; my += prof[i]; }
        mx /= 12; my /= 12;
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < 12; i++) {
            double a = x[(i + rot) % 12] - mx, b = prof[i] - my;
            sxy += a * b; sxx += a * a; syy += b * b;
        }
        return sxy / (Math.sqrt(sxx * syy) + 1e-12);
    }

    static double estimateBpm(float[] x) {
        final int n = 1024, hop = 512;
        int m = (x.length - n) / hop + 1;
        if (m < 100) return 0;
        double[] w = hann(n), re = new double[n], im = new double[n];
        double[] prev = new double[n / 2];
        double[] flux = new double[m];
        int kMax = 4000 * n / SR;
        for (int f = 0; f < m; f++) {
            for (int i = 0; i < n; i++) { re[i] = x[f * hop + i] * w[i]; im[i] = 0; }
            fft(re, im);
            double s = 0;
            for (int k = 1; k <= kMax; k++) {
                double mag = Math.log1p(100 * Math.hypot(re[k], im[k]));
                s += Math.max(0, mag - prev[k]);
                prev[k] = mag;
            }
            flux[f] = s;
        }
        double mean = 0;
        for (double v : flux) mean += v;
        mean /= m;
        for (int i = 0; i < m; i++) flux[i] -= mean;
        double fps = SR / (double) hop;
        int minLag = (int) Math.floor(60.0 / 180 * fps), maxLag = (int) Math.ceil(60.0 / 70 * fps);
        double[] ac = new double[maxLag + 2];
        int bestL = minLag;
        for (int l = minLag - 1; l <= maxLag + 1; l++) {
            double s = 0;
            for (int i = 0; i + l < m; i++) s += flux[i] * flux[i + l];
            ac[l] = s / (m - l);
        }
        for (int l = minLag; l <= maxLag; l++) if (ac[l] > ac[bestL]) bestL = l;
        double lag = bestL;
        double d = ac[bestL - 1] - 2 * ac[bestL] + ac[bestL + 1];
        if (d != 0) lag += 0.5 * (ac[bestL - 1] - ac[bestL + 1]) / d;
        return Math.round(600.0 * fps / lag) / 10.0;
    }

    /** x : échantillons mono à 22 050 Hz. */
    public static Analysis analyze(float[] x) {
        final int N = 8192, HOP = 4096;
        int nf = x.length < N ? 0 : (x.length - N) / HOP + 1;
        if (nf < 8) throw new IllegalArgumentException("Morceau trop court pour l'analyse");

        double[] win = hann(N), re = new double[N], im = new double[N];
        float[][] chroma = new float[nf][12];
        double[] rms = new double[nf];
        int kLo = (int) Math.ceil(100.0 * N / SR), kHi = (int) Math.floor(2000.0 * N / SR);
        double[] global = new double[12];
        double maxRms = 0;

        for (int f = 0; f < nf; f++) {
            double e = 0;
            for (int i = 0; i < N; i++) {
                double v = x[f * HOP + i];
                e += v * v;
                re[i] = v * win[i];
                im[i] = 0;
            }
            rms[f] = Math.sqrt(e / N);
            maxRms = Math.max(maxRms, rms[f]);
            fft(re, im);
            for (int k = kLo; k <= kHi; k++) {
                double mag = Math.log1p(100 * Math.hypot(re[k], im[k]));
                double freq = k * (double) SR / N;
                int midi = (int) Math.round(69 + 12 * Math.log(freq / 440.0) / Math.log(2));
                chroma[f][((midi % 12) + 12) % 12] += mag;
            }
            for (int p = 0; p < 12; p++) global[p] += chroma[f][p];
        }

        Analysis A = new Analysis();
        double tot = 0;
        for (double v : global) tot += v;
        A.pcShare = new double[12];
        for (int p = 0; p < 12; p++) A.pcShare[p] = 100 * global[p] / tot;

        double bestC = -2;
        for (int r = 0; r < 12; r++) {
            for (int m = 0; m < 2; m++) {
                double c = corr(global, m == 0 ? MAJ : MIN, r);
                if (c > bestC) { bestC = c; A.keyRoot = r; A.minor = (m == 1); }
            }
        }

        A.bpm = estimateBpm(x);
        if (A.bpm <= 0) A.bpm = 100;

        int first = 0;
        while (first < nf - 1 && rms[first] < 0.1 * maxRms) first++;
        A.t0 = first * (double) HOP / SR;
        A.dur = x.length / (double) SR;
        double beat = 60.0 / A.bpm;
        A.half = beat * 2;
        int nHalves = (int) ((A.dur - A.t0) / A.half);
        if (nHalves < 2) nHalves = 2;

        A.chords = new String[nHalves];
        Map<String, Integer> count = new HashMap<>();
        for (int h = 0; h < nHalves; h++) {
            double t = A.t0 + h * A.half;
            int a = (int) Math.ceil((t * SR - N / 2.0) / HOP);
            int b = (int) Math.floor(((t + A.half) * SR - N / 2.0) / HOP);
            a = Math.max(0, Math.min(nf - 1, a));
            b = Math.max(a, Math.min(nf - 1, b));
            float[] avg = new float[12];
            for (int i = a; i <= b; i++) {
                double s = 0;
                for (float v : chroma[i]) s += v * v;
                s = Math.sqrt(s) + 1e-9;
                for (int p = 0; p < 12; p++) avg[p] += chroma[i][p] / s;
            }
            A.chords[h] = matchChord(avg);
            count.merge(A.chords[h], 1, Integer::sum);
        }

        List<Map.Entry<String, Integer>> es = new ArrayList<>(count.entrySet());
        es.sort((p, q) -> q.getValue() - p.getValue());
        List<String> top = new ArrayList<>();
        for (Map.Entry<String, Integer> e : es) {
            if (e.getKey().equals("N.C.")) continue;
            top.add(e.getKey());
            if (top.size() == 8) break;
        }
        A.topChords = top.toArray(new String[0]);
        return A;
    }
}
