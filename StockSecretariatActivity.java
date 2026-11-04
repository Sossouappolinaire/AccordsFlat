package bj.radio;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Secrétariat.
 *  - Secrétaire : prépare une liste en brouillon (nom, 5/15, 5/10), tape « Envoyer », voit la liste complète
 *    puis « Modifier » ou « Valider » ; il voit le stock de son massier et ses listes envoyées.
 *  - Administrateur : lit les listes avec le prix de production de chaque producteur, et peut modifier,
 *    ajouter ou supprimer des lignes.
 * Une liste de plus de 2 jours passe dans « Voir les anciens points ».
 */
public class StockSecretariatActivity extends StockBaseActivity {
    // valeurs à remettre dans les champs (Modifier une ligne du brouillon)
    private String pNom = "", pA = "", pB = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        init("Secrétariat", StockSession.admin() ? "Listes envoyées par les secrétaires"
                : "Massier : " + StockSession.massier, true, false);
    }

    @Override protected void render() {
        showFlash();
        if (StockSession.admin()) renderAdmin(); else renderSecretaire();
    }

    // ------------------------------------------------------------------ brouillon (gardé sur le téléphone)

    private SharedPreferences prefs() { return getSharedPreferences("stock_brouillon", MODE_PRIVATE); }
    private String cle() { return "b_" + StockSession.phone; }

    private JSONArray brouillon() {
        try { return new JSONArray(prefs().getString(cle(), "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private void sauver(JSONArray a) { prefs().edit().putString(cle(), a.toString()).apply(); }

    private JSONArray sans(JSONArray a, int index) {
        JSONArray r = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (i != index) r.put(a.opt(i));
        return r;
    }

    private TextView bouton(String texte, int couleur, View.OnClickListener l) {
        TextView t = StockUi.pill(this, texte, couleur);
        t.setTextSize(13);
        t.setLayoutParams(StockUi.lp(this, -2, -2, 0, 10, 10, 0));
        t.setPadding(StockUi.dp(this, 16), StockUi.dp(this, 8), StockUi.dp(this, 16), StockUi.dp(this, 8));
        t.setOnClickListener(l);
        return t;
    }

    // ------------------------------------------------------------------ secrétaire

    private void renderSecretaire() {
        int[] st = db.stock(StockSession.massier);
        LinearLayout sc = StockUi.card(this);
        sc.addView(StockUi.tv(this, "Stock de " + StockSession.massier, 13, StockUi.GRAY, true));
        sc.addView(StockUi.tv(this, "5/15 = " + st[0] + "   |   5/10 = " + st[1], 18, StockUi.BLUE, true));
        body.addView(sc);

        body.addView(StockUi.sectionTitle(this, "Nouvelle ligne du brouillon", StockUi.ORANGE));
        LinearLayout f = StockUi.card(this);
        final EditText nm = StockUi.et(this, "Nom du producteur", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText a = StockUi.num(this, "Nombre de 5/15");
        final EditText c = StockUi.num(this, "Nombre de 5/10");
        nm.setText(pNom); a.setText(pA); c.setText(pB);
        pNom = ""; pA = ""; pB = "";
        Button add = StockUi.btn(this, "Ajouter à la liste", StockUi.GREEN);
        f.addView(nm); f.addView(a); f.addView(c); f.addView(add);
        body.addView(f);
        add.setOnClickListener(v -> {
            String name = nm.getText().toString().trim().replaceAll("\\s+", " ");
            int n15 = parse(a), n10 = parse(c);
            if (name.isEmpty()) { toast("Entrez le nom du producteur"); return; }
            if (n15 == 0 && n10 == 0) { toast("Entrez au moins une quantité"); return; }
            try {
                JSONArray d = brouillon();
                d.put(new JSONObject().put("producteur", name).put("n15", n15).put("n10", n10));
                sauver(d);
            } catch (Exception e) { toast("Erreur : " + e.getMessage()); return; }
            redraw();
        });

        final JSONArray d = brouillon();
        body.addView(StockUi.sectionTitle(this, "Brouillon : " + d.length() + " ligne(s)", StockUi.PURPLE));
        if (d.length() == 0) {
            body.addView(infoCard("Aucune ligne. Ajoutez les producteurs avec leur nombre de 5/15 et de 5/10, puis tapez Envoyer."));
        }
        for (int i = 0; i < d.length(); i++) {
            final int idx = i;
            JSONObject o = d.optJSONObject(i);
            if (o == null) continue;
            LinearLayout card = StockUi.card(this);
            card.addView(StockUi.tv(this, (i + 1) + ".  " + o.optString("producteur"), 16, StockUi.DARK, true));
            card.addView(StockUi.tv(this, "5/15 : " + o.optInt("n15") + "   •   5/10 : " + o.optInt("n10"), 14, StockUi.GRAY, false));
            LinearLayout r = new LinearLayout(this);
            r.addView(bouton("Modifier", StockUi.BLUE, v -> {
                pNom = o.optString("producteur"); pA = String.valueOf(o.optInt("n15")); pB = String.valueOf(o.optInt("n10"));
                sauver(sans(d, idx));
                redraw();
            }));
            r.addView(bouton("Retirer", StockUi.RED, v -> { sauver(sans(d, idx)); redraw(); }));
            card.addView(r);
            body.addView(card);
        }

        if (d.length() > 0) {
            Button envoi = StockUi.btn(this, "Envoyer à l'administrateur", StockUi.ORANGE);
            body.addView(envoi);
            envoi.setOnClickListener(v -> confirmerEnvoi(v, d));
        }

        body.addView(StockUi.sectionTitle(this, "Listes envoyées", StockUi.BLUE));
        afficherLots(false);
    }

    /** Étape 2 : la fenêtre avec la liste complète, puis Modifier ou Valider. */
    private void confirmerEnvoi(final View bouton, final JSONArray d) {
        StringBuilder sb = new StringBuilder();
        int t15 = 0, t10 = 0;
        for (int i = 0; i < d.length(); i++) {
            JSONObject o = d.optJSONObject(i);
            if (o == null) continue;
            sb.append(i + 1).append(". ").append(o.optString("producteur"))
              .append("  —  5/15 : ").append(o.optInt("n15")).append("  |  5/10 : ").append(o.optInt("n10")).append("\n");
            t15 += o.optInt("n15"); t10 += o.optInt("n10");
        }
        sb.append("\nTotal :  5/15 = ").append(t15).append("   |   5/10 = ").append(t10);
        ScrollView sv = new ScrollView(this);
        TextView tv = StockUi.tv(this, sb.toString(), 15, StockUi.DARK, false);
        int pd = StockUi.dp(this, 20);
        tv.setPadding(pd, pd / 2, pd, 0);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("Liste complète (" + d.length() + " ligne(s))")
                .setView(sv)
                .setNegativeButton("Modifier", null)
                .setPositiveButton("Valider", (dlg, w) -> envoyer(bouton, () -> {
                    String err = db.secretariatEnvoyer(d);
                    if (err == null) sauver(new JSONArray());   // envoyé : le brouillon est vidé
                    return err;
                }, "✔ Liste envoyée à l'administrateur le " + StockDb.now()))
                .show();
    }

    // ------------------------------------------------------------------ administrateur

    private void renderAdmin() {
        body.addView(StockUi.sectionTitle(this, "Ajouter une ligne", StockUi.GREEN));
        LinearLayout f = StockUi.card(this);
        final EditText m = StockUi.et(this, "Massier", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText nm = StockUi.et(this, "Nom du producteur", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText a = StockUi.num(this, "Nombre de 5/15  (" + StockDb.PRIX_515 + " F l'unité)");
        final EditText c = StockUi.num(this, "Nombre de 5/10  (" + StockDb.PRIX_510 + " F l'unité)");
        List<String> ms = db.massiers();
        if (!ms.isEmpty()) f.addView(StockUi.tv(this, "Massiers enregistrés : " + android.text.TextUtils.join(", ", ms), 12, StockUi.GRAY, false));
        Button add = StockUi.btn(this, "Ajouter la ligne", StockUi.GREEN);
        f.addView(m); f.addView(nm); f.addView(a); f.addView(c); f.addView(add);
        body.addView(f);
        add.setOnClickListener(v -> {
            final String mas = m.getText().toString().trim().replaceAll("\\s+", " ");
            final String name = nm.getText().toString().trim().replaceAll("\\s+", " ");
            final int n15 = parse(a), n10 = parse(c);
            if (mas.isEmpty()) { toast("Entrez le massier"); return; }
            if (name.isEmpty()) { toast("Entrez le nom du producteur"); return; }
            if (n15 == 0 && n10 == 0) { toast("Entrez au moins une quantité"); return; }
            envoyer(v, () -> db.secretariatAjouter(mas, name, n15, n10),
                    "✔ Ligne ajoutée le " + StockDb.now() + "\n" + name + " — 5/15 : " + n15 + "  |  5/10 : " + n10);
        });

        body.addView(StockUi.sectionTitle(this, "Listes des secrétaires", StockUi.BLUE));
        afficherLots(true);
    }

    // ------------------------------------------------------------------ listes envoyées (les deux rôles)

    private void afficherLots(boolean admin) {
        List<List<String[]>> lots = db.lots();
        if (lots.isEmpty()) { body.addView(infoCard("Aucune liste pour le moment.")); return; }
        List<List<String[]>> anciens = new ArrayList<>();
        int recents = 0;
        for (List<String[]> lot : lots) {
            if (StockDb.isRecent(lot.get(0)[6])) { body.addView(carteLot(lot, admin)); recents++; }
            else anciens.add(lot);
        }
        if (anciens.isEmpty()) return;
        if (recents == 0) body.addView(infoCard("Rien de nouveau ces 2 derniers jours."));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        for (List<String[]> lot : anciens) box.addView(carteLot(lot, admin));
        foldButton(body, "Voir les anciens points (" + anciens.size() + ")", "Masquer les anciens points", box);
    }

    private View carteLot(List<String[]> lot, boolean admin) {
        String[] t = lot.get(0);
        LinearLayout c = StockUi.card(this);
        c.addView(StockUi.tv(this, "Liste du " + t[6], 15, StockUi.DARK, true));
        c.addView(StockUi.tv(this, "Massier : " + t[2] + "  •  " + t[7], 12, StockUi.GRAY, false));
        int s15 = 0, s10 = 0;
        for (final String[] e : lot) {
            int n15 = Integer.parseInt(e[4]), n10 = Integer.parseInt(e[5]);
            s15 += n15; s10 += n10;
            LinearLayout l = new LinearLayout(this);
            l.setOrientation(LinearLayout.VERTICAL);
            l.setPadding(0, StockUi.dp(this, 12), 0, 0);
            l.addView(StockUi.tv(this, e[3], 16, StockUi.DARK, true));
            if (admin) {
                l.addView(StockUi.tv(this, "5/15 : " + n15 + " × " + StockDb.PRIX_515 + " = " + StockUi.money(n15 * StockDb.PRIX_515), 13, StockUi.GRAY, false));
                l.addView(StockUi.tv(this, "5/10 : " + n10 + " × " + StockDb.PRIX_510 + " = " + StockUi.money(n10 * StockDb.PRIX_510), 13, StockUi.GRAY, false));
                l.addView(StockUi.tv(this, "Prix de production : " + StockUi.money(n15 * StockDb.PRIX_515 + n10 * StockDb.PRIX_510), 14, StockUi.GREEN, true));
                LinearLayout r = new LinearLayout(this);
                r.addView(bouton("Modifier", StockUi.BLUE, v -> modifier(v, e)));
                r.addView(bouton("Supprimer", StockUi.RED, v -> supprimer(v, e)));
                l.addView(r);
            } else {
                l.addView(StockUi.tv(this, "5/15 : " + n15 + "   •   5/10 : " + n10, 13, StockUi.GRAY, false));
            }
            c.addView(l);
        }
        TextView tot = StockUi.tv(this, admin
                ? "Total à payer : " + StockUi.money(s15 * StockDb.PRIX_515 + s10 * StockDb.PRIX_510) + "   (5/15 : " + s15 + "  •  5/10 : " + s10 + ")"
                : "Total envoyé :  5/15 = " + s15 + "   |   5/10 = " + s10, 14, StockUi.BLUE, true);
        tot.setPadding(0, StockUi.dp(this, 12), 0, 0);
        c.addView(tot);
        return c;
    }

    private void modifier(final View src, final String[] e) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pd = StockUi.dp(this, 20);
        box.setPadding(pd, pd / 2, pd, 0);
        final EditText nm = StockUi.et(this, "Nom du producteur", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText a = StockUi.num(this, "Nombre de 5/15");
        final EditText c = StockUi.num(this, "Nombre de 5/10");
        nm.setText(e[3]); a.setText(e[4]); c.setText(e[5]);
        box.addView(nm); box.addView(a); box.addView(c);
        new AlertDialog.Builder(this)
                .setTitle("Modifier la ligne")
                .setView(box)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", (dlg, w) -> {
                    final String name = nm.getText().toString().trim().replaceAll("\\s+", " ");
                    final int n15 = parse(a), n10 = parse(c);
                    if (name.isEmpty()) { toast("Entrez le nom du producteur"); return; }
                    if (n15 == 0 && n10 == 0) { toast("Entrez au moins une quantité"); return; }
                    final long id = Long.parseLong(e[0]);
                    envoyer(src, () -> db.secretariatModifier(id, name, n15, n10),
                            "✔ Ligne modifiée le " + StockDb.now() + "\n" + name + " — 5/15 : " + n15 + "  |  5/10 : " + n10);
                })
                .show();
    }

    private void supprimer(final View src, final String[] e) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer cette ligne ?")
                .setMessage(e[3] + " — 5/15 : " + e[4] + "  |  5/10 : " + e[5])
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer", (dlg, w) -> {
                    final long id = Long.parseLong(e[0]);
                    envoyer(src, () -> db.secretariatSupprimer(id), "✔ Ligne supprimée le " + StockDb.now());
                })
                .show();
    }
}
