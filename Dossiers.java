package bj.radio;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Dossier « TvKouame » dans le stockage du téléphone : TvKouame/Telechargement contient toutes les vidéos téléchargées.
 * Il est créé dès que l'application a l'autorisation « accès à tous les fichiers » (Android 11+) ou « stockage » (Android 10-).
 * Sans cette autorisation, le même dossier est créé à l'intérieur du dossier privé de l'application (les téléchargements marchent quand même).
 */
final class Dossiers {

    static final String RACINE = "TvKouame";
    static final String TELECHARGEMENT = "Telechargement";

    private Dossiers() {}

    static boolean autorise(Context c) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        if (Build.VERSION.SDK_INT >= 23) {
            return c.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /** Demande l'autorisation d'écrire dans le stockage (ouvre l'écran des réglages sur Android 11+). */
    static void demanderAcces(Activity a) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + a.getPackageName())));
                } catch (Exception e) {
                    a.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                }
            } else if (Build.VERSION.SDK_INT >= 23) {
                a.requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 21);
            }
        } catch (Exception ignore) {
            // réglage indisponible : on garde le dossier privé
        }
    }

    /** Dossier des vidéos téléchargées (créé s'il n'existe pas) : TvKouame/Telechargement. Jamais null. */
    static File telechargement(Context c) {
        if (autorise(c)) {
            File d = new File(new File(Environment.getExternalStorageDirectory(), RACINE), TELECHARGEMENT);
            if (d.isDirectory() || d.mkdirs()) return d;
        }
        File base = c.getExternalFilesDir(null);
        if (base == null) base = c.getFilesDir();
        File d = new File(new File(base, RACINE), TELECHARGEMENT);
        d.mkdirs();
        return d;
    }

    /** Vrai si le dossier visible dans le stockage du téléphone est utilisable (autorisation donnée). */
    static boolean dossierPublic(Context c) {
        return autorise(c) && new File(new File(Environment.getExternalStorageDirectory(), RACINE), TELECHARGEMENT).isDirectory();
    }

    /** À appeler au lancement : crée TvKouame/Telechargement dès que c'est permis. */
    static void creer(Context c) {
        try {
            telechargement(c);
        } catch (Exception ignore) {
            // pas bloquant
        }
    }

    /** Demande l'autorisation une seule fois par installation (le bouton « Télécharger » la redemandera si besoin). */
    static boolean dejaDemande(Context c) {
        SharedPreferences sp = c.getSharedPreferences("dossiers", Context.MODE_PRIVATE);
        boolean d = sp.getBoolean("demande", false);
        sp.edit().putBoolean("demande", true).apply();
        return d;
    }

    /** Toutes les vidéos téléchargées : nouveau dossier TvKouame/Telechargement + anciens dossiers de l'appli. */
    static List<File> videos(Context c) {
        List<File> out = new ArrayList<>();
        List<String> vus = new ArrayList<>();
        List<File> dossiers = new ArrayList<>();
        dossiers.add(new File(new File(Environment.getExternalStorageDirectory(), RACINE), TELECHARGEMENT));
        File base = c.getExternalFilesDir(null);
        if (base == null) base = c.getFilesDir();
        dossiers.add(new File(new File(base, RACINE), TELECHARGEMENT));
        dossiers.add(c.getExternalFilesDir(Environment.DIRECTORY_MOVIES));     // anciennes versions
        for (File d : dossiers) {
            if (d == null) continue;
            File[] l = null;
            try { l = d.listFiles(); } catch (Exception ignore) { }
            if (l == null) continue;
            for (File f : l) {
                if (!f.isFile() || f.getName().endsWith(".part")) continue;
                String k = f.getAbsolutePath();
                if (vus.contains(k)) continue;
                vus.add(k);
                out.add(f);
            }
        }
        return out;
    }

    /** Fait apparaître le fichier dans la galerie / le gestionnaire de fichiers. */
    static void indexer(Context c, File f) {
        try {
            MediaScannerConnection.scanFile(c.getApplicationContext(), new String[]{f.getAbsolutePath()}, null, null);
        } catch (Exception ignore) {
            // pas bloquant
        }
    }
}
