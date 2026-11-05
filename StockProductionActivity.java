package bj.radio;

import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import java.util.List;

public class StockProductionActivity extends StockBaseActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        init("Production", StockSession.admin() ? "Ajouter ce que chaque massier a produit" : "Productions de la semaine", true, false);
    }

    @Override protected void render() {
        showFlash();
        if (StockSession.admin()) {
            body.addView(StockUi.sectionTitle(this, "Nouvelle production", StockUi.GREEN));
            LinearLayout f = StockUi.card(this);
            final List<String> liste = db.massiersListe();
            final Spinner m = StockUi.spinner(this, liste, null);
            final EditText a = StockUi.num(this, "Nombre de 5/15  (" + StockDb.PRIX_515 + " F l'unité)");
            final EditText c = StockUi.num(this, "Nombre de 5/10  (" + StockDb.PRIX_510 + " F l'unité)");
            f.addView(StockUi.tv(this, liste.isEmpty()
                    ? "Aucun massier : ajoutez-en d'abord dans Comptes."
                    : "Choisissez le massier dans la liste (ajoutés dans Comptes).", 12, StockUi.GRAY, false));
            f.addView(m); f.addView(a); f.addView(c);
            Button s = StockUi.btn(this, "Enregistrer la production", StockUi.GREEN);
            f.addView(s);
            body.addView(f);
            s.setOnClickListener(v -> {
                if (liste.isEmpty()) { toast("Ajoutez d'abord un massier dans Comptes"); return; }
                String name = String.valueOf(m.getSelectedItem());
                int n15 = parse(a), n10 = parse(c);
                if (n15 == 0 && n10 == 0) { toast("Entrez au moins une quantité"); return; }
                envoyer(v, () -> db.addEntry(StockDb.PROD, name, n15, n10),
                        "✔ Enregistré le " + StockDb.now() + "\n" + name + " — 5/15 : " + n15 + "  |  5/10 : " + n10
                        + "\nTotal : " + StockUi.money(n15 * StockDb.PRIX_515 + n10 * StockDb.PRIX_510));
            });
        }
        // Uniquement les productions, regroupées par semaine (du lundi au dimanche)
        body.addView(StockUi.sectionTitle(this, "Productions par semaine", StockUi.GREEN));
        List<String[]> l = db.history(StockDb.PROD);
        if (l.isEmpty()) { body.addView(infoCard("Aucune production pour le moment.")); return; }
        java.util.TreeMap<Long, List<String[]>> semaines = new java.util.TreeMap<>(java.util.Collections.<Long>reverseOrder());
        for (String[] e : l) {
            long w = StockDb.weekStart(StockDb.parse(e[4]));
            List<String[]> g = semaines.get(w);
            if (g == null) { g = new java.util.ArrayList<>(); semaines.put(w, g); }
            g.add(e);
        }
        long now = System.currentTimeMillis();
        long cur = StockDb.weekStart(now);
        // Lundi et mardi, la semaine passée reste visible ; dès mercredi elle passe dans « Voir les anciennes semaines »
        long garde = (now - cur < 2L * 24 * 3600 * 1000) ? StockDb.weekStart(cur - 1) : cur;
        if (semaines.firstKey() < garde) body.addView(infoCard("Pas de production cette semaine."));
        LinearLayout anciennes = new LinearLayout(this);
        anciennes.setOrientation(LinearLayout.VERTICAL);
        int nbAnciennes = 0;
        for (java.util.Map.Entry<Long, List<String[]>> w : semaines.entrySet()) {
            boolean visible = w.getKey() >= garde;
            if (!visible) nbAnciennes++;
            semaineBloc(visible ? body : anciennes, w.getKey(), w.getValue());
        }
        if (nbAnciennes > 0) foldButton(body, "Voir les anciennes semaines (" + nbAnciennes + ")", "Masquer les anciennes semaines", anciennes);
    }

    /** Une semaine : totaux 5/15 et 5/10, montant dû aux producteurs, puis les productions. */
    private void semaineBloc(LinearLayout cible, long debut, List<String[]> lignes) {
        int a = 0, b = 0;
        for (String[] e : lignes) { a += Integer.parseInt(e[2]); b += Integer.parseInt(e[3]); }
        LinearLayout t = StockUi.card(this);
        t.addView(StockUi.tv(this, StockDb.weekLabel(debut), 16, StockUi.DARK, true));
        t.addView(StockUi.tv(this, "5/15 : " + a + "   •   5/10 : " + b, 14, StockUi.GREEN, true));
        t.addView(StockUi.tv(this, "À payer aux producteurs : " + StockUi.money(a * StockDb.PRIX_515 + b * StockDb.PRIX_510), 13, StockUi.GRAY, false));
        cible.addView(t);
        for (String[] e : lignes) cible.addView(entryCard(e, true));
    }
}
