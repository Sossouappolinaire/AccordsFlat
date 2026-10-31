package com.accords.app;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Paroles en ligne via une API compatible OpenAI (/audio/transcriptions, ex. Whisper). */
public class LyricsOnline {

    private static final int MAX_BYTES = 25 * 1024 * 1024;

    public static String transcribe(Context ctx, Uri uri, String fileName,
                                    String baseUrl, String apiKey, String model) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty())
            throw new Exception("Clé API manquante (bouton Paramètres)");

        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            byte[] b = new byte[16384];
            int r;
            while ((r = in.read(b)) > 0) {
                bo.write(b, 0, r);
                if (bo.size() > MAX_BYTES) throw new Exception("Fichier trop gros (25 Mo max pour l'envoi)");
            }
        }

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
            field(out, boundary, "response_format", "json");
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                    + fileName + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            bo.writeTo(out);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream resp = new ByteArrayOutputStream();
        if (is != null) {
            byte[] b = new byte[8192];
            int r;
            while ((r = is.read(b)) > 0) resp.write(b, 0, r);
        }
        String body = resp.toString("UTF-8");
        if (code < 200 || code >= 300) throw new Exception("Erreur serveur " + code + " : " + body);
        return new JSONObject(body).optString("text", "(aucun texte)");
    }

    private static void field(OutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
}
