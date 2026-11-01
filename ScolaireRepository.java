package bj.radio;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;

/**
 * Accès aux tables pédagogiques Supabase.
 *
 * Les contenus d'exercices sont publiés uniquement après vérification humaine
 * de leur source et de leur adéquation au programme béninois.
 */
final class ScolaireRepository {

    static final String[] CODES_NIVEAUX = {
            "CM2", "6E", "5E", "4E", "3E", "2NDE", "1ERE", "TLE"
    };

    static final String[] NOMS_NIVEAUX = {
            "CM2", "6e", "5e", "4e", "3e", "Seconde", "Première", "Terminale"
    };

    private ScolaireRepository() {}

    static String codeNiveau(int position) {
        if (position < 0 || position >= CODES_NIVEAUX.length) return CODES_NIVEAUX[0];
        return CODES_NIVEAUX[position];
    }

    static JSONArray listerMatieres(String codeNiveau) {
        String chemin = "scolaire_programmes"
                + "?select=code_matiere,statut_couverture,reference_source,"
                + "matiere:scolaire_matieres(code,nom,ordre)"
                + "&code_niveau=eq." + encoder(codeNiveau)
                + "&order=ordre.asc";
        String texte = Supabase.rest("GET", chemin, null, null);
        if (texte == null) return null;
        try {
            return new JSONArray(texte);
        } catch (JSONException e) {
            return null;
        }
    }

    static JSONArray listerExercices(String codeNiveau, String codeMatiere) {
        String chemin = "scolaire_exercices"
                + "?select=id,titre,objectif,competence,consigne,explication,correction,difficulte"
                + "&code_niveau=eq." + encoder(codeNiveau)
                + "&code_matiere=eq." + encoder(codeMatiere)
                + "&statut=eq.publie"
                + "&order=cree_le.asc";
        String texte = Supabase.rest("GET", chemin, null, null);
        if (texte == null) return null;
        try {
            return new JSONArray(texte);
        } catch (JSONException e) {
            return null;
        }
    }

    static JSONArray listerExposesTermines() {
        String chemin = "scolaire_exposes"
                + "?select=id,theme,nombre_pages,plan,contenu_pages,statut,cree_le"
                + "&statut=eq.termine"
                + "&order=cree_le.desc";
        String texte = Supabase.rest("GET", chemin, null, null);
        if (texte == null) return null;
        try {
            return new JSONArray(texte);
        } catch (JSONException e) {
            return null;
        }
    }

    static boolean enregistrerDemandeExpose(String theme, int nombrePages) {
        try {
            JSONObject demande = new JSONObject();
            demande.put("theme", theme.trim());
            demande.put("nombre_pages", nombrePages);
            demande.put("statut", "demande");
            return Supabase.rest("POST", "scolaire_exposes", demande.toString(), "return=minimal") != null;
        } catch (JSONException e) {
            return false;
        }
    }

    private static String encoder(String valeur) {
        try {
            return URLEncoder.encode(valeur == null ? "" : valeur, "UTF-8")
                    .replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }
}
