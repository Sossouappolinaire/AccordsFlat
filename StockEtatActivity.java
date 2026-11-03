package bj.radio;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import java.util.List;

public class StockEtatActivity extends StockBaseActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        init("Stock", "Produit + ancien stock − vendu", true, false);
    }

    private View big(String label, int prod, int sold, int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(StockUi.gradient(this, color, 22));
        c.setElevation(StockUi.dp(this, 5));
        c.setPadding(StockUi.dp(this, 20), StockUi.dp(this, 18), StockUi.dp(this, 20), StockUi.dp(this, 18));
        c.setLayoutParams(StockUi.lp(this, -1, -2, 0, 6, 0, 6));
        c.addView(StockUi.tv(this, "Stock " + label, 14, 0xCCFFFFFF, true));
        c.addView(StockUi.tv(this, String.valueOf(prod - sold), 40, Color.WHITE, true));
        c.addView(StockUi.tv(this, "Produit : " + prod + "   •   Vendu : " + sold, 13, 0xDDFFFFFF, false));
        c.addView(StockUi.progress(this, prod == 0 ? 0 : (float) (prod - sold) / prod, Color.WHITE, 0x55FFFFFF));
        return c;
    }

    private View line(String label, int prod, int sold, int color) {
        int st = prod - sold;
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(0, StockUi.dp(this, 8), 0, 0);
        LinearLayout r = new LinearLayout(this);
        android.widget.TextView l = StockUi.tv(this, label, 14, StockUi.DARK, true);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(l);
        r.addView(StockUi.tv(this, "Stock : " + st, 15, st == 0 ? StockUi.RED : color, true));
        c.addView(r);
        c.addView(StockUi.tv(this, "Produit : " + prod + "  •  Vendu : " + sold, 12, StockUi.GRAY, false));
        c.addView(StockUi.progress(this, prod == 0 ? 0 : (float) st / prod, color, 0xFFE5E7EB));
        return c;
    }

    @Override protected void render() {
        int[] p = db.totals(StockDb.PROD, null), s = db.totals(StockDb.SALE, null);
        body.addView(StockUi.sectionTitle(this, "Stock total sur le terrain", StockUi.BLUE));
        body.addView(big("5/15", p[0], s[0], StockUi.BLUE));
        body.addView(big("5/10", p[1], s[1], StockUi.TEAL));

        body.addView(StockUi.sectionTitle(this, "Stock par massier", StockUi.ORANGE));
        List<String> ms = db.massiers();
        if (ms.isEmpty()) body.addView(infoCard("Aucun massier pour le moment."));
        for (String m : ms) {
            int[] pp = db.totals(StockDb.PROD, m), ss = db.totals(StockDb.SALE, m);
            LinearLayout c = StockUi.card(this);
            c.addView(StockUi.tv(this, m, 18, StockUi.DARK, true));
            c.addView(line("5/15", pp[0], ss[0], StockUi.BLUE));
            c.addView(line("5/10", pp[1], ss[1], StockUi.TEAL));
            android.widget.TextView mt = StockUi.tv(this, "Ajouts et ventes (les plus récents en haut)", 13, StockUi.GRAY, true);
            mt.setPadding(0, StockUi.dp(this, 14), 0, StockUi.dp(this, 4));
            c.addView(mt);
            addEntries(c, db.historyBy(m), true);
            body.addView(c);
        }
    }
}
