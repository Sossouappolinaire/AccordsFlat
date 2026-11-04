package bj.radio;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

public class StockHomeActivity extends StockBaseActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean adm = StockSession.admin();
        String nom = StockSession.nom == null ? "" : StockSession.nom.trim();
        if (adm) {
            // Administrateur : ‹ Retour ramène au panneau d'administration
            init("Gestion de la carrière", "Administrateur" + (nom.isEmpty() ? "" : "  •  " + nom), true, false);
        } else {
            // Propriétaire : son nom et prénom en haut + « Retour hors carrière »
            String sous = StockSession.secretaire() ? "Secrétaire  •  " + StockSession.massier : "Propriétaire  •  " + StockSession.phone;
            init(nom.isEmpty() ? "Accueil" : nom, sous, false, true);
        }
    }

    private void go(Class<?> c) { startActivity(new Intent(this, c)); }

    private LinearLayout row(View a, View b) {
        LinearLayout r = new LinearLayout(this);
        r.addView(a); r.addView(b);
        return r;
    }

    private View spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1));
        return v;
    }

    @Override protected void render() {
        int[] p = db.totals(StockDb.PROD, null), s = db.totals(StockDb.SALE, null);
        LinearLayout stats = new LinearLayout(this);
        stats.addView(StockUi.statBox(this, "Stock 5/15", String.valueOf(p[0] - s[0]), StockUi.BLUE));
        stats.addView(StockUi.statBox(this, "Stock 5/10", String.valueOf(p[1] - s[1]), StockUi.TEAL));
        long[] wk = db.weeklySales().get(0);
        stats.addView(StockUi.statBox(this, "Vendu semaine", (wk[1] + wk[2]) + " vendus", StockUi.GREEN));
        body.addView(stats);

        body.addView(StockUi.sectionTitle(this, "Menu principal", StockUi.INDIGO));
        View stock = StockUi.tile(this, "📦", "Stock", "5/15 et 5/10 sur le terrain", StockUi.BLUE, v -> go(StockEtatActivity.class));
        View hist = StockUi.tile(this, "🕒", "Historique", "Tout, avec date et heure", StockUi.PURPLE, v -> go(StockHistoryActivity.class));
        View bilan = StockUi.tile(this, "📊", "Bilan et prix", "Montants en francs", StockUi.TEAL, v -> go(StockBilanActivity.class));

        View secr = StockUi.tile(this, "📝", "Secrétariat",
                StockSession.admin() ? "Listes des secrétaires et prix" : "Ajouter et envoyer une liste", StockUi.PINK, v -> go(StockSecretariatActivity.class));

        if (StockSession.secretaire()) {
            body.addView(row(secr, stock));
        } else if (StockSession.admin()) {
            View prod = StockUi.tile(this, "🧱", "Production", "Ajouter par massier", StockUi.GREEN, v -> go(StockProductionActivity.class));
            View sales = StockUi.tile(this, "💰", "Ventes", "Ajoutées par le propriétaire", StockUi.ORANGE, v -> go(StockSalesActivity.class));
            View acc = StockUi.tile(this, "👤", "Comptes", "Créer le propriétaire", StockUi.PINK, v -> go(StockAccountsActivity.class));
            body.addView(row(prod, sales));
            body.addView(row(stock, bilan));
            body.addView(row(hist, acc));
            body.addView(row(secr, spacer()));
        } else {
            View sales = StockUi.tile(this, "💰", "Vendre", "Enregistrer une vente", StockUi.ORANGE, v -> go(StockSalesActivity.class));
            View prod = StockUi.tile(this, "🧱", "Productions", "Ajouts de l'administrateur", StockUi.GREEN, v -> go(StockProductionActivity.class));
            body.addView(row(sales, stock));
            body.addView(row(prod, bilan));
            body.addView(row(hist, spacer()));
        }
    }
}
