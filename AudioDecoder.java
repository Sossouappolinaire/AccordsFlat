package bj.radio;

import java.util.function.IntConsumer;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;

/** Décode un fichier audio (MP3, M4A, WAV, OGG...) en échantillons mono flottants. */
public class AudioDecoder {

    public static class Result {
        public float[] samples;
        public int sampleRate;
    }

    private static class FloatList {
        float[] a = new float[1 << 20];
        int n = 0;
        void add(float v) {
            if (n == a.length) a = Arrays.copyOf(a, n * 2);
            a[n++] = v;
        }
        float[] toArray() { return Arrays.copyOf(a, n); }
    }

    public static Result decodeMono(Context ctx, Uri uri) throws Exception {
        return decodeMono(ctx, uri, null);
    }

    public static Result decodeMono(Context ctx, Uri uri, IntConsumer prog) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(ctx, uri, null);
        int track = -1;
        MediaFormat fmt = null;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) { track = i; fmt = f; break; }
        }
        if (track < 0) throw new Exception("Aucune piste audio trouvée");
        ex.selectTrack(track);

        MediaCodec codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
        codec.configure(fmt, null, null, 0);
        codec.start();

        int rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int ch = Math.max(1, fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        FloatList list = new FloatList();
        long durUs = fmt.containsKey(MediaFormat.KEY_DURATION) ? fmt.getLong(MediaFormat.KEY_DURATION) : 0;
        int lastPct = -1;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inDone = false, outDone = false;

        while (!outDone) {
            if (!inDone) {
                int ii = codec.dequeueInputBuffer(10000);
                if (ii >= 0) {
                    ByteBuffer ib = codec.getInputBuffer(ii);
                    int sz = ex.readSampleData(ib, 0);
                    if (sz < 0) {
                        codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inDone = true;
                    } else {
                        long st = ex.getSampleTime();
                        codec.queueInputBuffer(ii, 0, sz, st, 0);
                        if (prog != null && durUs > 0) {
                            int pct = (int) Math.min(99, 100 * st / durUs);
                            if (pct != lastPct) { lastPct = pct; prog.accept(pct); }
                        }
                        ex.advance();
                    }
                }
            }
            int oi = codec.dequeueOutputBuffer(info, 10000);
            if (oi >= 0) {
                ByteBuffer ob = codec.getOutputBuffer(oi);
                ob.position(info.offset);
                ob.limit(info.offset + info.size);
                ShortBuffer sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer();
                int frames = sb.remaining() / ch;
                for (int f = 0; f < frames; f++) {
                    float s = 0;
                    for (int c = 0; c < ch; c++) s += sb.get();
                    list.add(s / ch / 32768f);
                }
                codec.releaseOutputBuffer(oi, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
            } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat nf = codec.getOutputFormat();
                rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                ch = Math.max(1, nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            }
        }
        codec.stop();
        codec.release();
        ex.release();

        if (prog != null) prog.accept(100);
        Result r = new Result();
        r.samples = list.toArray();
        r.sampleRate = rate;
        return r;
    }

    /** Décode un fichier audio en stéréo 44,1 kHz : retourne [gauche, droite]. */
    public static float[][] decodeStereo44k(Context ctx, Uri uri, IntConsumer prog) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(ctx, uri, null);
        int track = -1;
        MediaFormat fmt = null;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) { track = i; fmt = f; break; }
        }
        if (track < 0) throw new Exception("Aucune piste audio trouvée");
        ex.selectTrack(track);

        MediaCodec codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
        codec.configure(fmt, null, null, 0);
        codec.start();

        int rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int ch = Math.max(1, fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        FloatList left = new FloatList();
        FloatList right = new FloatList();
        long durUs = fmt.containsKey(MediaFormat.KEY_DURATION) ? fmt.getLong(MediaFormat.KEY_DURATION) : 0;
        int lastPct = -1;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inDone = false, outDone = false;

        while (!outDone) {
            if (!inDone) {
                int ii = codec.dequeueInputBuffer(10000);
                if (ii >= 0) {
                    ByteBuffer ib = codec.getInputBuffer(ii);
                    int sz = ex.readSampleData(ib, 0);
                    if (sz < 0) {
                        codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inDone = true;
                    } else {
                        long st = ex.getSampleTime();
                        codec.queueInputBuffer(ii, 0, sz, st, 0);
                        if (prog != null && durUs > 0) {
                            int pct = (int) Math.min(99, 100 * st / durUs);
                            if (pct != lastPct) { lastPct = pct; prog.accept(pct); }
                        }
                        ex.advance();
                    }
                }
            }
            int oi = codec.dequeueOutputBuffer(info, 10000);
            if (oi >= 0) {
                ByteBuffer ob = codec.getOutputBuffer(oi);
                ob.position(info.offset);
                ob.limit(info.offset + info.size);
                ShortBuffer sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer();
                int frames = sb.remaining() / ch;
                for (int f = 0; f < frames; f++) {
                    float l = 0, r = 0;
                    for (int c = 0; c < ch; c++) {
                        float v = sb.get() / 32768f;
                        if (c == 0) l = v;
                        else if (c == 1) r = v;
                    }
                    if (ch == 1) r = l;
                    left.add(l);
                    right.add(r);
                }
                codec.releaseOutputBuffer(oi, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
            } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat nf = codec.getOutputFormat();
                rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                ch = Math.max(1, nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            }
        }
        codec.stop();
        codec.release();
        ex.release();

        if (prog != null) prog.accept(100);
        float[] l = left.toArray();
        float[] r = right.toArray();
        return new float[][]{resample(l, rate, 44100), resample(r, rate, 44100)};
    }

    /** Rééchantillonnage simple (moyenne en descente, interpolation linéaire en montée). */
    public static float[] resample(float[] in, int from, int to) {
        if (from == to) return in;
        double ratio = (double) from / to;
        int outLen = (int) (in.length / ratio);
        float[] out = new float[outLen];
        for (int i = 0; i < outLen; i++) {
            double p = i * ratio;
            int a = (int) p;
            if (ratio > 1.0) {
                int b = Math.min(in.length, (int) (p + ratio));
                if (b <= a) b = a + 1;
                float s = 0;
                for (int k = a; k < b && k < in.length; k++) s += in[k];
                out[i] = s / (b - a);
            } else {
                int a2 = Math.min(a + 1, in.length - 1);
                double frac = p - a;
                out[i] = (float) (in[a] * (1 - frac) + in[a2] * frac);
            }
        }
        return out;
    }
}
