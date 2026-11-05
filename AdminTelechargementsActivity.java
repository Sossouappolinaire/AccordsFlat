package bj.radio;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Administrateur : voit TOUS les téléchargements de films et séries (qui, quoi, quand), les films les plus
 * téléchargés et le total par utilisateur. Rafraîchi automatiquement toutes les 20 secondes.
 * Les droits sont vérifiés par la base (admin_telechargements) : cet écran seul ne donne aucun pouvoir.
 */
public class AdminTelechargementsActivity extends Activity {

    private TextView etat, chiffres, top, parUtilisateur, liste;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean actif = false;
    private final Runnable boucle = new Runnable() {
        @Override public void run() {
            if (!actif) return;
            charger();
            h.postDelayed(this, 20000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.fenetre(this);
        if (!Compte.admin()) { finish(); return; }

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackground(Ui.fond());
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        c.setPadding(p, p, p, p);

        Ui.ajouter(c, Ui.texte(this, "📥 Téléchargements", 24, Ui.TXT, true), 0);
        etat = Ui.texte(this, "⏳ Connexion à la base…", 13, Ui.TXT2, false);
        Ui.ajouter(c, etat, 6);
        chiffres = Ui.texte(this, "", 16, Ui.JAUNE, true);
        Ui.ajouter(c, chiffres, 12);
        Button actu = Ui.bouton(this, "🔄 Actualiser", Ui.VIOLET);
        actu.setOnClickListener(v -> charger());
        Ui.ajouter(c, actu, 8);

        Ui.ajouter(c, Ui.texte(this, "🏆 Les plus téléchargés", 18, Ui.TXT, true), 20);
        top = Ui.texte(this, "", 13, Ui.TXT2, false);
        Ui.ajouter(c, top, 6);

        Ui.ajouter(c, Ui.texte(this, "👤 Par utilisateur", 18, Ui.TXT, true), 20);
        parUtilisateur = Ui.texte(this, "", 13, Ui.TXT2, false);
        Ui.ajouter(c, parUtilisateur, 6);

        Ui.ajouter(c, Ui.texte(this, "🕒 Derniers téléchargements", 18, Ui.TXT, true), 20);
        liste = Ui.texte(this, "", 13, Ui.TXT2, false);
        Ui.ajouter(c, liste, 6);

        ScrollView sv = new ScrollView(this);
        sv.addView(c);
        page.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        actif = true;
        h.removeCallbacks(boucle);
        h.post(boucle);
    }

    @Override
    protected void onPause() {
        actif = false;
        h.removeCallbacks(boucle);
        super.onPause();
    }

    private void charger() {
        new Thread(() -> {
            final JSONObject r = Supabase.adminTelechargements();
            final String erreur = Supabase.derniereErreur == null ? "" : Supabase.derniereErreur;
            runOnUiThread(() -> afficher(r, erreur));
        }).start();
    }

    private static String nomFilm(JSONObject o) {
        String t = o.isNull("titre") ? "" : o.optString("titre", "");
        if (t.isEmpty()) t = o.isNull("lien") ? "Lien inconnu" : o.optString("lien", "Lien inconnu");
        String serie = o.isNull("serie") ? "" : o.optString("serie", "").trim();
        if (!serie.isEmpty()) {
            t = "📺 " + serie + (o.isNull("episode") ? "" : " · épisode " + o.optInt("episode")) + " — " + t;
        } else {
            t = "🎬 " + t;
        }
        return t;
    }

    private void afficher(JSONObject r, String erreur) {
        if (isFinishing()) return;
        String heure = new SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(new Date());
        if (r == null || !r.optBoolean("ok")) {
            String cause = erreur.contains("http 404")
                    ? "la fonction admin_telechargements n'existe pas dans la base (exécutez section23_purge_versions_telechargements.sql dans Supabase)."
                    : erreur.isEmpty() ? "aucune réponse de la base." : erreur;
            etat.setText("🔴 Base non connectée : " + cause + "\nDernier essai à " + heure);
            return;
        }
        etat.setText("🟢 Connecté à la base · mis à jour à " + heure);
        chiffres.setText(r.optLong("total") + " téléchargements · " + r.optLong("utilisateurs") + " utilisateur(s)");

        StringBuilder sb = new StringBuilder();
        JSONArray t = r.optJSONArray("top");
        for (int i = 0; t != null && i < t.length(); i++) {
            JSONObject o = t.optJSONObject(i);
            if (o == null) continue;
            sb.append(i + 1).append(". ").append(nomFilm(o)).append("\n    ")
                    .append(o.optInt("nb")).append(" téléchargement(s) · ").append(o.optInt("personnes")).append(" personne(s)\n");
        }
        top.setText(sb.length() == 0 ? "Aucun téléchargement pour l'instant." : sb.toString().trim());

        sb = new StringBuilder();
        JSONArray u = r.optJSONArray("par_utilisateur");
        for (int i = 0; u != null && i < u.length(); i++) {
            JSONObject o = u.optJSONObject(i);
            if (o == null) continue;
            String mail = o.optString("email", "");
            sb.append("• ").append(o.optString("nom", "?")).append(mail.isEmpty() ? "" : " (" + mail + ")")
                    .append(" — ").append(o.optInt("nb")).append(" · dernier le ")
                    .append(Config.date(o.optLong("dernier_ms", 0L))).append('\n');
        }
        parUtilisateur.setText(sb.length() == 0 ? "—" : sb.toString().trim());

        sb = new StringBuilder();
        JSONArray l = r.optJSONArray("liste");
        for (int i = 0; l != null && i < l.length(); i++) {
            JSONObject o = l.optJSONObject(i);
            if (o == null) continue;
            sb.append(Config.date(o.optLong("cree_ms", 0L))).append("\n👤 ").append(o.optString("nom", "?"))
                    .append("\n").append(nomFilm(o)).append("\n\n");
        }
        liste.setText(sb.length() == 0 ? "—" : sb.toString().trim());
    }
}
