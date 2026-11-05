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

        // Massiers : l'administrateur les ajoute ici AVANT de créer leur secrétaire
        body.addView(StockUi.sectionTitle(this, "Massiers", StockUi.GREEN));
        LinearLayout mf = StockUi.card(this);
        final EditText mn = StockUi.et(this, "Nom du massier à ajouter", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        Button ma = StockUi.btn(this, "Ajouter le massier", StockUi.GREEN);
        mf.addView(mn); mf.addView(ma);
        body.addView(mf);
        ma.setOnClickListener(v -> {
            final String n = mn.getText().toString().trim().replaceAll("\\s+", " ");
            if (n.isEmpty()) { toast("Entrez le nom du massier"); return; }
            envoyer(v, () -> db.massierAjouter(n), "✔ Massier ajouté le " + StockDb.now() + "\n" + n);
        });
        final List<String> listeMassiers = db.massiersListe();
        if (listeMassiers.isEmpty()) body.addView(infoCard("Aucun massier pour le moment. Ajoutez-en un ci-dessus."));
        for (final String nomM : listeMassiers) {
            LinearLayout mc = StockUi.card(this);
            LinearLayout mr = new LinearLayout(this);
            TextView mt = StockUi.tv(this, nomM, 16, StockUi.DARK, true);
            mt.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            mr.addView(mt);
            TextView sup = StockUi.pill(this, "Supprimer", StockUi.RED);
            sup.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                    .setTitle("Supprimer ce massier ?")
                    .setMessage(nomM + "\n\nPossible seulement s'il n'a ni stock, ni production, ni secrétaire.")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Supprimer", (d, w) -> envoyer(v, () -> db.massierSupprimer(nomM), "✔ Massier supprimé le " + StockDb.now()))
                    .show());
            mr.addView(sup);
            mc.addView(mr);
            body.addView(mc);
        }

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
        final Spinner sm = StockUi.spinner(this, listeMassiers, null);
        g.addView(StockUi.tv(this, listeMassiers.isEmpty()
                ? "Ajoutez d'abord un massier ci-dessus pour créer son secrétaire."
                : "Massier dont il est le secrétaire (choisissez dans la liste) :", 12, StockUi.GRAY, false));
        Button sb = StockUi.btn(this, "Créer le compte secrétaire", StockUi.TEAL);
        g.addView(sn); g.addView(sp); g.addView(sc); g.addView(sm); g.addView(sb);
        body.addView(g);
        sb.setOnClickListener(v -> {
            final String n = sn.getText().toString().trim().replaceAll("\\s+", " ");
            final String a = sp.getText().toString().replace(" ", "").trim();
            final String p = sc.getText().toString().trim();
            if (listeMassiers.isEmpty()) { toast("Ajoutez d'abord un massier"); return; }
            final String m = String.valueOf(sm.getSelectedItem());
            if (n.length() < 2) { toast("Entrez le nom et prénom du secrétaire"); return; }
            if (!a.matches("\\+?[0-9]{8,15}") || p.length() != 4) { toast("Téléphone ou PIN (4 chiffres) invalide"); return; }
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
