package bj.radio;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import android.content.Context;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sépare un morceau en pistes (voix, batterie, basse, autres instruments) avec Spleeter 4 pistes
 * converti en ONNX. Tout se passe sur le téléphone ; le modèle (≈ 80 Mo) est téléchargé une seule fois.
 */
public class StemSeparator {

    public interface Progress { void onProgress(int percent, String message); }

    /** Noms de fichiers du modèle, dans l'ordre des pistes. */
    public static final String[] STEMS = {"vocals", "drums", "bass", "other"};
    /** Libellés affichés. */
    public static final String[] LABELS = {"Voix", "Batterie", "Basse", "Piano et autres instruments (solo)"};
    /** Courts noms pour les fichiers exportés. */
    public static final String[] SLUGS = {"voix", "batterie", "basse", "piano-autres"};

    static final String BASE_URL = "https://huggingface.co/Best-Practice/spleeter-4stems-onnx/resolve/main/";
    static final String SUFFIX = ".fp16.onnx";
    static final int SR = 44100;

    private static final int BLOCK = 20 * SR;   // durée traitée à chaque tour
    private static final int CONTEXT = SR;      // 1 s de contexte de chaque côté, jeté ensuite

    private static File modelsDir(Context c) { return new File(c.getFilesDir(), "stems-onnx"); }

    public static boolean modelsReady(Context c) {
        for (String s : STEMS) {
            File f = new File(modelsDir(c), s + SUFFIX);
            if (!f.exists() || f.length() < 1_000_000) return false;
        }
        return true;
    }

