package com.accords.app;

import android.content.Context;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Télécharge le modèle Vosk français au premier démarrage et le garde dans le stockage de l'appli. */
public class ModelDownloader {

    public interface Progress { void onPercent(int percent); }

    static final String URL_ZIP = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip";
    static final String DIR_NAME = "vosk-model-small-fr-0.22";

    private static File root(Context c) { return new File(c.getFilesDir(), "model-fr"); }

    public static File modelDir(Context c) { return new File(root(c), DIR_NAME); }

    public static boolean isReady(Context c) { return new File(root(c), "ready").exists(); }

    /** Ne fait rien si le modèle est déjà là. */
    public static synchronized void ensure(Context c, Progress p) throws Exception {
        if (isReady(c)) return;
        File root = root(c);
        deleteRec(root);
        root.mkdirs();
        File zip = new File(c.getFilesDir(), "model-fr.zip.tmp");
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(URL_ZIP).openConnection();
            con.setConnectTimeout(30000);
            con.setReadTimeout(60000);
            int code = con.getResponseCode();
            if (code != 200) throw new IOException("Serveur : code " + code);
            long total = con.getContentLengthLong();
            try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(zip)) {
                byte[] b = new byte[32768];
                long done = 0;
                int r, last = -1;
                while ((r = in.read(b)) > 0) {
                    out.write(b, 0, r);
                    done += r;
                    if (total > 0) {
                        int pct = (int) (done * 90 / total);   // 0-90 % : téléchargement
                        if (pct != last) { last = pct; p.onPercent(pct); }
                    }
                }
            }
            p.onPercent(90);
            unzip(zip, root);                                 // 90-100 % : décompression
            if (!modelDir(c).isDirectory()) throw new IOException("Archive du modèle invalide");
            new File(root, "ready").createNewFile();
            p.onPercent(100);
        } catch (Exception e) {
            deleteRec(root);
            throw e;
        } finally {
            zip.delete();
        }
    }

    private static void unzip(File zip, File dest) throws IOException {
        String destPath = dest.getCanonicalPath() + File.separator;
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry e;
            byte[] b = new byte[32768];
            while ((e = zin.getNextEntry()) != null) {
                File f = new File(dest, e.getName());
                if (!f.getCanonicalPath().startsWith(destPath)) throw new IOException("Chemin illégal dans l'archive");
                if (e.isDirectory()) { f.mkdirs(); continue; }
                f.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(f)) {
                    int r;
                    while ((r = zin.read(b)) > 0) out.write(b, 0, r);
                }
            }
        }
    }

    private static void deleteRec(File f) {
        if (f.isDirectory()) {
            File[] list = f.listFiles();
            if (list != null) for (File x : list) deleteRec(x);
        }
        f.delete();
    }
}
