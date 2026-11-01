package bj.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Button;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Choix du niveau et de la matière, puis lecture des exercices publiés en base. */
public class ExercicesActivity extends Activity {

    private Spinner choixNiveau;
    private Spinner choixMatiere;
    private LinearLayout listeExercices;
    private TextView etat;
    private final List<JSONObject> matieresChargees = new ArrayList<>();
    private String niveauCourant;
    private String matiereCourante = "";
    private int numeroRequeteExercices = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.fenetre(this);

        int marge = Ui.dp(this, 18);
        LinearLayout contenu = new LinearLayout(this);
        contenu.setOrientation(LinearLayout.VERTICAL);
        contenu.setPadding(marge, marge * 2, marge, marge);
        contenu.setBackground(Ui.fond());

        TextView titre = Ui.texte(this, "Exercices scolaires", 26, Ui.JAUNE, true);
        Ui.ajouter(contenu, titre, 4);

        TextView labelNiveau = Ui.texte(this, "1. Choisis ta classe", 15, Ui.TXT, true);
        Ui.ajouter(contenu, labelNiveau, 20);
        choixNiveau = new Spinner(this);
        Ui.styleSpinner(choixNiveau);
        List<String> niveaux = new ArrayList<>();
        for (String nom : ScolaireRepository.NOMS_NIVEAUX) niveaux.add(nom);
        choixNiveau.setAdapter(Ui.adapterSpinner(this, niveaux));
        Ui.ajouter(contenu, choixNiveau, 8);

        TextView labelMatiere = Ui.texte(this, "2. Choisis la matière", 15, Ui.TXT, true);
        Ui.ajouter(contenu, labelMatiere, 20);
        choixMatiere = new Spinner(this);
        Ui.styleSpinner(choixMatiere);
        List<String> attente = new ArrayList<>();
        attente.add("Sélectionne d’abord une classe");
        choixMatiere.setAdapter(Ui.adapterSpinner(this, attente));
        Ui.ajouter(contenu, choixMatiere, 8);

        etat = Ui.texte(this, "Les exercices publiés apparaîtront ici.", 14, Ui.TXT2, false);
        Ui.ajouter(contenu, etat, 16);

        listeExercices = new LinearLayout(this);
        listeExercices.setOrientation(LinearLayout.VERTICAL);
        Ui.ajouter(contenu, listeExercices, 8);

        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.addView(contenu);
        setContentView(page);

        choixNiveau.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                niveauCourant = ScolaireRepository.codeNiveau(position);
                chargerMatieres(niveauCourant);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        choixMatiere.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && position <= matieresChargees.size()) {
                    JSONObject matiere = matieresChargees.get(position - 1);
                    String code = matiere.optString("code_matiere", "");
                    if (!code.isEmpty()) chargerExercices(niveauCourant, code);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void chargerMatieres(String niveau) {
        etat.setText("Chargement des matières…");
        listeExercices.removeAllViews();
        matieresChargees.clear();
        matiereCourante = "";
        numeroRequeteExercices++;
        List<String> attente = new ArrayList<>();
        attente.add("Chargement…");
        choixMatiere.setAdapter(Ui.adapterSpinner(this, attente));

        new Thread(() -> {
            JSONArray donnees = ScolaireRepository.listerMatieres(niveau);
            runOnUiThread(() -> {
                if (!niveau.equals(niveauCourant)) return;
                matieresChargees.clear();
                List<String> noms = new ArrayList<>();
                noms.add("Choisir une matière");

                if (donnees != null) {
                    for (int i = 0; i < donnees.length(); i++) {
                        JSONObject ligne = donnees.optJSONObject(i);
                        JSONObject matiere = ligne == null ? null : ligne.optJSONObject("matiere");
                        if (ligne == null || matiere == null) continue;

                        JSONObject entree = new JSONObject();
                        try {
                            entree.put("code_matiere", ligne.optString("code_matiere", ""));
                            entree.put("nom", matiere.optString("nom", "Matière"));
                            entree.put("statut_couverture", ligne.optString("statut_couverture", "a_documenter"));
                        } catch (Exception ignored) {
                            continue;
                        }
                        matieresChargees.add(entree);
                        String nom = entree.optString("nom", "Matière");
                        String couverture = entree.optString("statut_couverture", "a_documenter");
                        if ("a_documenter".equals(couverture)) {
                            nom += " · programme à documenter";
                        } else if ("source_trouvee".equals(couverture)) {
                            nom += " · source en vérification";
                        }
                        noms.add(nom);
                    }
                }

                choixMatiere.setAdapter(Ui.adapterSpinner(this, noms));
                if (donnees == null) {
                    etat.setText("Impossible de lire les matières. Vérifie la connexion et la migration Supabase.");
                } else if (matieresChargees.isEmpty()) {
                    etat.setText("Aucune matière configurée pour cette classe dans la base.");
                } else {
                    etat.setText("Les contenus non encore vérifiés sont signalés dans la liste.");
                }
            });
        }).start();
    }

    private void chargerExercices(String niveau, String matiere) {
        matiereCourante = matiere;
        int requete = ++numeroRequeteExercices;
        etat.setText("Chargement des exercices publiés…");
        listeExercices.removeAllViews();

        new Thread(() -> {
            JSONArray exercices = ScolaireRepository.listerExercices(niveau, matiere);
            runOnUiThread(() -> {
                if (requete != numeroRequeteExercices
                        || !niveau.equals(niveauCourant)
                        || !matiere.equals(matiereCourante)) return;
                afficherExercices(exercices);
            });
        }).start();
    }

    private void afficherExercices(JSONArray exercices) {
        listeExercices.removeAllViews();
        if (exercices == null) {
            etat.setText("Lecture impossible. Vérifie la connexion et les droits Supabase.");
            return;
        }
        if (exercices.length() == 0) {
            etat.setText("Aucun exercice validé pour ce choix. Les contenus apparaîtront après vérification et publication.");
            return;
        }

        etat.setText(exercices.length() + " exercice(s) disponible(s)");
        for (int i = 0; i < exercices.length(); i++) {
            JSONObject exercice = exercices.optJSONObject(i);
            if (exercice == null) continue;
            Button bouton = Ui.bouton(this, "✏️  " + exercice.optString("titre", "Exercice"), Ui.CARD);
            bouton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            bouton.setOnClickListener(v -> afficherDetail(exercice));
            Ui.ajouter(listeExercices, bouton, 10);
        }
    }

    private void afficherDetail(JSONObject exercice) {
        StringBuilder message = new StringBuilder();
        ajouterBloc(message, "Objectif", exercice.optString("objectif", ""));
        ajouterBloc(message, "Consigne", exercice.optString("consigne", ""));
        ajouterBloc(message, "Explication", exercice.optString("explication", ""));
        ajouterBloc(message, "Correction", exercice.optString("correction", ""));
        new AlertDialog.Builder(this)
                .setTitle(exercice.optString("titre", "Exercice"))
                .setMessage(message.toString().trim())
                .setPositiveButton("Fermer", null)
                .show();
    }

    private static void ajouterBloc(StringBuilder texte, String titre, String valeur) {
        if (valeur == null || valeur.trim().isEmpty()) return;
        if (texte.length() > 0) texte.append("\n\n");
        texte.append(titre).append('\n').append(valeur.trim());
    }
}
