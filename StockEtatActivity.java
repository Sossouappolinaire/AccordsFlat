package bj.radio;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public class StockEtatActivity extends StockBaseActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        init("Stock", "Ce qui reste sur le terrain, par massier", true, false);
    }

    private LinearLayout col(String label, int valeur, int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(StockUi.tv(this, label, 13, StockUi.GRAY, true));
        c.addView(StockUi.tv(this, String.valueOf(valeur), 34, valeur == 0 ? StockUi.RED : color, true));
        return c;
    }

    /** Administrateur : fixe le stock restant sur le terrain d'un massier. */
    private void corriger(final View src, final String massier, int st15, int st10) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pd = StockUi.dp(this, 20);
        box.setPadding(pd, pd / 2, pd, 0);
        final android.widget.EditText a = StockUi.num(this, "Stock 5/15 sur le terrain");
        final android.widget.EditText c = StockUi.num(this, "Stock 5/10 sur le terrain");
        a.setText(String.valueOf(st15)); c.setText(String.valueOf(st10));
        box.addView(StockUi.tv(this, "Écrivez ce qu'il reste réellement chez " + massier + ".", 13, StockUi.GRAY, false));
        box.addView(a); box.addView(c);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Corriger le stock")
                .setView(box)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", (dlg, w) -> {
                    final int n15 = parse(a), n10 = parse(c);
                    envoyer(src, () -> db.stockFixer(massier, n15, n10),
                            "✔ Stock de " + massier + " enregistré le " + StockDb.now() + "\n5/15 : " + n15 + "  |  5/10 : " + n10);
                })
                .show();
    }

    @Override protected void render() {
        showFlash();
        body.addView(StockUi.sectionTitle(this, "Stock par massier", StockUi.ORANGE));
        List<String> ms = StockSession.admin() ? db.massiersListe() : db.massiers();
        if (ms.isEmpty()) body.addView(infoCard("Aucun massier pour le moment."));
        for (final String m : ms) {
            final int[] pp = db.totals(StockDb.PROD, m), ss = db.totals(StockDb.SALE, m);
            LinearLayout c = StockUi.card(this);
            c.addView(StockUi.tv(this, m, 18, StockUi.DARK, true));
            LinearLayout r = new LinearLayout(this);
            r.setPadding(0, StockUi.dp(this, 8), 0, 0);
            r.addView(col("Stock 5/15", pp[0] - ss[0], StockUi.BLUE));
            r.addView(col("Stock 5/10", pp[1] - ss[1], StockUi.TEAL));
            c.addView(r);
            if (StockSession.admin()) {
                TextView corr = StockUi.pill(this, "✏ Corriger le stock", StockUi.BLUE);
                corr.setLayoutParams(StockUi.lp(this, -2, -2, 0, 8, 0, 0));
                corr.setPadding(StockUi.dp(this, 16), StockUi.dp(this, 8), StockUi.dp(this, 16), StockUi.dp(this, 8));
                corr.setOnClickListener(v -> corriger(v, m, pp[0] - ss[0], pp[1] - ss[1]));
                c.addView(corr);
            }
            body.addView(c);
        }
    }
}
