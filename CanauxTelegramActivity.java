package bj.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Admin : canaux Telegram privés. Chaque canal (nom + lien d'invitation t.me) reçoit un lien à partager :
 * « adresse de la page ? c = code ». La page affiche le nom du canal et un bouton « Ouvrir » qui redirige vers Telegram.
 * Les droits sont vérifiés par la base (est_admin) : cet écran seul ne donne aucun pouvoir.
 */
public class CanauxTelegramActivity extends Activity {

    /** Page servie par la fonction Edge « canal ». Modifiable dans l'écran (par ex. vers canal.html sur GitHub Pages). */
    private static final String PAGE_DEFAUT = "https://ooccarepvhiqireyxjdl.supabase.co/functions/v1/canal";

    private EditText nom, lien, page;
    private Button enregistrer;
    private TextView statut;
    private LinearLayout listeVue;
    private String idEnEdition = null;
    private JSONArray liste = new JSONArray();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.fenetre(this);
        if (!Compte.admin()) {
            finish();
            return;
        }

        LinearLayout pageVue = new LinearLayout(this);
        pageVue.setOrientation(LinearLayout.VERTICAL);
        pageVue.setBackground(Ui.fond());
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        c.setPadding(p, p, p, p);

        Ui.ajouter(c, Ui.texte(this, "📣 Canaux Telegram", 24, Ui.TXT, true), 0);
        Ui.ajouter(c, Ui.texte(this, "Ajoutez le nom du canal et son lien d'invitation (t.me/+…). Un lien à partager est créé : la page affiche le nom et un bouton « Ouvrir » qui mène au canal.",
                13, Ui.TXT2, false), 6);

        nom = Ui.champ(this, "Nom du canal (tel qu'il apparaît dans Telegram)");
        lien = Ui.champ(this, "Lien Telegram (https://t.me/+…)");
        lien.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        Ui.ajouter(c, nom, 14);
        Ui.ajouter(c, lien, 8);

        enregistrer = Ui.bouton(this, "➕ Ajouter le canal", Ui.ACCENT);
        enregistrer.setOnClickListener(v -> enregistrer());
        Ui.ajouter(c, enregistrer, 10);
        statut = Ui.texte(this, "", 13, Ui.JAUNE, false);
        Ui.ajouter(c, statut, 8);

        Ui.ajouter(c, Ui.texte(this, "Canaux enregistrés", 18, Ui.TXT, true), 18);
        listeVue = new LinearLayout(this);
        listeVue.setOrientation(LinearLayout.VERTICAL);
        Ui.ajouter(c, listeVue, 6);

        Ui.ajouter(c, Ui.texte(this, "Adresse de la page web", 16, Ui.TXT, true), 22);
        page = Ui.champ(this, "Adresse de la page");
        page.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        page.setText(prefs().getString("page", PAGE_DEFAUT));
        Ui.ajouter(c, page, 8);
        Button sauverPage = Ui.boutonContour(this, "Enregistrer l'adresse", Ui.TXT);
        sauverPage.setOnClickListener(v -> {
            String a = page.getText().toString().trim();
            if (!a.startsWith("https://")) { statut.setText("⚠ L'adresse doit commencer par https://"); return; }
            prefs().edit().putString("page", a).apply();
            statut.setText("✅ Adresse enregistrée.");
        });
        Ui.ajouter(c, sauverPage, 8);

        ScrollView sv = new ScrollView(this);
        sv.addView(c);
        pageVue.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(pageVue);
        charger();
    }

    private SharedPreferences prefs() { return getSharedPreferences("canaux_telegram", MODE_PRIVATE); }

    private String lienPartage(String code) {
        String a = prefs().getString("page", PAGE_DEFAUT);
        return a + (a.contains("?") ? "&" : "?") + "c=" + code;
    }

    private void charger() {
        new Thread(() -> {
            final JSONArray r = Supabase.adminCanauxListe();
            runOnUiThread(() -> {
                if (r == null) { statut.setText("⚠ Impossible de charger (" + Supabase.derniereErreur + ")"); return; }
                liste = r;
                dessiner();
            });
        }).start();
    }

    private void dessiner() {
        listeVue.removeAllViews();
        if (liste.length() == 0) {
            Ui.ajouter(listeVue, Ui.texte(this, "Aucun canal pour le moment.", 14, Ui.TXT2, false), 4);
            return;
        }
        for (int i = 0; i < liste.length(); i++) {
            final JSONObject o = liste.optJSONObject(i);
            if (o == null) continue;
            Ui.ajouter(listeVue, Ui.texte(this, "📣 " + o.optString("nom"), 16, Ui.TXT, true), 14);
            Ui.ajouter(listeVue, Ui.texte(this, lienPartage(o.optString("code")), 12, Ui.TXT2, false), 2);

            LinearLayout ligne = new LinearLayout(this);
            Button copier = Ui.bouton(this, "📋 Copier", Ui.VIOLET);
            copier.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Lien", lienPartage(o.optString("code"))));
                Toast.makeText(this, "Lien copié", Toast.LENGTH_SHORT).show();
            });
            Button partager = Ui.bouton(this, "📤 Partager", Ui.VERT);
            partager.setOnClickListener(v -> {
                Intent s = new Intent(Intent.ACTION_SEND);
                s.setType("text/plain");
                s.putExtra(Intent.EXTRA_TEXT, o.optString("nom") + "\n" + lienPartage(o.optString("code")));
                startActivity(Intent.createChooser(s, "Partager le lien"));
            });
            ligne.addView(copier, new LinearLayout.LayoutParams(0, -2, 1f));
            ligne.addView(partager, new LinearLayout.LayoutParams(0, -2, 1f));
            Ui.ajouter(listeVue, ligne, 4);

            LinearLayout ligne2 = new LinearLayout(this);
            Button modifier = Ui.boutonContour(this, "✏ Modifier", Ui.TXT);
            modifier.setOnClickListener(v -> {
                idEnEdition = o.optString("id");
                nom.setText(o.optString("nom"));
                lien.setText(o.optString("lien"));
                enregistrer.setText("💾 Enregistrer la modification");
                statut.setText("Modification de « " + o.optString("nom") + " »");
            });
            Button supprimer = Ui.boutonContour(this, "🗑 Supprimer", Ui.TXT);
            supprimer.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Supprimer ce canal ?")
                    .setMessage("Son lien partagé cessera de fonctionner.")
                    .setPositiveButton("Supprimer", (d, w) -> new Thread(() -> {
                        final boolean ok = Supabase.adminCanalSupprimer(o.optString("id"));
                        runOnUiThread(() -> { statut.setText(ok ? "✅ Canal supprimé." : "⚠ Impossible de supprimer."); if (ok) charger(); });
                    }).start())
                    .setNegativeButton("Annuler", null)
                    .show());
            ligne2.addView(modifier, new LinearLayout.LayoutParams(0, -2, 1f));
            ligne2.addView(supprimer, new LinearLayout.LayoutParams(0, -2, 1f));
            Ui.ajouter(listeVue, ligne2, 4);
        }
    }

    private void enregistrer() {
        final String n = nom.getText().toString().trim();
        final String l = lien.getText().toString().trim();
        if (n.isEmpty()) { statut.setText("⚠ Écrivez le nom du canal."); return; }
        if (!l.matches("(?i)^https://(t\\.me|telegram\\.me)/\\S+$")) {
            statut.setText("⚠ Le lien doit commencer par https://t.me/");
            return;
        }
        final String id = idEnEdition;
        statut.setText("Envoi…");
        new Thread(() -> {
            final JSONObject r = id == null ? Supabase.adminCanalAjouter(n, l) : Supabase.adminCanalModifier(id, n, l);
            runOnUiThread(() -> {
                if (r == null) { statut.setText("⚠ Impossible (" + Supabase.derniereErreur + ")"); return; }
                statut.setText(id == null ? "✅ Canal ajouté. Copiez ou partagez son lien ci-dessous." : "✅ Modification enregistrée.");
                nom.setText("");
                lien.setText("");
                idEnEdition = null;
                enregistrer.setText("➕ Ajouter le canal");
                charger();
            });
        }).start();
    }
}
