package bj.radio;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;
import java.util.List;

public class StockHistoryActivity extends StockBaseActivity {
    private int filter = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        init("Historique", "Chaque ajout avec sa date et son heure", true, false);
    }

    private TextView chip(String text, final int idx, int color) {
        boolean on = filter == idx;
        TextView t = StockUi.tv(this, text, 13, on ? Color.WHITE : StockUi.GRAY, true);
        t.setGravity(android.view.Gravity.CENTER);
        t.setBackground(StockUi.shape(this, on ? color : Color.WHITE, 20));
        t.setElevation(StockUi.dp(this, on ? 4 : 1));
        t.setLayoutParams(StockUi.lp(this, 0, StockUi.dp(this, 40), 4, 4, 4, 4));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1;
        t.setOnClickListener(v -> { filter = idx; redraw(); });
        return t;
    }

    @Override protected void render() {
        LinearLayout chips = new LinearLayout(this);
        chips.addView(chip("Tout", 0, StockUi.INDIGO));
        chips.addView(chip("Productions", 1, StockUi.GREEN));
        chips.addView(chip("Ventes", 2, StockUi.ORANGE));
        body.addView(chips);
        List<String[]> l = db.history(filter == 1 ? StockDb.PROD : filter == 2 ? StockDb.SALE : null);
        body.addView(StockUi.sectionTitle(this, l.size() + " enregistrement(s)", StockUi.PURPLE));
        if (l.isEmpty()) body.addView(infoCard("Rien à afficher pour le moment."));
        addEntries(body, l, false);
    }
}
