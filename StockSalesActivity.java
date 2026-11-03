package bj.radio;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.List;

public class StockSalesActivity extends StockBaseActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        init(StockSession.admin() ? "Ventes" : "Vendre", StockSession.admin() ? "Ventes ajoutées par le propriétaire" : "Ajouter le nombre de 5/15 et 5/10 vendus (pour calculer le stock)", true, false);
    }

    @Override protected void render() {
        showFlash();
        if (!StockSession.admin()) {
            body.addView(StockUi.sectionTitle(this, "Nouvelle vente", StockUi.ORANGE));
            LinearLayout f = StockUi.card(this);
            final List<String> ms = db.massiers();
            if (ms.isEmpty()) {
                f.addView(StockUi.tv(this, "Aucun massier pour le moment. L'administrateur doit d'abord enregistrer une production.", 14, StockUi.GRAY, false));
            } else {
                f.addView(StockUi.tv(this, "Massier", 12, StockUi.GRAY, true));
                final Spinner sp = new Spinner(this);
                sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ms));
                final TextView dispo = StockUi.tv(this, "", 13, StockUi.BLUE, true);
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        int[] st = db.stock(ms.get(pos));
                        dispo.setText("Disponible : 5/15 = " + st[0] + "  |  5/10 = " + st[1]);
                    }
                    public void onNothingSelected(AdapterView<?> p) {}
                });
                final EditText a = StockUi.num(this, "Nombre de 5/15 vendus");
                final EditText c = StockUi.num(this, "Nombre de 5/10 vendus");
                Button s = StockUi.btn(this, "Enregistrer la vente", StockUi.ORANGE);
                f.addView(sp); f.addView(dispo); f.addView(a); f.addView(c); f.addView(s);
                s.setOnClickListener(v -> {
                    String name = ms.get(sp.getSelectedItemPosition());
                    int n15 = parse(a), n10 = parse(c);
                    if (n15 == 0 && n10 == 0) { toast("Entrez au moins une quantité"); return; }
                    int[] st = db.stock(name);
                    if (n15 > st[0] || n10 > st[1]) {
                        toast("Stock insuffisant chez " + name + " : " + st[0] + " de 5/15 et " + st[1] + " de 5/10");
                        return;
                    }
                    envoyer(v, () -> db.addEntry(StockDb.SALE, name, n15, n10),
                            "✔ Vente enregistrée le " + StockDb.now() + "\n" + name + " — 5/15 : " + n15 + "  |  5/10 : " + n10);
                });
            }
            body.addView(f);
        }
        body.addView(StockUi.sectionTitle(this, "Ventes enregistrées", StockUi.ORANGE));
        List<String[]> l = db.history(StockDb.SALE);
        if (l.isEmpty()) body.addView(infoCard("Aucune vente pour le moment."));
        addEntries(body, l, false);
    }
}