    /** Télécharge les modèles manquants (ne fait rien s'ils sont déjà là). */
    public static void ensureModels(Context c, Progress p) throws IOException {
        File dir = modelsDir(c);
        dir.mkdirs();
        for (int i = 0; i < STEMS.length; i++) {
            File f = new File(dir, STEMS[i] + SUFFIX);
            if (f.exists() && f.length() > 1_000_000) continue;
            File tmp = new File(dir, STEMS[i] + SUFFIX + ".tmp");
            HttpURLConnection con = (HttpURLConnection) new URL(BASE_URL + STEMS[i] + SUFFIX).openConnection();
            con.setConnectTimeout(30000);
            con.setReadTimeout(60000);
            con.setRequestProperty("User-Agent", "AccordsApp");
            int code = con.getResponseCode();
            if (code != 200) throw new IOException("Téléchargement du modèle impossible (code " + code + ")");
            long total = con.getContentLengthLong();
            if (total <= 0) total = 20_000_000L;
            try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] b = new byte[32768];
                long done = 0;
                int r, last = -1;
                while ((r = in.read(b)) > 0) {
                    out.write(b, 0, r);
                    done += r;
                    int pct = (int) Math.min(99, (i * 100L + done * 100 / total) / STEMS.length);
                    if (pct != last) { last = pct; p.onProgress(pct, "Téléchargement du modèle (une seule fois)"); }
                }
            } catch (IOException e) {
                tmp.delete();
                throw e;
            }
            if (!tmp.renameTo(f)) throw new IOException("Impossible d'enregistrer le modèle");
        }
        p.onProgress(100, "Téléchargement du modèle (une seule fois)");
    }

    /**
     * Sépare pcm[2][n] (44,1 kHz stéréo) et écrit une piste WAV par modèle dans le cache.
     * Retourne les fichiers dans l'ordre de STEMS.
     */
    public static File[] separate(Context ctx, float[][] pcm, Progress p, AtomicBoolean cancel) throws Exception {
        final int n = pcm[0].length;
        if (n < SR) throw new IOException("Morceau trop court");
        final int S = STEMS.length;

        File outDir = new File(ctx.getCacheDir(), "stems");
        deleteRec(outDir);
        outDir.mkdirs();

        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions so = new OrtSession.SessionOptions();
        so.setIntraOpNumThreads(Math.max(2, Runtime.getRuntime().availableProcessors()));
        OrtSession[] sess = new OrtSession[S];
        File[] wavs = new File[S];
        OutputStream[] outs = new OutputStream[S];
        long[] written = new long[S];
        try {
            for (int i = 0; i < S; i++)
                sess[i] = env.createSession(new File(modelsDir(ctx), STEMS[i] + SUFFIX).getPath(), so);
            for (int i = 0; i < S; i++) {
                wavs[i] = new File(outDir, SLUGS[i] + ".wav");
                outs[i] = new BufferedOutputStream(new FileOutputStream(wavs[i]), 1 << 16);
                outs[i].write(new byte[44]);   // en-tête, complété à la fin
            }

            final int blocks = (n + BLOCK - 1) / BLOCK;
            for (int b = 0; b < blocks; b++) {
                if (cancel.get()) throw new CancellationException();
                p.onProgress(b * 100 / blocks, "Séparation en cours");
                int s0 = b * BLOCK, e0 = Math.min(n, s0 + BLOCK);
                int a = Math.max(0, s0 - CONTEXT), z = Math.min(n, e0 + CONTEXT);
                float[][][] seg = processSegment(env, sess, pcm, a, z);
                int off = s0 - a, len = e0 - s0;
                byte[] buf = new byte[len * 4];
                for (int i = 0; i < S; i++) {
                    float[] l = seg[i][0], r = seg[i][1];
                    for (int j = 0; j < len; j++) {
                        putShort(buf, 4 * j, l[off + j]);
                        putShort(buf, 4 * j + 2, r[off + j]);
                    }
                    outs[i].write(buf, 0, buf.length);
                    written[i] += buf.length;
                }
            }
            for (int i = 0; i < S; i++) outs[i].close();
            outs = null;
            for (int i = 0; i < S; i++) fixHeader(wavs[i], written[i]);
            p.onProgress(100, "Séparation terminée");
            return wavs;
        } finally {
            if (outs != null) for (OutputStream o : outs) { try { if (o != null) o.close(); } catch (IOException ignored) { } }
            for (OrtSession s : sess) { try { if (s != null) s.close(); } catch (Exception ignored) { } }
            so.close();
        }
    }

    /** Traite pcm[·][a..z) : retourne [piste][canal][z-a]. */
    private static float[][][] processSegment(OrtEnvironment env, OrtSession[] sess, float[][] pcm,
                                              int a, int z) throws Exception {
        final int len = z - a;
        StemDsp.Spec spec = StemDsp.analyze(pcm, a, len);
        final int splits = StemDsp.splits(spec.frames);
        float[] netIn = StemDsp.netInput(spec, splits);
        long[] shape = {2, splits, StemDsp.T, StemDsp.F};

        ByteBuffer bb = ByteBuffer.allocateDirect(netIn.length * 4).order(ByteOrder.nativeOrder());
        FloatBuffer inBuf = bb.asFloatBuffer();
        inBuf.put(netIn);
        inBuf.rewind();

        float[][] est = new float[sess.length][];
        try (OnnxTensor t = OnnxTensor.createTensor(env, inBuf, shape)) {
            for (int i = 0; i < sess.length; i++) {
                try (OrtSession.Result r = sess[i].run(Collections.singletonMap("x", t))) {
                    OnnxTensor o = (OnnxTensor) r.get(0);
                    FloatBuffer fb = o.getFloatBuffer();
                    fb.rewind();
                    est[i] = new float[netIn.length];
                    fb.get(est[i]);
                }
            }
        }
        return StemDsp.synthesize(spec, est, splits, len);
    }

    private static void putShort(byte[] b, int at, float v) {
        int s = Math.round(v * 32767f);
        if (s > 32767) s = 32767;
        if (s < -32768) s = -32768;
        b[at] = (byte) (s & 0xFF);
        b[at + 1] = (byte) ((s >> 8) & 0xFF);
    }

    /** Écrit l'en-tête WAV (stéréo, 16 bits, 44,1 kHz) au début du fichier. */
    private static void fixHeader(File f, long dataLen) throws IOException {
        byte[] h = new byte[44];
        put(h, 0, "RIFF");
        le32(h, 4, (int) (36 + dataLen));
        put(h, 8, "WAVE");
        put(h, 12, "fmt ");
        le32(h, 16, 16);
        h[20] = 1;                 // PCM
        h[22] = 2;                 // 2 canaux
        le32(h, 24, SR);
        le32(h, 28, SR * 4);
        h[32] = 4;
        h[34] = 16;
        put(h, 36, "data");
        le32(h, 40, (int) dataLen);
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.seek(0);
            raf.write(h);
        }
    }

    private static void put(byte[] b, int at, String s) {
        for (int i = 0; i < s.length(); i++) b[at + i] = (byte) s.charAt(i);
    }

    private static void le32(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
        b[at + 2] = (byte) (v >> 16);
        b[at + 3] = (byte) (v >> 24);
    }

    static void deleteRec(File f) {
        if (f.isDirectory()) {
            File[] list = f.listFiles();
            if (list != null) for (File x : list) deleteRec(x);
        }
        f.delete();
    }
}
