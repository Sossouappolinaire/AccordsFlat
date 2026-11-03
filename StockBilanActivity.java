package bj.radio;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.List;

public class StockBilanActivity extends StockBaseActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        init("Bilan et prix", "Prix producteurs : 5/15 = " + StockDb.PRIX_515 + " F   •   5/10 = " + StockDb.PRIX_510 + " F l'unité", true, false);
    }

    private void row(LinearLayout c, String l, String v, int color, boolean bold) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, StockUi.dp(this, 5), 0, StockUi.dp(this, 5));
        TextView a = StockUi.tv(this, l, 14, StockUi.GRAY, false);
        a.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(a); r.addView(StockUi.tv(this, v, bold ? 16 : 14, color, bold));
        c.addView(r);
    }

    private View weekCard(long[] w, boolean current) {
        int n15 = (int) w[1], n10 = (int) w[2];
        LinearLayout c = StockUi.card(this);
        LinearLayout top = new LinearLayout(this);
        TextView t = StockUi.tv(this, StockDb.weekLabel(w[0]), 14, StockUi.DARK, true);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(t);
        if (current) top.addView(StockUi.pill(this, "CETTE SEMAINE", StockUi.GREEN));
        c.addView(top);
        row(c, "5/15 vendus", String.valueOf(n15), StockUi.DARK, false);
        row(c, "5/10 vendus", String.valueOf(n10), StockUi.DARK, false);
        row(c, "Total vendu", String.valueOf(n15 + n10), StockUi.GREEN, true);
        return c;
    }

    @Override protected void render() {
        int[] p = db.totals(StockDb.PROD, null), s = db.totals(StockDb.SALE, null);

        List<long[]> weeks = db.weeklySales();
        body.addView(StockUi.sectionTitle(this, "Ventes de la semaine", StockUi.GREEN));
        body.addView(weekCard(weeks.get(0), true));
        if (weeks.size() > 1) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            for (int i = 1; i < weeks.size(); i++) box.addView(weekCard(weeks.get(i), false));
            foldButton(body, "Voir les autres semaines (" + (weeks.size() - 1) + ")", "Masquer les autres semaines", box);
        }

        body.addView(StockUi.sectionTitle(this, "À payer aux producteurs (production)", StockUi.BLUE));
        LinearLayout d = StockUi.card(this);
        row(d, "Produit 5/15 : " + p[0], StockUi.money(p[0] * StockDb.PRIX_515), StockUi.DARK, false);
        row(d, "Produit 5/10 : " + p[1], StockUi.money(p[1] * StockDb.PRIX_510), StockUi.DARK, false);
        row(d, "Total à payer aux producteurs", StockUi.money(p[0] * StockDb.PRIX_515 + p[1] * StockDb.PRIX_510), StockUi.BLUE, true);
        body.addView(d);

        body.addView(StockUi.sectionTitle(this, "Stock restant", StockUi.TEAL));
        LinearLayout e = StockUi.card(this);
        row(e, "Stock 5/15", String.valueOf(p[0] - s[0]), StockUi.DARK, false);
        row(e, "Stock 5/10", String.valueOf(p[1] - s[1]), StockUi.DARK, false);
        body.addView(e);

        body.addView(StockUi.sectionTitle(this, "Nombre vendu par massier", StockUi.ORANGE));
        List<String> ms = db.massiers();
        if (ms.isEmpty()) body.addView(infoCard("Aucun massier pour le moment."));
        LinearLayout f = StockUi.card(this);
        for (String m : ms) {
            int[] ss = db.totals(StockDb.SALE, m);
            row(f, m + " (5/15 : " + ss[0] + "  •  5/10 : " + ss[1] + ")", String.valueOf(ss[0] + ss[1]), StockUi.ORANGE, true);
        }
        if (!ms.isEmpty()) body.addView(f);
    }
}
