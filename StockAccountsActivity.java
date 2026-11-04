package bj.radio;

import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import java.util.List;

public class StockAccountsActivity extends StockBaseActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        init("Comptes", "Créer et voir les comptes propriétaire et secrétaire", true, false);
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

        body.addView(StockUi.sectionTitle(this, "Nouveau compte secrétaire", StockUi.TEAL));
        LinearLayout g = StockUi.card(this);
        final EditText sn = StockUi.et(this, "Nom et prénom du secrétaire", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText sp = StockUi.et(this, "Numéro de téléphone du secrétaire", InputType.TYPE_CLASS_PHONE);
        final EditText sc = StockUi.et(this, "Code PIN (4 chiffres)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        final EditText sm = StockUi.et(this, "Massier dont il est le secrétaire", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        List<String> ms = db.massiers();
        if (!ms.isEmpty()) g.addView(StockUi.tv(this, "Massiers enregistrés : " + android.text.TextUtils.join(", ", ms), 12, StockUi.GRAY, false));
        Button sb = StockUi.btn(this, "Créer le compte secrétaire", StockUi.TEAL);
        g.addView(sn); g.addView(sp); g.addView(sc); g.addView(sm); g.addView(sb);
        body.addView(g);
        sb.setOnClickListener(v -> {
            final String n = sn.getText().toString().trim().replaceAll("\\s+", " ");
            final String a = sp.getText().toString().replace(" ", "").trim();
            final String p = sc.getText().toString().trim();
            final String m = sm.getText().toString().trim().replaceAll("\\s+", " ");
            if (n.length() < 2) { toast("Entrez le nom et prénom du secrétaire"); return; }
            if (!a.matches("\\+?[0-9]{8,15}") || p.length() != 4) { toast("Téléphone ou PIN (4 chiffres) invalide"); return; }
            if (m.isEmpty()) { toast("Entrez le massier dont il est le secrétaire"); return; }
            envoyer(v, () -> db.createSecretary(n, a, p, m),
                    "✔ Compte secrétaire créé le " + StockDb.now() + "\nSecrétaire : " + n + "\nMassier : " + m + "\nNuméro : " + a);
        });

        body.addView(StockUi.sectionTitle(this, "Comptes secrétaire", StockUi.TEAL));
        List<String[]> secs = db.secretaires();
        if (secs.isEmpty()) body.addView(infoCard("Aucun compte secrétaire pour le moment."));
        for (String[] o : secs) {
            LinearLayout c = StockUi.card(this);
            LinearLayout r = new LinearLayout(this);
            TextView n = StockUi.tv(this, o[1], 17, StockUi.DARK, true);
            n.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(n); r.addView(StockUi.pill(this, "SECRÉTAIRE", StockUi.TEAL));
            c.addView(r);
            c.addView(StockUi.tv(this, "Massier : " + o[2], 14, StockUi.DARK, true));
            c.addView(StockUi.tv(this, "Numéro : " + o[0], 14, StockUi.GRAY, false));
            c.addView(StockUi.tv(this, "Créé le " + o[3], 12, StockUi.GRAY, false));
            body.addView(c);
        }

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
