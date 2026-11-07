package bj.radio;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Photo de profil du jeu « Questions pour un champion » : petite image portrait gardée sur le téléphone et envoyée à la base. */
final class QuizPhoto {

    private static final int LARG = 180, HAUT = 216;   // portrait : remplit la moitié du plateau comme à la télévision
    private static final int MAX_CARACTERES = 28000;      // la base refuse au-delà de 30 000

    private QuizPhoto() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("quiz", Context.MODE_PRIVATE);
    }

    /** Photo enregistrée sur ce téléphone, en texte (base64), ou null. */
    static String locale64(Context c) {
        String s = prefs(c).getString("photo64", null);
        return s == null || s.isEmpty() ? null : s;
    }

    static Bitmap locale(Context c) {
        return decoder(locale64(c));
    }

    static Bitmap decoder(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            byte[] o = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(o, 0, o.length);
        } catch (Exception e) {
            return null;
        }
    }

    static void effacer(Context c) {
        prefs(c).edit().remove("photo64").apply();
    }

    /** Lit l'image choisie, la recadre en portrait, la réduit et la garde. Renvoie la photo en base64, ou null si l'image est illisible. */
    static String enregistrer(Context c, Uri uri) {
        try {
            BitmapFactory.Options mesure = new BitmapFactory.Options();
            mesure.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, mesure);
            }
            int echantillon = 1;
            while (Math.min(mesure.outWidth / (echantillon * 2) / (float) LARG, mesure.outHeight / (echantillon * 2) / (float) HAUT) >= 1f) echantillon *= 2;
            BitmapFactory.Options lire = new BitmapFactory.Options();
            lire.inSampleSize = echantillon;
            Bitmap grande;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                grande = BitmapFactory.decodeStream(in, null, lire);
            }
            if (grande == null) return null;
            // recadrage portrait LARG:HAUT, centré en largeur, plutôt vers le haut (le visage)
            int gw = grande.getWidth(), gh = grande.getHeight();
            float ratio = LARG / (float) HAUT;
            int cw = gw, ch = gh;
            if (gw / (float) gh > ratio) cw = Math.round(gh * ratio); else ch = Math.round(gw / ratio);
            int cx = (gw - cw) / 2, cy = Math.max(0, Math.round((gh - ch) * 0.3f));
            Bitmap rogne = Bitmap.createBitmap(grande, cx, cy, cw, ch);
            Bitmap petite = Bitmap.createScaledBitmap(rogne, LARG, HAUT, true);
            String b64 = null;
            for (int qualite = 80; qualite >= 30; qualite -= 10) {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                petite.compress(Bitmap.CompressFormat.JPEG, qualite, o);
                b64 = Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
                if (b64.length() <= MAX_CARACTERES) break;
            }
            if (b64 == null || b64.length() > MAX_CARACTERES) return null;
            prefs(c).edit().putString("photo64", b64).apply();
            return b64;
        } catch (Exception e) {
            return null;
        }
    }
}
