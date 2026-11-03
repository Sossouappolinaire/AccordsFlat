package bj.radio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.*;

public abstract class StockBaseActivity extends Activity {
    protected LinearLayout body;
    protected StockDb db;
    protected String flash;

    protected abstract void render();

    private int seq = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = new StockDb(this);
        if (StockSession.role == null) {
            // pas connecté à Stock Massiers : retour à l'écran de connexion de l'appli
            startActivity(new Intent(this, AuthActivity.class));
            finish();
        }
    }

    @Override protected void onResume() { super.onResume(); if (body != null && StockSession.role != null) refresh(); }

    /** Recharge les données depuis la base (réseau, en arrière-plan) puis redessine l'écran. */
    protected void refresh() {
        final int mine = ++seq;
        if (!StockDb.pret()) {
            body.removeAllViews();
            body.addView(infoCard("Chargement…"));
        }
        new Thread(() -> {
            final String err = StockDb.charger();
            runOnUiThread(() -> {
                if (isFinishing() || mine != seq) return;
                if (err != null && !StockDb.pret()) { montrerErreur(err); return; }
                if (err != null) toast(err + " (données du dernier chargement)");
                redraw();
            });
        }).start();
    }

    /** Redessine l'écran avec les données déjà chargées (sans réseau). */
    protected void redraw() {
        body.removeAllViews();
        render();
    }

    private void montrerErreur(String message) {
        body.removeAllViews();
        body.addView(infoCard(message));
        Button r = StockUi.btn(this, "Réessayer", StockUi.INDIGO);
        r.setOnClickListener(v -> refresh());
        body.addView(r);
    }

    protected interface Tache { String faire(); }

    /** Lance une écriture sur le serveur en arrière-plan ; en cas de succès, affiche la confirmation et recharge. */
    protected void envoyer(final View bouton, final Tache tache, final String confirmation) {
        bouton.setEnabled(false);
        new Thread(() -> {
            final String err = tache.faire();
            runOnUiThread(() -> {
                bouton.setEnabled(true);
                if (isFinishing()) return;
                if (err != null) { toast(err); return; }
                StockUi.pop(bouton);
                flash = confirmation;
                refresh();
            });
        }).start();
    }

    protected void init(String title, String sub, boolean back, boolean logout) {
        getWindow().setStatusBarColor(StockUi.INDIGO);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(StockUi.BG);

        LinearLayout h = StockUi.header(this);
        LinearLayout top = new LinearLayout(this);
        TextView left = StockUi.tv(this, back ? "‹  Retour" : "STOCK MASSIERS", 13, 0xCCFFFFFF, true);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        if (back) left.setOnClickListener(v -> finish());
        top.addView(left);
        if (logout) {
            // Propriétaire : retour au projet 1 (l'appli Kouamé Play Box), hors carrière
            TextView lo = StockUi.tv(this, "Retour hors carrière", 13, 0xFFFFCDD2, true);
            lo.setOnClickListener(v -> {
                StockSession.fermer();
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            });
            top.addView(lo);
        }
        h.addView(top);
        TextView t = StockUi.tv(this, title, 26, Color.WHITE, true);
        t.setPadding(0, StockUi.dp(this, 10), 0, 0);
        h.addView(t);
        h.addView(StockUi.tv(this, sub, 13, 0xBBFFFFFF, false));

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pd = StockUi.dp(this, 14);
        body.setPadding(pd, pd, pd, pd * 2);
        sv.addView(body);
        root.addView(h); root.addView(sv);
        setContentView(root);
    }

    protected void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    protected int parse(EditText e) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception x) { return 0; }
    }

    /** Bandeau vert de confirmation avec date et heure. */
    protected void showFlash() {
        if (flash == null) return;
        LinearLayout c = StockUi.card(this);
        c.setBackground(StockUi.shape(this, 0xFFDCFCE7, 18));
        c.addView(StockUi.tv(this, flash, 14, 0xFF166534, true));
        body.addView(c);
        StockUi.flash(c);
        flash = null;
    }

    protected View infoCard(String text) {
        LinearLayout c = StockUi.card(this);
        c.addView(StockUi.tv(this, text, 14, StockUi.GRAY, false));
        return c;
    }

    private LinearLayout col(String label, int qty, int price, int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(StockUi.tv(this, label, 12, StockUi.GRAY, true));
        c.addView(StockUi.tv(this, String.valueOf(qty), 22, color, true));
        c.addView(StockUi.tv(this, StockUi.money(price), 12, StockUi.GRAY, false));
        return c;
    }

    /** Bouton qui montre ou cache un bloc (anciens points, autres semaines). */
    protected void foldButton(LinearLayout target, final String closed, final String open, final LinearLayout box) {
        box.setVisibility(View.GONE);
        final Button b = StockUi.btn(this, closed, StockUi.INDIGO);
        b.setOnClickListener(v -> {
            boolean show = box.getVisibility() != View.VISIBLE;
            box.setVisibility(show ? View.VISIBLE : View.GONE);
            b.setText(show ? open : closed);
            StockUi.pop(v);
        });
        target.addView(b);
        target.addView(box);
    }

    /** Récents (moins de 2 jours) en haut ; les plus anciens derrière « Voir les anciens points ». */
    protected void addEntries(LinearLayout target, List<String[]> list, boolean compact) {
        List<String[]> old = new ArrayList<>();
        int recent = 0;
        for (String[] e : list) {
            if (StockDb.isRecent(e[4])) { target.addView(entryCard(e, compact)); recent++; }
            else old.add(e);
        }
        if (old.isEmpty()) return;
        if (recent == 0) {
            if (compact) target.addView(StockUi.tv(this, "Rien de nouveau ces 2 derniers jours.", 12, StockUi.GRAY, false));
            else target.addView(infoCard("Rien de nouveau ces 2 derniers jours."));
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        for (String[] e : old) box.addView(entryCard(e, compact));
        foldButton(target, "Voir les anciens points (" + old.size() + ")", "Masquer les anciens points", box);
    }

    /** Carte d'un enregistrement : massier, 5/15, 5/10, total, date et heure. */
    protected View entryCard(String[] e) { return entryCard(e, false); }

    protected View entryCard(String[] e, boolean compact) {
        boolean prod = StockDb.PROD.equals(e[0]);
        int color = prod ? StockUi.GREEN : StockUi.ORANGE;
        int n15 = Integer.parseInt(e[2]), n10 = Integer.parseInt(e[3]);
        LinearLayout c = StockUi.card(this);
        if (compact) { c.setBackground(StockUi.shape(this, 0xFFF3F4F6, 14)); c.setElevation(0); }
        LinearLayout top = new LinearLayout(this);
        TextView name = StockUi.tv(this, e[1], 17, StockUi.DARK, true);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(name);
        top.addView(StockUi.pill(this, prod ? "PRODUCTION" : "VENTE", color));
        c.addView(top);
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, StockUi.dp(this, 10), 0, StockUi.dp(this, 6));
        r.addView(col("5/15", n15, n15 * StockDb.PRIX_515, color));
        r.addView(col("5/10", n10, n10 * StockDb.PRIX_510, color));
        c.addView(r);
        c.addView(StockUi.tv(this, "Total : " + StockUi.money(n15 * StockDb.PRIX_515 + n10 * StockDb.PRIX_510), 15, color, true));
        TextView d = StockUi.tv(this, e[4] + "  •  " + e[5], 12, StockUi.GRAY, false);
        d.setPadding(0, StockUi.dp(this, 6), 0, 0);
        c.addView(d);
        return c;
    }
}
