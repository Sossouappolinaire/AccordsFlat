package bj.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;

/** Menu « JEUX » : Panda Dash, Super Kouamé (style Mario), Serpent, Commando Kouamé (tir et action), Liquide (tri de couleurs), Combat Mortel (robot ou joueur en ligne), Football Kouamé (arcade), Foot Pro Kouamé (simulation) et GTA Kouamé (monde ouvert : voitures, police, missions). Les records sont gardés sur le téléphone. */
public class JeuxActivity extends Activity {

    private static final String PREFS = "jeux";

    private static final String[] JEUX = {"panda", "mario", "serpent", "commando", "liquide", "motard", "foot", "pes", "quiz", "casse", "gta"};

    /** Par jeu : [0] mon record, [1] meilleur joueur de tous les joueurs. */
    private final HashMap<String, TextView[]> champs = new HashMap<>();

    static int record(Context c, String cle) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(cle, 0);
    }

    /** Vrai si le téléphone a une connexion internet. Les jeux, eux, fonctionnent toujours sans. */
    static boolean internet(Context c) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    private static String nomJeu(String cle) {
        switch (cle) {
            case "panda": return "Panda Dash";
            case "mario": return "Super Kouamé";
            case "serpent": return "Serpent";
            case "commando": return "Commando Kouamé";
            case "liquide": return "Liquide";
            case "casse": return "Cœur Casse-Briques";
            case "gta": return "GTA Kouamé";
            default: return cle;
        }
    }

    private static String aujourdhui() {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("Africa/Porto-Novo"));
        return f.format(new java.util.Date());
    }

    /** Meilleur score fait aujourd'hui sur ce téléphone pour ce jeu (0 si aucun). */
    static int recordDuJour(Context c, String cle) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return aujourdhui().equals(sp.getString("jour_" + cle, "")) ? sp.getInt("jourscore_" + cle, 0) : 0;
    }

    static void enregistrer(Context c, String cle, int valeur) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean record = valeur > sp.getInt(cle, 0);
        boolean duJour = ScoresJeux.solo(cle) && valeur > recordDuJour(c, cle);
        if (record) sp.edit().putInt(cle, valeur).apply();
        if (duJour) sp.edit().putString("jour_" + cle, aujourdhui()).putInt("jourscore_" + cle, valeur).apply();
        if (record) {
            ScoresJeux.envoyer(cle, valeur);     // classement partagé par tous les joueurs (envoie aussi le score du jour)
        } else if (duJour) {
            final int v = valeur;                // pas un record, mais le meilleur score d'aujourd'hui : compte pour le prix du jour
            new Thread(() -> ScoresJeux.envoyerJourSync(cle, v)).start();
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.fenetre(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        root.setPadding(p, p, p, p);

        Ui.ajouter(root, Ui.texte(this, "🎮 Jeux", 28, Ui.JAUNE, true), 0);
        Ui.ajouter(root, Ui.texte(this, "Choisissez un jeu et battez votre record !", 14, Ui.TXT2, false), 4);
        Ui.ajouter(root, Ui.texte(this, "🎁 Jeux solo : le meilleur score de la journée de chaque jeu gagne 10 F, à condition d'avoir progressé (battre son propre meilleur score d'avant). Payé le lendemain, de minuit à minuit.", 13, Ui.VERT, true), 6);

        carte(root, "panda", "🐼", "Panda Dash",
                "Cours, saute, ramasse les pièces et écrase les scarabées rouges jusqu'au drapeau.",
                0xFF2E9E73, 0xFF9BCB3C,
                new Intent(this, JeuPlateformeActivity.class).putExtra("mode", "panda"));

        carte(root, "mario", "🍄", "Super Kouamé",
                "Un jeu de plateformes comme Mario : briques, tuyaux, champignons méchants et niveaux de plus en plus durs.",
                0xFFD32F2F, 0xFFFF9800,
                new Intent(this, JeuPlateformeActivity.class).putExtra("mode", "mario"));

        carte(root, "quiz", "🧠", "Questions pour un champion",
                "Quiz de culture générale : les 4 réponses sont lues à voix haute. Joue seul ou en ligne contre les autres joueurs.",
                0xFF6A1B9A, 0xFF1565C0,
                new Intent(this, JeuQuizActivity.class));

        carte(root, "serpent", "🐍", "Serpent",
                "Glissez le doigt pour tourner, mangez les pommes, ne touchez ni les murs ni votre queue.",
                0xFF1E8E3E, 0xFF12B5CB,
                new Intent(this, JeuSerpentActivity.class));

        carte(root, "casse", "💖", "Cœur Casse-Briques",
                "Casse-briques néon : brise les cœurs, attrape les bonus (raquette large, trois boules, ralenti), enchaîne les combos et évite les astéroïdes.",
                0xFFC2185B, 0xFF5E35B1,
                new Intent(this, JeuCasseBriquesActivity.class));

        carte(root, "gta", "🚗", "GTA Kouamé",
                "Monde ouvert : une grande ville vue de dessus. Vole des voitures, tire, évite la police (jusqu'à 5 étoiles), livre des colis et gagne un maximum d'argent.",
                0xFF263238, 0xFF1E78DC,
                new Intent(this, JeuGtaActivity.class));

        carte(root, "commando", "🔫", "Commando Kouamé",
                "Un jeu d'action et de tir comme les anciens jeux de console : cours, saute, tire sur les soldats et détruis le robot de chaque niveau.",
                0xFF37474F, 0xFFFF5722,
                new Intent(this, JeuCommandoActivity.class));

        carte(root, "liquide", "🧪", "Liquide",
                "Versez les liquides d'un tube à l'autre pour que chaque tube ne contienne plus qu'une seule couleur. Les niveaux se compliquent.",
                0xFF00897B, 0xFF8E24AA,
                new Intent(this, JeuLiquideActivity.class));

        carte(root, "motard", "🥊", "Combat Mortel",
                "Jeu de combat : poings, pieds, garde et attaque spéciale. Affronte le robot (10 niveaux, très fort) ou invite un joueur en ligne.",
                0xFFB71C1C, 0xFF7B4DFF,
                new Intent(this, JeuMotardActivity.class));

        carte(root, "foot", "⚽", "Football Kouamé",
                "Football arcade 6 contre 6 en caméra TV : dribble, passes, tirs puissants, tacles et glissades. Choisis ton équipe et le niveau.",
                0xFF1B8E3E, 0xFF0D47A1,
                new Intent(this, JeuFootActivity.class).putExtra("mode", "foot"));

        carte(root, "pes", "🏟️", "Foot Pro Kouamé",
                "Football de simulation 11 contre 11 : jeu posé, fatigue, passes en profondeur, gardiens et matchs plus longs, dans le style des grands jeux de foot.",
                0xFF0B3D91, 0xFF00897B,
                new Intent(this, JeuFootActivity.class).putExtra("mode", "pes"));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackground(Ui.fond());
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (String jeu : JEUX) {
            TextView[] f = champs.get(jeu);
            f[0].setText("🏆 Mon record : " + record(this, jeu));
            f[1].setText(texteMonde(ScoresJeux.meilleur(jeu)));
        }
        // Renvoie mes records (au cas où l'envoi avait échoué hors connexion) et relit les meilleurs scores de tous.
        new Thread(() -> {
            if (!internet(this)) return;          // hors ligne : on joue avec les records du téléphone, rien à envoyer
            for (String jeu : JEUX) {
                ScoresJeux.envoyerSync(jeu, record(this, jeu));
                ScoresJeux.envoyerJourSync(jeu, recordDuJour(this, jeu));   // renvoie le meilleur score d'aujourd'hui si l'envoi avait échoué
                String t = ScoresJeux.actualiser(jeu);
                runOnUiThread(() -> {
                    TextView[] f = champs.get(jeu);
                    if (f != null) f[1].setText(texteMonde(t));
                });
            }
            JSONArray prix = ScoresJeux.payerJours();
            if (prix != null && prix.length() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < prix.length(); i++) {
                    JSONObject o = prix.optJSONObject(i);
                    if (o == null) continue;
                    sb.append("• ").append(nomJeu(o.optString("jeu"))).append(" (").append(o.optString("jour")).append(") : score ")
                            .append(o.optInt("score")).append(" → +").append(o.optInt("points")).append(" F\n");
                }
                final String txt = sb.toString().trim();
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    new AlertDialog.Builder(this)
                            .setTitle("🎁 Bravo, meilleur score du jour !")
                            .setMessage(txt + "\n\nCes points sont ajoutés à vos points gagnés (menu Questions pour un champion).")
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }).start();
    }

    private static String texteMonde(String t) {
        return t.isEmpty() ? "🥇 Meilleur joueur : personne encore, soyez le premier !" : "🥇 Meilleur joueur : " + t;
    }

    /** Fenêtre « Top 10 » d'un jeu, lue dans la base (visible par tous les joueurs). */
    private void afficherClassement(String jeu, String titre) {
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("🏆 " + titre)
                .setMessage("Chargement…")
                .setPositiveButton("Fermer", null)
                .create();
        dlg.show();
        if (!internet(this)) {
            dlg.setMessage("Pas d'internet : le classement s'affichera quand vous serez connecté. Vous pouvez jouer hors ligne, votre record est gardé sur le téléphone.");
            return;
        }
        new Thread(() -> {
            JSONArray a = ScoresJeux.classement(jeu, 10);
            String txt;
            if (a == null) {
                txt = "Impossible de lire le classement. Vérifiez votre connexion internet.";
            } else if (a.length() == 0) {
                txt = "Aucun score pour le moment. Soyez le premier !";
            } else {
                StringBuilder sb = new StringBuilder();
                String moi = Compte.nom();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o == null) continue;
                    String nom = o.optString("nom", "Joueur");
                    sb.append(i + 1).append(". ").append(nom).append(" — ").append(o.optInt("score"));
                    if (!moi.isEmpty() && moi.equals(nom)) sb.append("   ← vous");
                    sb.append('\n');
                }
                txt = sb.toString().trim();
            }
            final String fin = txt;
            runOnUiThread(() -> dlg.setMessage(fin));
        }).start();
    }

    private void carte(LinearLayout parent, String jeu, String emoji, String titre, String desc, int c1, int c2, Intent it) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.HORIZONTAL);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setBackground(Ui.onde(c1, c2, 22, this));
        t.setElevation(Ui.dp(this, 4));
        int q = Ui.dp(this, 14);
        t.setPadding(q, q, q, q);
        t.setClickable(true);
        t.setOnClickListener(v -> startActivity(it));
        Ui.pression(t);

        TextView e = Ui.texte(this, emoji, 44, Ui.TXT, false);
        t.addView(e);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.texte(this, titre, 20, Ui.TXT, true));
        col.addView(Ui.texte(this, desc, 12, 0xDDFFFFFF, false));
        TextView rec = Ui.texte(this, "", 13, Ui.JAUNE, true);
        col.addView(rec, Ui.lp(this, -2, -2, 0f, 4, 0));
        TextView monde = Ui.texte(this, "", 13, 0xFFFFF59D, true);
        col.addView(monde, Ui.lp(this, -2, -2, 0f, 2, 0));
        TextView classement = Ui.texte(this, "📊 Voir le classement (Top 10)", 13, Ui.TXT, true);
        classement.setPadding(0, Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 4));
        classement.setOnClickListener(v -> afficherClassement(jeu, titre));
        col.addView(classement);
        champs.put(jeu, new TextView[]{rec, monde});

        LinearLayout.LayoutParams lc = new LinearLayout.LayoutParams(0, -2, 1f);
        lc.leftMargin = Ui.dp(this, 14);
        t.addView(col, lc);

        LinearLayout.LayoutParams lt = new LinearLayout.LayoutParams(-1, -2);
        lt.topMargin = Ui.dp(this, 14);
        parent.addView(t, lt);
    }
}
