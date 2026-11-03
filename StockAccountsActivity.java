package bj.radio;

import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import java.util.List;

public class StockAccountsActivity extends StockBaseActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        init("Comptes", "Créer et voir les comptes propriétaire", true, false);
    }

    @Override protected void render() {
        showFlash();
        body.addView(StockUi.sectionTitle(this, "Nouveau compte propriétaire", StockUi.PINK));
        LinearLayout f = StockUi.card(this);
        final EditText nm = StockUi.et(this, "Nom et prénom du propriétaire", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText ph = StockUi.et(this, "Numéro de téléphone du propriétaire", InputType.TYPE_CLASS_PHONE);
        final EditText pn = StockUi.et(this, "Code PIN (4 chiffres)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        Button s = StockUi.btn(this, "Créer le compte", StockUi.PINK);
        f.addView(nm); f.addView(ph); f.addView(pn); f.addView(s);
        body.addView(f);
        s.setOnClickListener(v -> {
            final String n = nm.getText().toString().trim().replaceAll("\\s+", " ");
            final String a = ph.getText().toString().replace(" ", "").trim();
            final String p = pn.getText().toString().trim();
            if (n.length() < 2) { toast("Entrez le nom et prénom du propriétaire"); return; }
            if (!a.matches("\\+?[0-9]{8,15}") || p.length() != 4) { toast("Téléphone ou PIN (4 chiffres) invalide"); return; }
            envoyer(v, () -> db.createOwner(n, a, p),
                    "✔ Compte créé le " + StockDb.now() + "\nPropriétaire : " + n + "\nNuméro : " + a);
        });

        body.addView(StockUi.sectionTitle(this, "Comptes propriétaire", StockUi.PURPLE));
        List<String[]> l = db.owners();
        if (l.isEmpty()) body.addView(infoCard("Aucun compte propriétaire pour le moment."));
        for (String[] o : l) {
            LinearLayout c = StockUi.card(this);
            LinearLayout r = new LinearLayout(this);
            TextView n = StockUi.tv(this, o[1], 17, StockUi.DARK, true);
            n.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(n); r.addView(StockUi.pill(this, "PROPRIÉTAIRE", StockUi.PINK));
            c.addView(r);
            c.addView(StockUi.tv(this, "Numéro : " + o[0], 14, StockUi.GRAY, false));
            c.addView(StockUi.tv(this, "Créé le " + o[2], 12, StockUi.GRAY, false));
            body.addView(c);
        }
    }
}
