package bj.radio;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Traitement du signal pour la séparation de pistes (modèle Spleeter) :
 * STFT (fenêtre de Hann périodique 4096, pas 1024), masque de Wiener « ratio »
 * et reconstruction. Java pur, sans dépendance Android.
 */
final class StemDsp {

    static final int N_FFT = 4096;
    static final int HOP = 1024;
    static final int T = 512;          // trames par bloc du réseau
    static final int F = 1024;         // bandes de fréquence vues par le réseau (~11 kHz)
    static final int BINS = N_FFT / 2 + 1;
    static final int PAD = N_FFT - HOP;

    private static final float[] WIN = new float[N_FFT];
    private static final int[] REV = new int[N_FFT];
    private static final float[] COS = new float[N_FFT / 2];
    private static final float[] SIN = new float[N_FFT / 2];
    private static final ThreadLocal<float[][]> SCRATCH =
            ThreadLocal.withInitial(() -> new float[][]{new float[N_FFT], new float[N_FFT]});

    static {
        for (int i = 0; i < N_FFT; i++) WIN[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / N_FFT));
        int bits = Integer.numberOfTrailingZeros(N_FFT);
        for (int i = 0; i < N_FFT; i++) REV[i] = Integer.reverse(i) >>> (32 - bits);
        for (int k = 0; k < N_FFT / 2; k++) {
            COS[k] = (float) Math.cos(2 * Math.PI * k / N_FFT);
            SIN[k] = (float) Math.sin(2 * Math.PI * k / N_FFT);
        }
    }

    private StemDsp() { }

    /** Spectres complexes de deux canaux : re/im[canal][trame * BINS + bin]. */
    static final class Spec {
        int frames;
        float[][] re, im;
    }

    /** FFT complexe directe sur place (taille N_FFT). */
    private static void fft(float[] re, float[] im) {
        for (int i = 0; i < N_FFT; i++) {
            int j = REV[i];
            if (j > i) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int size = 2; size <= N_FFT; size <<= 1) {
            int half = size >> 1, step = N_FFT / size;
            for (int i = 0; i < N_FFT; i += size) {
                for (int j = 0, k = 0; j < half; j++, k += step) {
                    float wr = COS[k], wi = -SIN[k];
                    int p = i + j, q = p + half;
                    float tr = re[q] * wr - im[q] * wi;
                    float ti = re[q] * wi + im[q] * wr;
                    re[q] = re[p] - tr;
                    im[q] = im[p] - ti;
                    re[p] += tr;
                    im[p] += ti;
                }
            }
        }
    }

    /** STFT stéréo du segment pcm[c][a .. a+len). Un zéro-padding de N_FFT-HOP est ajouté devant. */
    static Spec analyze(float[][] pcm, int a, int len) {
        final int frames = (PAD + len + HOP - 1) / HOP;
        final Spec s = new Spec();
        s.frames = frames;
        s.re = new float[2][frames * BINS];
        s.im = new float[2][frames * BINS];
        for (int c = 0; c < 2; c++) {
            final float[] x = pcm[c];
            final float[] outRe = s.re[c], outIm = s.im[c];
            IntStream.range(0, frames).parallel().forEach(fr -> {
                float[][] sc = SCRATCH.get();
                float[] re = sc[0], im = sc[1];
                int base = fr * HOP - PAD;
                for (int j = 0; j < N_FFT; j++) {
                    int idx = base + j;
                    re[j] = (idx >= 0 && idx < len) ? x[a + idx] * WIN[j] : 0f;
                    im[j] = 0f;
                }
                fft(re, im);
                System.arraycopy(re, 0, outRe, fr * BINS, BINS);
                System.arraycopy(im, 0, outIm, fr * BINS, BINS);
            });
        }
        return s;
    }

    /** Nombre de blocs de T trames nécessaires. */
    static int splits(int frames) { return (frames + T - 1) / T; }

    /** Entrée du réseau : modules des F premières bandes, forme [2][splits*T][F] aplatie. */
    static float[] netInput(Spec s, int splits) {
        float[] in = new float[2 * splits * T * F];
        for (int c = 0; c < 2; c++) {
            final float[] re = s.re[c], im = s.im[c];
            final int cOff = c * splits * T * F;
            IntStream.range(0, s.frames).parallel().forEach(fr -> {
                int o = cOff + fr * F, so = fr * BINS;
                for (int k = 0; k < F; k++) {
                    float r = re[so + k], i = im[so + k];
                    in[o + k] = (float) Math.sqrt(r * r + i * i);
                }
            });
        }
        return in;
    }

    /**
     * Applique le masque « ratio » de Spleeter aux estimations des pistes et reconstruit le signal.
     * est[piste] a la forme [2][splits*T][F] aplatie. Résultat : [piste][canal][len].
     * La somme des pistes redonne le morceau d'origine.
     */
    static float[][][] synthesize(Spec s, float[][] est, int splits, int len) {
        final int frames = s.frames;
        final int stems = est.length;
        final int m = frames * F;
        final int stride = splits * T * F;

        // dénominateur commun : somme des carrés de toutes les pistes
        final float[] denom = new float[2 * m];
        Arrays.fill(denom, 1e-10f);
        for (int i = 0; i < stems; i++) {
            final float[] e = est[i];
            for (int c = 0; c < 2; c++) {
                for (int fr = 0; fr < frames; fr++) {
                    int eo = c * stride + fr * F, d = c * m + fr * F;
                    for (int k = 0; k < F; k++) {
                        float v = e[eo + k];
                        denom[d + k] += v * v;
                    }
                }
            }
        }

        // somme des fenêtres au carré (normalisation de la reconstruction)
        final int total = (frames - 1) * HOP + N_FFT;
        final double[] wsum = new double[total];
        for (int fr = 0; fr < frames; fr++)
            for (int j = 0; j < N_FFT; j++) wsum[fr * HOP + j] += (double) WIN[j] * WIN[j];

        final float[][][] out = new float[stems][2][];
        final float[] frameBuf = new float[frames * N_FFT];
        final float[] acc = new float[total];

        for (int i = 0; i < stems; i++) {
            final float[] e = est[i];
            for (int c = 0; c < 2; c++) {
                final float[] sre = s.re[c], sim = s.im[c];
                final int cc = c;
                IntStream.range(0, frames).parallel().forEach(fr -> {
                    float[][] sc = SCRATCH.get();
                    float[] re = sc[0], im = sc[1];
                    int eo = cc * stride + fr * F, d = cc * m + fr * F, so = fr * BINS;
                    float sum = 0f;
                    for (int k = 0; k < F; k++) {
                        float v = e[eo + k];
                        float mk = (v * v + 2.5e-11f) / denom[d + k];
                        sum += mk;
                        re[k] = sre[so + k] * mk;
                        im[k] = sim[so + k] * mk;
                    }
                    float mean = sum / F;   // au-delà de ~11 kHz : masque moyen de la trame
                    for (int k = F; k < BINS; k++) {
                        re[k] = sre[so + k] * mean;
                        im[k] = sim[so + k] * mean;
                    }
                    for (int k = 1; k < BINS - 1; k++) {   // symétrie hermitienne
                        re[N_FFT - k] = re[k];
                        im[N_FFT - k] = -im[k];
                    }
                    fft(im, re);               // FFT inverse par échange réel/imaginaire
                    int fo = fr * N_FFT;
                    for (int j = 0; j < N_FFT; j++) frameBuf[fo + j] = re[j] / N_FFT * WIN[j];
                });
                Arrays.fill(acc, 0f);
                for (int fr = 0; fr < frames; fr++) {
                    int fo = fr * N_FFT, at = fr * HOP;
                    for (int j = 0; j < N_FFT; j++) acc[at + j] += frameBuf[fo + j];
                }
                float[] y = new float[len];
                for (int j = 0; j < len; j++) {
                    int p = PAD + j;
                    y[j] = wsum[p] > 1e-8 ? (float) (acc[p] / wsum[p]) : 0f;
                }
                out[i][c] = y;
            }
        }
        return out;
    }
}
