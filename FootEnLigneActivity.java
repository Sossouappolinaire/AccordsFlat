package bj.radio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** Salon, invitations et attente d'un adversaire pour les deux jeux de football. */
public final class FootEnLigneActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout liste;
    private String mode = "foot";
    private String duel = "";
    private String adversaire = "";
    private boolean hote;
    private boolean lancement;
    private boolean attenteReponse;
    private boolean chargement;
    private long debutAttente;
    private int session;

    private final Runnable actualiser = this::chargerSalon;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mode = "pes".equals(getIntent().getStringExtra("mode")) ? "pes" : "foot";
        String modeInvitation = getIntent().getStringExtra("foot_invitation_mode");
        if ("foot".equals(modeInvitation) || "pes".equals(modeInvitation)) mode = modeInvitation;
        construireEcran();
        if (!Compte.connecte()) {
            Toast.makeText(this, "Connecte-toi à ton compte pour jouer en ligne.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (!traiterActionNotification()) chargerSalon();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView texte(String valeur, int taille, int couleur) {
        TextView t = new TextView(this);
        t.setText(valeur);
        t.setTextSize(taille);
        t.setTextColor(couleur);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private Button bouton(String valeur) {
        Button b = new Button(this);
        b.setText(valeur);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF245A3B));
        return b;
    }

    private void construireEcran() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(18), dp(20), dp(12));
        page.setBackgroundColor(0xFF101820);

        TextView titre = texte("⚽  FOOTBALL EN LIGNE", 22, 0xFFFFD54F);
        titre.setGravity(Gravity.CENTER);
        page.addView(titre, new LinearLayout.LayoutParams(-1, -2));

        TextView sousTitre = texte(
                ("pes".equals(mode) ? "Foot Pro Kouamé · 11 contre 11" : "Football Kouamé · 6 contre 6")
                        + "\nConnecté comme " + (Compte.nom().isEmpty() ? "Joueur" : Compte.nom()),
                14, 0xFFCFD8DC);
        sousTitre.setGravity(Gravity.CENTER);
        sousTitre.setPadding(0, dp(6), 0, dp(14));
        page.addView(sousTitre, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        liste = new LinearLayout(this);
        liste.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(liste, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button retour = bouton("← Retour");
        retour.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF37474F));
        retour.setOnClickListener(v -> {
            annulerAttente();
            finish();
        });
        page.addView(retour, new LinearLayout.LayoutParams(-1, -2));
        setContentView(page);
    }

    private void chargerSalon() {
        if (isFinishing() || lancement || attenteReponse || chargement) return;
        chargement = true;
        final int s = session;
        new Thread(() -> {
            JSONArray joueurs = FootReseau.joueursEnLigne();
            JSONArray invitations = FootReseau.invitations();
            runOnUiThread(() -> {
                chargement = false;
                if (s != session || isFinishing() || lancement || attenteReponse || liste == null) return;
                afficherSalon(joueurs, invitations);
                handler.removeCallbacks(actualiser);
                handler.postDelayed(actualiser, 4000);
            });
        }).start();
    }

    private void afficherSalon(JSONArray joueurs, JSONArray invitations) {
        liste.removeAllViews();
        liste.addView(texte("INVITATIONS REÇUES", 13, 0xFFFFD54F));
        boolean invitationPresentee = false;
        if (invitations != null) {
            for (int i = 0; i < invitations.length(); i++) {
                JSONObject invitation = invitations.optJSONObject(i);
                if (invitation == null) continue;
                invitationPresentee = true;
                final String id = invitation.optString("id", "");
                final String nom = invitation.optString("de", "Un joueur");
                final String modeInvite = "pes".equals(invitation.optString("mode")) ? "pes" : "foot";
                LinearLayout ligne = new LinearLayout(this);
                ligne.setOrientation(LinearLayout.VERTICAL);
                ligne.setPadding(dp(10), dp(5), dp(10), dp(5));
                ligne.setBackgroundColor(0xFF1D2933);
                ligne.addView(texte(nom + " t'invite à jouer "
                        + ("pes".equals(modeInvite) ? "au Foot Pro" : "au Football"), 15, Color.WHITE));
                LinearLayout choix = new LinearLayout(this);
                Button accepter = bouton("Accepter");
                accepter.setOnClickListener(v -> repondreInvitation(id, nom, modeInvite, true));
                Button refuser = bouton("Refuser");
                refuser.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF7B3030));
                refuser.setOnClickListener(v -> repondreInvitation(id, nom, modeInvite, false));
                choix.addView(accepter, new LinearLayout.LayoutParams(0, -2, 1f));
                choix.addView(refuser, new LinearLayout.LayoutParams(0, -2, 1f));
                ligne.addView(choix);
                liste.addView(ligne, new LinearLayout.LayoutParams(-1, -2));
            }
        }
        if (!invitationPresentee) {
            liste.addView(texte("Aucune invitation pour le moment.", 14, 0xFFB0BEC5));
        }

        liste.addView(texte("JOUEURS EN LIGNE", 13, 0xFFFFD54F));
        if (joueurs == null) {
            liste.addView(texte("Connexion impossible. Vérifie Internet et que la migration SQL du football en ligne a été installée.",
                    14, 0xFFFF8A80));
            return;
        }
        if (joueurs.length() == 0) {
            liste.addView(texte("Aucun autre joueur en ligne. Cette liste se renouvelle automatiquement.",
                    14, 0xFFB0BEC5));
            return;
        }
        for (int i = 0; i < joueurs.length(); i++) {
            JSONObject joueur = joueurs.optJSONObject(i);
            if (joueur == null) continue;
            String id = joueur.optString("id", "");
            String nom = joueur.optString("nom", "Joueur");
            Button inviter = bouton("🟢  " + nom + "  ·  Inviter");
            inviter.setOnClickListener(v -> envoyerInvitation(id, nom));
            liste.addView(inviter, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void envoyerInvitation(String idJoueur, String nom) {
        if (idJoueur.isEmpty()) return;
        handler.removeCallbacks(actualiser);
        attenteReponse = true;
        hote = true;
        adversaire = nom;
        debutAttente = System.currentTimeMillis();
        afficherAttente("Invitation envoyée à " + nom + "…");
        int s = ++session;
        new Thread(() -> {
            String id = FootReseau.inviter(idJoueur, mode);
            runOnUiThread(() -> {
                if (s != session || isFinishing()) {
                    if (id != null) new Thread(() -> FootReseau.quitter(id)).start();
                    return;
                }
                if (id == null) {
                    attenteReponse = false;
                    Toast.makeText(this, "Invitation impossible : " + Supabase.derniereErreur, Toast.LENGTH_LONG).show();
                    chargerSalon();
                    return;
                }
                duel = id;
                attendreReponse(s);
            });
        }).start();
    }

    private void attendreReponse(int s) {
        handler.postDelayed(() -> new Thread(() -> {
            JSONObject info = FootReseau.info(duel);
            runOnUiThread(() -> {
                if (s != session || isFinishing() || lancement) return;
                String etat = info == null ? "" : info.optString("etat", "");
                if ("accepte".equals(etat) || "en_cours".equals(etat)) {
                    String jeu = "pes".equals(info.optString("mode", mode)) ? "pes" : "foot";
                    lancerMatch(duel, jeu, true, adversaire);
                } else if ("refuse".equals(etat) || "annule".equals(etat) || "termine".equals(etat)
                        || "expire".equals(etat) || System.currentTimeMillis() - debutAttente > 100_000) {
                    attenteReponse = false;
                    duel = "";
                    Toast.makeText(this, nomAdversaire(info) + " n'a pas rejoint le match.",
                            Toast.LENGTH_LONG).show();
                    chargerSalon();
                } else {
                    afficherAttente("En attente de la réponse de " + adversaire + "…");
                    attendreReponse(s);
                }
            });
        }).start(), 1400);
    }

    private String nomAdversaire(JSONObject info) {
        return info == null ? adversaire : info.optString("invite_nom", adversaire);
    }

    private void repondreInvitation(String id, String nom, String modeInvite, boolean accepter) {
        handler.removeCallbacks(actualiser);
        FootReseau.annulerNotification(this, id);
        final int s = ++session;
        new Thread(() -> {
            String etat = FootReseau.repondre(id, accepter);
            runOnUiThread(() -> {
                if (s != session || isFinishing()) return;
                if (accepter && ("accepte".equals(etat) || "en_cours".equals(etat))) {
                    duel = id;
                    hote = false;
                    adversaire = nom;
                    lancerMatch(id, modeInvite, false, nom);
                } else {
                    chargerSalon();
                }
            });
        }).start();
    }

    private boolean traiterActionNotification() {
        String id = getIntent().getStringExtra("foot_invitation_id");
        String reponse = getIntent().getStringExtra("foot_invitation_reponse");
        if (id == null || id.isEmpty() || reponse == null || reponse.isEmpty()) return false;
        String nom = getIntent().getStringExtra("foot_invitation_nom");
        if (nom == null || nom.isEmpty()) nom = "Un joueur";
        final String adversaireNotification = nom;
        final String modeNotification = mode;
        FootReseau.annulerNotification(this, id);
        if ("non".equals(reponse)) {
            attenteReponse = true;
            int s = ++session;
            new Thread(() -> {
                FootReseau.repondre(id, false);
                runOnUiThread(() -> {
                    if (s != session || isFinishing()) return;
                    attenteReponse = false;
                    chargerSalon();
                });
            }).start();
            return true;
        }
        if (!"oui".equals(reponse)) return false;
        duel = id;
        adversaire = adversaireNotification;
        hote = false;
        attenteReponse = true;
        afficherAttente("Connexion au match de " + adversaireNotification + "…");
        int s = ++session;
        new Thread(() -> {
            String etat = FootReseau.repondre(id, true);
            runOnUiThread(() -> {
                if (s != session || isFinishing()) return;
                if ("accepte".equals(etat) || "en_cours".equals(etat)) {
                    lancerMatch(id, modeNotification, false, adversaireNotification);
                } else {
                    attenteReponse = false;
                    duel = "";
                    Toast.makeText(this, "Cette invitation n'est plus valable.", Toast.LENGTH_LONG).show();
                    chargerSalon();
                }
            });
        }).start();
        return true;
    }

    private void afficherAttente(String message) {
        if (liste == null) return;
        liste.removeAllViews();
        TextView t = texte("⏳  " + message, 18, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        liste.addView(t, new LinearLayout.LayoutParams(-1, dp(130)));
        TextView info = texte("Garde cette fenêtre ouverte pendant la connexion.", 14, 0xFFB0BEC5);
        info.setGravity(Gravity.CENTER);
        liste.addView(info, new LinearLayout.LayoutParams(-1, -2));
    }

    private void lancerMatch(String id, String modeMatch, boolean estHote, String nom) {
        if (lancement) return;
        lancement = true;
        attenteReponse = false;
        handler.removeCallbacks(actualiser);
        handler.removeCallbacksAndMessages(null);
        Intent intent = new Intent(this, JeuFootActivity.class)
                .putExtra("mode", modeMatch)
                .putExtra("online_duel", id)
                .putExtra("online_host", estHote)
                .putExtra("online_adversaire", nom);
        startActivity(intent);
        finish();
    }

    private void annulerAttente() {
        ++session;
        handler.removeCallbacksAndMessages(null);
        String id = duel;
        duel = "";
        attenteReponse = false;
        if (!id.isEmpty() && !lancement) new Thread(() -> FootReseau.quitter(id)).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!lancement && !attenteReponse) chargerSalon();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (isFinishing() && !lancement && !duel.isEmpty()) {
            String id = duel;
            new Thread(() -> FootReseau.quitter(id)).start();
        }
        super.onDestroy();
    }
}
