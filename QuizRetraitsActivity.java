package bj.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Administrateur : demandes de retrait des points gagnés au jeu « Questions pour un champion ».
 * Chaque demande contient le pays, le numéro, le nom et le prénom du joueur et le montant (1 point = 1 F).
 * « Confirmer » retire les points du solde du joueur ; « Refuser » les lui laisse. Les droits sont vérifiés par la base.
 */
public class QuizRetraitsActivity extends Activity {

    private LinearLayout liste;
    private TextView statut;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.fenetre(this);
        if (!Compte.admin()) {
            finish();
            return;
        }
        LinearLayout racine = new LinearLayout(this);
        racine.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        racine.setPadding(p, p, p, p);
        Ui.ajouter(racine, Ui.texte(this, "💰 Retraits du quiz", 24, Ui.JAUNE, true), 0);
        Ui.ajouter(racine, Ui.texte(this, "Payez le joueur au numéro indiqué, puis appuyez sur « Confirmer » : ses points sont alors retirés de son solde.", 13, Ui.TXT2, false), 6);
        Button actualiser = Ui.boutonContour(this, "🔄 Actualiser", Ui.TXT);
        actualiser.setOnClickListener(v -> charger());
        Ui.ajouter(racine, actualiser, 10);
        statut = Ui.texte(this, "Chargement…", 13, Ui.JAUNE, false);
        Ui.ajouter(racine, statut, 8);
        liste = new LinearLayout(this);
        liste.setOrientation(LinearLayout.VERTICAL);
        Ui.ajouter(racine, liste, 4);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackground(Ui.fond());
        sv.addView(racine);
        setContentView(sv);
        charger();
    }

    private void charger() {
        new Thread(() -> {
            final JSONObject r = QuizReseau.adminRetraits();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (r == null || !r.optBoolean("ok")) {
                    statut.setText("⚠ Impossible de charger" + (r == null ? "" : " : " + r.optString("erreur")));
                    return;
                }
                JSONArray a = r.optJSONArray("liste");
                int attente = 0;
                for (int i = 0; a != null && i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o != null && "en_attente".equals(o.optString("etat"))) attente++;
                }
                statut.setText(attente == 0 ? "Aucune demande en attente." : attente + " demande(s) en attente.");
                dessiner(a);
            });
        }).start();
    }

    private void dessiner(JSONArray a) {
        liste.removeAllViews();
        for (int i = 0; a != null && i < a.length(); i++) {
            final JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            final boolean attente = "en_attente".equals(o.optString("etat"));
            LinearLayout carte = new LinearLayout(this);
            carte.setOrientation(LinearLayout.VERTICAL);
            carte.setBackground(Ui.rond(Ui.CARD, 14, this));
            int q = Ui.dp(this, 12);
            carte.setPadding(q, q, q, q);

            Ui.ajouter(carte, Ui.texte(this, o.optString("prenom") + " " + o.optString("nom"), 17, Ui.TXT, true), 0);
            Ui.ajouter(carte, Ui.texte(this, "🌍 " + o.optString("pays") + "   📞 " + o.optString("numero"), 14, Ui.TXT, false), 4);
            TextView montant = Ui.texte(this, o.optInt("montant") + " F  (" + o.optInt("montant") + " points)", 20, Ui.VERT, true);
            Ui.ajouter(carte, montant, 6);
            String etat = attente ? "⏳ En attente" : "confirme".equals(o.optString("etat")) ? "✅ Confirmée" : "❌ Refusée";
            Ui.ajouter(carte, Ui.texte(this, etat + "  •  demandée le " + o.optString("cree"), 12, Ui.TXT2, false), 4);

            Button copier = Ui.boutonContour(this, "📋 Copier le numéro", Ui.TXT);
            copier.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Numéro", o.optString("numero")));
                Toast.makeText(this, "Numéro copié", Toast.LENGTH_SHORT).show();
            });
            Ui.ajouter(carte, copier, 8);

            if (attente) {
                LinearLayout ligne = new LinearLayout(this);
                ligne.setGravity(Gravity.CENTER_VERTICAL);
                Button ok = Ui.bouton(this, "✅ Confirmer", Ui.VERT);
                ok.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle("Confirmer le retrait ?")
                        .setMessage("Avez-vous payé " + o.optInt("montant") + " F à " + o.optString("prenom") + " " + o.optString("nom")
                                + " (" + o.optString("numero") + ") ?\n\nLes " + o.optInt("montant") + " points seront retirés de son solde.")
                        .setNegativeButton("Annuler", null)
                        .setPositiveButton("Confirmer", (d, w) -> traiter(o.optLong("id"), true))
                        .show());
                Button non = Ui.boutonContour(this, "✖ Refuser", Ui.TXT);
                non.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle("Refuser la demande ?")
                        .setMessage("Les points restent dans le solde du joueur, qui pourra faire une nouvelle demande.")
                        .setNegativeButton("Annuler", null)
                        .setPositiveButton("Refuser", (d, w) -> traiter(o.optLong("id"), false))
                        .show());
                ligne.addView(ok, new LinearLayout.LayoutParams(0, -2, 1f));
                ligne.addView(non, new LinearLayout.LayoutParams(0, -2, 1f));
                Ui.ajouter(carte, ligne, 6);
            }
            Ui.ajouter(liste, carte, 12);
        }
    }

    private void traiter(final long id, final boolean confirmer) {
        new Thread(() -> {
            final JSONObject r = QuizReseau.adminTraiter(id, confirmer);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (r == null) statut.setText("⚠ Pas de réponse. Vérifiez la connexion.");
                else if (!r.optBoolean("ok")) statut.setText("⚠ " + r.optString("erreur", "Erreur"));
                else statut.setText(confirmer ? "✅ Retrait confirmé, points retirés du solde." : "Demande refusée.");
                charger();
            });
        }).start();
    }
}
