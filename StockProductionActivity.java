package bj.radio;

import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import java.util.List;

public class StockProductionActivity extends StockBaseActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        init("Production", StockSession.admin() ? "Ajouter ce que chaque massier a produit" : "Productions ajoutées par l'administrateur", true, false);
    }

    /** Ligne : type, produit, nombre vendu et stock restant. */
    private TextView ligne(String label, int prod, int vendu, int color) {
        TextView t = StockUi.tv(this, label + "  :  produit " + prod + "  •  vendu " + vendu + "  •  stock " + (prod - vendu), 14, color, true);
        t.setPadding(0, StockUi.dp(this, 8), 0, 0);
        return t;
    }

    @Override protected void render() {
        showFlash();
        if (StockSession.admin()) {
            body.addView(StockUi.sectionTitle(this, "Nouvelle production", StockUi.GREEN));
            LinearLayout f = StockUi.card(this);
            final EditText m = StockUi.et(this, "Nom du massier", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            final EditText a = StockUi.num(this, "Nombre de 5/15  (" + StockDb.PRIX_515 + " F l'unité)");
            final EditText c = StockUi.num(this, "Nombre de 5/10  (" + StockDb.PRIX_510 + " F l'unité)");
            List<String> ms = db.massiers();
            if (!ms.isEmpty()) f.addView(StockUi.tv(this, "Massiers enregistrés : " + android.text.TextUtils.join(", ", ms), 12, StockUi.GRAY, false));
            f.addView(m); f.addView(a); f.addView(c);
            Button s = StockUi.btn(this, "Enregistrer la production", StockUi.GREEN);
            f.addView(s);
            body.addView(f);
            s.setOnClickListener(v -> {
                String name = m.getText().toString().trim();
                int n15 = parse(a), n10 = parse(c);
                if (name.isEmpty()) { toast("Entrez le nom du massier"); return; }
                if (n15 == 0 && n10 == 0) { toast("Entrez au moins une quantité"); return; }
                envoyer(v, () -> db.addEntry(StockDb.PROD, name, n15, n10),
                        "✔ Enregistré le " + StockDb.now() + "\n" + name + " — 5/15 : " + n15 + "  |  5/10 : " + n10
                        + "\nTotal : " + StockUi.money(n15 * StockDb.PRIX_515 + n10 * StockDb.PRIX_510));
            });
        }
        // En haut : par massier, la production, sa date, le nombre vendu et ce qu'on doit aux producteurs
        List<String> suivi = db.massiers();
        if (!suivi.isEmpty()) {
            body.addView(StockUi.sectionTitle(this, "Production, date et nombre vendu", StockUi.BLUE));
            for (String nom : suivi) {
                int[] pp = db.totals(StockDb.PROD, nom), ss = db.totals(StockDb.SALE, nom);
                LinearLayout c = StockUi.card(this);
                c.addView(StockUi.tv(this, nom, 18, StockUi.DARK, true));
                c.addView(StockUi.tv(this, "Dernière production : " + db.lastProd(nom), 12, StockUi.GRAY, false));
                c.addView(ligne("5/15", pp[0], ss[0], StockUi.BLUE));
                c.addView(ligne("5/10", pp[1], ss[1], StockUi.TEAL));
                TextView du = StockUi.tv(this, "À payer aux producteurs : "
                        + StockUi.money(pp[0] * StockDb.PRIX_515 + pp[1] * StockDb.PRIX_510), 14, StockUi.GREEN, true);
                du.setPadding(0, StockUi.dp(this, 8), 0, 0);
                c.addView(du);
                body.addView(c);
            }
        }
        body.addView(StockUi.sectionTitle(this, "Productions enregistrées", StockUi.GREEN));
        List<String[]> l = db.history(StockDb.PROD);
        if (l.isEmpty()) body.addView(infoCard("Aucune production pour le moment."));
        addEntries(body, l, false);
    }
}
