package bj.radio;

import java.util.function.IntConsumer;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.util.ArrayList;
import java.util.List;

/** Paroles hors ligne avec Vosk (modèle français téléchargé au premier démarrage), avec horodatage des mots. */
public class LyricsOffline {

    public interface Callback {
        void onResult(List<Lyrics.Word> words);
        void onError(String message);
    }

    private static Model cached;

    /** mono16k : échantillons mono à 16 000 Hz. Le modèle doit déjà être téléchargé. */
    public static void transcribe(Context ctx, float[] mono16k, Callback cb, IntConsumer progress) {
        new Thread(() -> {
            try {
                synchronized (LyricsOffline.class) {
                    if (cached == null) cached = new Model(ModelDownloader.modelDir(ctx).getAbsolutePath());
                }
                run(cached, mono16k, cb, progress);
            } catch (Throwable e) {
                cb.onError("Modèle Vosk introuvable : " + e.getMessage());
            }
        }).start();
    }

    private static void run(Model model, float[] x, Callback cb, IntConsumer progress) {
        try {
            Recognizer rec = new Recognizer(model, 16000.0f);
            rec.setWords(true);
            List<Lyrics.Word> words = new ArrayList<>();
            final int chunk = 4000;
            int lastPct = -1;
            byte[] buf = new byte[chunk * 2];
            for (int pos = 0; pos < x.length; pos += chunk) {
                int n = Math.min(chunk, x.length - pos);
                if (progress != null) {
                    int pct = (int) (100L * pos / x.length);
                    if (pct != lastPct) { lastPct = pct; progress.accept(pct); }
                }
                for (int i = 0; i < n; i++) {
                    int v = Math.max(-32768, Math.min(32767, Math.round(x[pos + i] * 32767f)));
                    buf[2 * i] = (byte) (v & 0xff);
                    buf[2 * i + 1] = (byte) ((v >> 8) & 0xff);
                }
                if (rec.acceptWaveForm(buf, n * 2)) collect(words, rec.getResult());
            }
            collect(words, rec.getFinalResult());
            rec.close();
            cb.onResult(words);
        } catch (Throwable e) {
            cb.onError("Erreur Vosk : " + e.getMessage());
        }
    }

    private static void collect(List<Lyrics.Word> out, String json) {
        try {
            JSONArray arr = new JSONObject(json).optJSONArray("result");
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String w = o.optString("word", "").trim();
                if (!w.isEmpty()) out.add(new Lyrics.Word(w, o.optDouble("start", 0), o.optDouble("end", 0)));
            }
        } catch (Exception ignored) { }
    }
}
