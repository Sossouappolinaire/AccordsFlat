package bj.radio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;

/** Réseau du mode football en ligne. Toutes les méthodes sont à appeler hors du thread UI. */
final class FootReseau {

    private FootReseau() {}
    private static final String CANAL = "foot_invitation";
    private static final HashSet<String> NOTIFIEES = new HashSet<>();

    private static JSONObject rpc(String fonction, JSONObject corps) {
        try {
            String reponse = Supabase.rest("POST", "rpc/" + fonction, corps.toString(), null);
            return reponse == null || reponse.trim().isEmpty() ? null : new JSONObject(reponse);
        } catch (Exception e) {
            return null;
        }
    }

    static JSONArray joueursEnLigne() {
        return MotardReseau.joueursEnLigne();
    }

    static JSONArray invitations() {
        try {
            String reponse = Supabase.rest("POST", "rpc/foot_ligne_invitations", "{}", null);
            return reponse == null ? null : new JSONArray(reponse);
        } catch (Exception e) {
            return null;
        }
    }

    static void verifierInvitations(Context context) {
        if (!Compte.connecte()) return;
        JSONArray liste = invitations();
        if (liste == null) return;
        for (int i = 0; i < liste.length(); i++) {
            JSONObject invitation = liste.optJSONObject(i);
            if (invitation == null) continue;
            String id = invitation.optString("id", "");
            synchronized (NOTIFIEES) {
                if (id.isEmpty() || !NOTIFIEES.add(id)) continue;
            }
            notifier(context, id, invitation.optString("de", "Un joueur"),
                    "pes".equals(invitation.optString("mode")) ? "pes" : "foot");
        }
    }

    static void annulerNotification(Context context, String duel) {
        NotificationManager gestionnaire =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (gestionnaire != null && duel != null) gestionnaire.cancel(duel.hashCode());
    }

    private static PendingIntent action(Context context, String duel, String nom, String mode,
                                        String reponse, int code) {
        Intent intent = new Intent(context, FootEnLigneActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("foot_invitation_id", duel)
                .putExtra("foot_invitation_nom", nom)
                .putExtra("foot_invitation_mode", mode)
                .putExtra("foot_invitation_reponse", reponse);
        return PendingIntent.getActivity(context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void notifier(Context context, String duel, String nom, String mode) {
        try {
            NotificationManager gestionnaire =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (gestionnaire == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                gestionnaire.createNotificationChannel(new NotificationChannel(
                        CANAL, "Football · invitations", NotificationManager.IMPORTANCE_HIGH));
            }
            int code = duel.hashCode();
            Notification.Builder notification = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, CANAL) : new Notification.Builder(context);
            notification.setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("⚽ Match de football")
                    .setContentText(nom + " t'invite à jouer "
                            + ("pes".equals(mode) ? "au Foot Pro." : "au Football."))
                    .setContentIntent(action(context, duel, nom, mode, "", code))
                    .setAutoCancel(true)
                    .addAction(new Notification.Action.Builder(null, "Accepter",
                            action(context, duel, nom, mode, "oui", code + 1)).build())
                    .addAction(new Notification.Action.Builder(null, "Refuser",
                            action(context, duel, nom, mode, "non", code + 2)).build());
            if (Build.VERSION.SDK_INT >= 26) notification.setTimeoutAfter(110_000);
            else notification.setPriority(Notification.PRIORITY_HIGH);
            gestionnaire.notify(code, notification.build());
        } catch (Exception ignore) {
        }
    }

    static String inviter(String idJoueur, String mode) {
        try {
            JSONObject resultat = rpc("foot_ligne_inviter",
                    new JSONObject().put("p_invite", idJoueur).put("p_mode", mode));
            return resultat == null ? null : resultat.optString("id", null);
        } catch (Exception e) {
            return null;
        }
    }

    static String repondre(String duel, boolean accepter) {
        try {
            JSONObject resultat = rpc("foot_ligne_repondre",
                    new JSONObject().put("p_duel", duel).put("p_oui", accepter));
            return resultat == null ? "" : resultat.optString("etat", "");
        } catch (Exception e) {
            return "";
        }
    }

    static JSONObject info(String duel) {
        try {
            return rpc("foot_ligne_info", new JSONObject().put("p_duel", duel));
        } catch (Exception e) {
            return null;
        }
    }

    static JSONObject synchroniser(String duel, JSONObject entree, JSONObject instantane) {
        try {
            JSONObject corps = new JSONObject()
                    .put("p_duel", duel)
                    .put("p_input", entree == null ? new JSONObject() : entree)
                    .put("p_snapshot", instantane == null ? JSONObject.NULL : instantane);
            return rpc("foot_ligne_sync", corps);
        } catch (Exception e) {
            return null;
        }
    }

    static void quitter(String duel) {
        try {
            rpc("foot_ligne_quitter", new JSONObject().put("p_duel", duel));
        } catch (Exception ignore) {
        }
    }
}
