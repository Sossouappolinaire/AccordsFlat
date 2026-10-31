package com.accords.app;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Paroles en ligne via une API compatible OpenAI (/audio/transcriptions, ex. Whisper), avec horodatage. */
public class LyricsOnline {

    private static final int MAX_BYTES = 25 * 1024 * 1024;

    private static class HttpError extends Exception {
        final int code;
        HttpError(int code, String body) {
            super("Erreur serveur " + code + " : " + body);
            this.code = code;
        }
    }

    public interface Listener {
        void onUpload(int percent);
        void onWaiting();
    }

    public static List<Lyrics.Word> transcribe(Context ctx, Uri uri, String fileName,
                                               String baseUrl, String apiKey, String model,
                                               Listener listener) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty())
            throw new Exception("Clé API manquante (bouton Réglages)");

        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            byte[] b = new byte[16384];
            int r;
            while ((r = in.read(b)) > 0) {
                bo.write(b, 0, r);
                if (bo.size() > MAX_BYTES) throw new Exception("Fichier trop gros (25 Mo max pour l'envoi)");
            }
        }

        String body;
        try {
            body = post(bo, fileName, baseUrl, apiKey, model, true, listener);
        } catch (HttpError e) {
            // certains serveurs n'acceptent pas les horodatages mot par mot : on réessaie sans
            if (e.code == 400 || e.code == 422) body = post(bo, fileName, baseUrl, apiKey, model, false, listener);
            else throw e;
        }
        return parse(body);
    }

    private static String post(ByteArrayOutputStream audio, String fileName, String baseUrl,
                               String apiKey, String model, boolean granular, Listener listener) throws Exception {
        String boundary = "----acc" + System.currentTimeMillis();
        String url = baseUrl.replaceAll("/+$", "") + "/audio/transcriptions";
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(30000);
        c.setReadTimeout(300000);
        c.setChunkedStreamingMode(0);
        c.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream out = c.getOutputStream()) {
            field(out, boundary, "model", model);
            field(out, boundary, "response_format", "verbose_json");
            if (granular) {
                field(out, boundary, "timestamp_granularities[]", "word");
                field(out, boundary, "timestamp_granularities[]", "segment");
            }
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                    + fileName + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            byte[] data = audio.toByteArray();
            int off = 0, lastPct = -1;
            while (off < data.length) {
                int n = Math.min(65536, data.length - off);
                out.write(data, off, n);
                off += n;
                int pct = (int) (100L * off / data.length);
                if (listener != null && pct != lastPct) { lastPct = pct; listener.onUpload(pct); }
            }
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        if (listener != null) listener.onWaiting();
        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream resp = new ByteArrayOutputStream();
        if (is != null) {
            byte[] b = new byte[8192];
            int r;
            while ((r = is.read(b)) > 0) resp.write(b, 0, r);
        }
        String body = resp.toString("UTF-8");
        if (code < 200 || code >= 300) throw new HttpError(code, body);
        return body;
    }

    private static List<Lyrics.Word> parse(String body) throws Exception {
        JSONObject j = new JSONObject(body);
        List<Lyrics.Word> out = new ArrayList<>();
        JSONArray ws = j.optJSONArray("words");
        if (ws != null && ws.length() > 0) {
            for (int i = 0; i < ws.length(); i++) {
                JSONObject o = ws.getJSONObject(i);
                String w = o.optString("word", "").trim();
                if (!w.isEmpty()) out.add(new Lyrics.Word(w, o.optDouble("start", 0), o.optDouble("end", 0)));
            }
            return out;
        }
        JSONArray segs = j.optJSONArray("segments");
        if (segs != null && segs.length() > 0) {
            for (int i = 0; i < segs.length(); i++) {
                JSONObject o = segs.getJSONObject(i);
                spread(out, o.optString("text", ""), o.optDouble("start", 0), o.optDouble("end", 0));
            }
            return out;
        }
        spread(out, j.optString("text", ""), 0, j.optDouble("duration", 0));
        return out;
    }

    /** Répartit les mots d'un texte uniformément entre deux instants. */
    private static void spread(List<Lyrics.Word> out, String text, double s, double e) {
        String t = text.trim();
        if (t.isEmpty()) return;
        String[] tok = t.split("\\s+");
        double dur = e > s ? (e - s) / tok.length : 0.3;
        for (int i = 0; i < tok.length; i++)
            out.add(new Lyrics.Word(tok[i], s + i * dur, s + (i + 1) * dur));
    }

    private static void field(OutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
}
