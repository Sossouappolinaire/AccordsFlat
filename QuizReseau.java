package bj.radio;

import org.json.JSONArray;
import org.json.JSONObject;

/** Réseau du jeu « Questions pour un champion » en ligne (fonctions quiz_* de la base). À appeler hors du thread principal. */
final class QuizReseau {

    private QuizReseau() {}

    private static JSONObject rpc(String fonction, JSONObject corps) {
        try {
            String r = Supabase.rest("POST", "rpc/" + fonction, corps.toString(), null);
            return r == null || r.trim().isEmpty() ? null : new JSONObject(r);
        } catch (Exception e) {
            return null;
        }
    }

    /** Crée un salon ; réponse {ok, salon, code} ou {ok:false, erreur}. */
    static JSONObject creer(int nb) {
        try { return rpc("quiz_creer", new JSONObject().put("p_nb", nb)); } catch (Exception e) { return null; }
    }

    /** Salons ouverts : [{code, hote, nb, joueurs}], ou null si pas de réponse. */
    static JSONArray ouverts() {
        try {
            String r = Supabase.rest("POST", "rpc/quiz_salons_ouverts", "{}", null);
            return r == null ? null : new JSONArray(r);
        } catch (Exception e) {
            return null;
        }
    }

    static JSONObject rejoindre(String code) {
        try { return rpc("quiz_rejoindre", new JSONObject().put("p_code", code)); } catch (Exception e) { return null; }
    }

    static void quitter(String salon) {
        try { rpc("quiz_quitter", new JSONObject().put("p_salon", salon)); } catch (Exception ignore) { }
    }

    static JSONObject demarrer(String salon) {
        try { return rpc("quiz_demarrer", new JSONObject().put("p_salon", salon)); } catch (Exception e) { return null; }
    }

    /** État du salon : joueurs, scores, questions, heure du serveur. */
    static JSONObject etat(String salon) {
        try { return rpc("quiz_etat", new JSONObject().put("p_salon", salon)); } catch (Exception e) { return null; }
    }

    static JSONObject repondre(String salon, int num, int choix) {
        try {
            return rpc("quiz_repondre", new JSONObject().put("p_salon", salon).put("p_num", num).put("p_choix", choix));
        } catch (Exception e) {
            return null;
        }
    }

    /** Bonne réponse d'une question dont le temps est écoulé. */
    static JSONObject revele(String salon, int num) {
        try { return rpc("quiz_revele", new JSONObject().put("p_salon", salon).put("p_num", num)); } catch (Exception e) { return null; }
    }

    // ---------------------------------------------------------------- photos de profil

    /** Envoie ma photo (base64) à la base ; null ou vide pour la retirer. */
    static JSONObject definirPhoto(String photo64) {
        try { return rpc("quiz_photo_definir", new JSONObject().put("p_photo", photo64 == null ? "" : photo64)); } catch (Exception e) { return null; }
    }

    /** Photos des joueurs d'un salon : {ok, photos:{cle: base64}}. */
    static JSONObject photos(String salon) {
        try { return rpc("quiz_photos", new JSONObject().put("p_salon", salon)); } catch (Exception e) { return null; }
    }

    // ---------------------------------------------------------------- points gagnés et retraits

    /** Mes points gagnés : {ok, points, victoires, gain, attente:{montant}, dernier}. */
    static JSONObject mesGains() {
        return rpc("quiz_mes_gains", new JSONObject());
    }

    /** Envoie à l'administrateur la demande de retrait de tous mes points gagnés. */
    static JSONObject demanderRetrait(String pays, String numero, String nom, String prenom) {
        try {
            return rpc("quiz_retrait_demander", new JSONObject().put("p_pays", pays).put("p_numero", numero)
                    .put("p_nom", nom).put("p_prenom", prenom));
        } catch (Exception e) {
            return null;
        }
    }

    /** Administrateur : demandes de retrait {ok, liste:[{id, nom, prenom, pays, numero, montant, etat, cree}]}. */
    static JSONObject adminRetraits() {
        return rpc("admin_quiz_retraits", new JSONObject());
    }

    /** Administrateur : confirme (points retirés du solde) ou refuse une demande. */
    static JSONObject adminTraiter(long id, boolean confirmer) {
        try {
            return rpc("admin_quiz_retrait_traiter", new JSONObject().put("p_id", id).put("p_confirmer", confirmer));
        } catch (Exception e) {
            return null;
        }
    }
}
