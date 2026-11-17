package bj.radio;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Chaînes TV en direct branchées sur TV Kouamé : UNIQUEMENT les chaînes qui marchent, c'est-à-dire celles que l'écran
 * Télévision a vérifiées (fichier marche.tsv) ou que l'utilisateur a validées en les regardant (ajoutees.tsv).
 * Aucune autre chaîne n'est jamais diffusée. Ordre d'essai : la dernière qui a vraiment démarré (si elle est toujours
 * dans la liste des chaînes qui marchent), puis les chaînes validées à la main, puis les francophones, puis les autres.
 * Le lecteur essaie la suivante tant qu'une chaîne ne démarre pas.
 */
final class ChaineDirect {
    private ChaineDirect() { }

    static final class Chaine {
        final String nom, url;

        Chaine(String nom, String url) {
            this.nom = nom;
            this.url = url;
        }
    }

    private static final String PREFS = "tv";
    private static final String CLE_DERNIERE = "kouame_direct_url";
    private static final String CLE_DERNIERE_NOM = "kouame_direct_nom";
    private static final int MAX_VERIFIEES = 40;
    private static final String[] FRANCOPHONES = {"BJ", "FR", "CI", "SN", "TG", "BF", "ML", "NE", "CM", "GA", "CG", "CD", "GN", "MA", "TN", "DZ", "BE", "CH", "CA"};

    static List<Chaine> candidats(Context c) {
        List<Chaine> res = new ArrayList<>();
        Set<String> vus = new HashSet<>();
        List<Chaine> manuelles = new ArrayList<>(), fr = new ArrayList<>(), autres = new ArrayList<>();
        for (String f : new String[]{"ajoutees.tsv", "marche.tsv"}) {
            for (String[] t : lireTsv(new File(c.getFilesDir(), f))) {
                if (!t[2].startsWith("http") || !vus.add(t[2])) continue;
                Chaine ch = new Chaine(t[0], t[2]);
                if (f.equals("ajoutees.tsv")) manuelles.add(ch);        // validées à la main : les plus sûres
                else if (estFrancophone(t[1])) fr.add(ch);
                else autres.add(ch);
            }
        }
        res.addAll(manuelles);
        res.addAll(fr);
        res.addAll(autres);
        if (res.size() > MAX_VERIFIEES) res = new ArrayList<>(res.subList(0, MAX_VERIFIEES));
        // la dernière chaîne qui a démarré passe en tête, seulement si elle fait toujours partie des chaînes qui marchent
        String derniere = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CLE_DERNIERE, "");
        for (int i = 0; i < res.size(); i++) {
            if (res.get(i).url.equals(derniere)) {
                res.add(0, res.remove(i));
                break;
            }
        }
        return res;
    }

    /** Garde en mémoire la chaîne qui vient de démarrer : c'est celle qu'on essaiera en premier la prochaine fois. */
    static void retenir(Context c, Chaine ch) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(CLE_DERNIERE, ch.url).putString(CLE_DERNIERE_NOM, ch.nom).apply();
    }

    private static boolean estFrancophone(String cc) {
        String x = cc == null ? "" : cc.trim().toUpperCase(Locale.ROOT);
        for (String f : FRANCOPHONES) if (f.equals(x)) return true;
        return false;
    }

    /** Lignes « nom TAB pays TAB adresse » écrites par l'écran Télévision. */
    private static List<String[]> lireTsv(File f) {
        List<String[]> res = new ArrayList<>();
        if (f == null || !f.isFile()) return res;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] t = l.split("\t");
                if (t.length >= 3 && !t[2].trim().isEmpty()) res.add(new String[]{t[0].trim(), t[1].trim(), t[2].trim()});
            }
        } catch (Exception ignored) {
        }
        return res;
    }
}
