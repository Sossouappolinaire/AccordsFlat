package com.accords.app;

import android.content.Context;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

/** Paroles hors ligne avec Vosk (modèle français téléchargé au premier démarrage). */
public class LyricsOffline {

    public interface Callback {
        void onResult(String text);
        void onError(String message);
    }

    private static Model cached;

    /** mono16k : échantillons mono à 16 000 Hz. Le modèle doit déjà être téléchargé. */
    public static void transcribe(Context ctx, float[] mono16k, Callback cb) {
        new Thread(() -> {
            try {
                synchronized (LyricsOffline.class) {
                    if (cached == null) cached = new Model(ModelDownloader.modelDir(ctx).getAbsolutePath());
                }
                run(cached, mono16k, cb);
            } catch (Throwable e) {
                cb.onError("Modèle Vosk introuvable : " + e.getMessage());
            }
        }).start();
    }

    private static void run(Model model, float[] x, Callback cb) {
        try {
            Recognizer rec = new Recognizer(model, 16000.0f);
            StringBuilder sb = new StringBuilder();
            final int chunk = 4000;
            byte[] buf = new byte[chunk * 2];
            for (int pos = 0; pos < x.length; pos += chunk) {
                int n = Math.min(chunk, x.length - pos);
                for (int i = 0; i < n; i++) {
                    int v = Math.max(-32768, Math.min(32767, Math.round(x[pos + i] * 32767f)));
                    buf[2 * i] = (byte) (v & 0xff);
                    buf[2 * i + 1] = (byte) ((v >> 8) & 0xff);
                }
                if (rec.acceptWaveForm(buf, n * 2)) append(sb, rec.getResult());
            }
            append(sb, rec.getFinalResult());
            rec.close();
            String t = sb.toString().trim();
            cb.onResult(t.isEmpty() ? "(aucune parole détectée)" : t);
        } catch (Throwable e) {
            cb.onError("Erreur Vosk : " + e.getMessage());
        }
    }

    private static void append(StringBuilder sb, String json) {
        try {
            String t = new JSONObject(json).optString("text", "").trim();
            if (!t.isEmpty()) sb.append(t).append('\n');
        } catch (Exception ignored) { }
    }
}
