package bj.radio;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Accueil du module scolaire. */
public class ScolaireActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.fenetre(this);

        int marge = Ui.dp(this, 20);
        LinearLayout contenu = new LinearLayout(this);
        contenu.setOrientation(LinearLayout.VERTICAL);
        contenu.setPadding(marge, marge * 2, marge, marge);
        contenu.setBackground(Ui.fond());

        TextView titre = Ui.texte(this, "Fais tes exposés et exercices", 26, Ui.JAUNE, true);
        titre.setGravity(Gravity.CENTER);
        Ui.ajouter(contenu, titre, 4);

        TextView aide = Ui.texte(this,
                "Choisis une activité. Les exercices affichés viennent de la base pédagogique validée.",
                15, Ui.TXT2, false);
        aide.setGravity(Gravity.CENTER);
        Ui.ajouter(contenu, aide, 12);

        Button exposes = Ui.boutonPilule(this, "📝  Préparer un exposé", Ui.VIOLET, Ui.BLEU);
        exposes.setOnClickListener(v -> startActivity(new Intent(this, ExposeActivity.class)));
        Ui.ajouter(contenu, exposes, 32);

        Button exercices = Ui.boutonPilule(this, "✏️  Faire un exercice", Ui.VERT, 0xFF159C84);
        exercices.setOnClickListener(v -> startActivity(new Intent(this, ExercicesActivity.class)));
        Ui.ajouter(contenu, exercices, 16);

        TextView note = Ui.texte(this,
                "Les contenus scolaires seront publiés après vérification des programmes et des sources du Bénin.",
                13, Ui.TXT2, false);
        note.setGravity(Gravity.CENTER);
        Ui.ajouter(contenu, note, 28);

        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.addView(contenu);
        setContentView(page);
    }
}
