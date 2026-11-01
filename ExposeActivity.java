package bj.radio;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Prépare une demande d'exposé dans Supabase.
 * La génération complète et l'export PDF s'activent lorsqu'un service de
 * rédaction est relié et que le contenu des pages est disponible.
 */
public class ExposeActivity extends Activity {

    private static final int DEMANDE_CREATION_PDF = 42;
    private EditText champTheme;
    private Spinner choixPages;
    private TextView etat;
    private Button enregistrer;
    private LinearLayout exposesTermines;
    private JSONObject exposeAExporter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.fenetre(this);

        int marge = Ui.dp(this, 20);
        LinearLayout contenu = new LinearLayout(this);
        contenu.setOrientation(LinearLayout.VERTICAL);
        contenu.setPadding(marge, marge * 2, marge, marge);
        contenu.setBackground(Ui.fond());

        TextView titre = Ui.texte(this, "Préparer un exposé", 26, Ui.JAUNE, true);
        Ui.ajouter(contenu, titre, 4);

        TextView labelTheme = Ui.texte(this, "Thème de l’exposé", 15, Ui.TXT, true);
        Ui.ajouter(contenu, labelTheme, 24);
        champTheme = Ui.champ(this, "Ex. : L’importance de l’eau potable");
        champTheme.setSingleLine(true);
        Ui.ajouter(contenu, champTheme, 8);

        TextView labelPages = Ui.texte(this, "Nombre total de pages", 15, Ui.TXT, true);
        Ui.ajouter(contenu, labelPages, 20);
        List<String> pages = new ArrayList<>();
        for (int n = 3; n <= 20; n++) pages.add(String.valueOf(n));
        choixPages = new Spinner(this);
        Ui.styleSpinner(choixPages);
        choixPages.setAdapter(Ui.adapterSpinner(this, pages));
        choixPages.setSelection(3); // 6 pages par défaut
        Ui.ajouter(contenu, choixPages, 8);

        TextView structure = Ui.texte(this,
                "Structure demandée : page 1 = plan; pages suivantes = développement; dernière page = conclusion.",
                14, Ui.TXT2, false);
        Ui.ajouter(contenu, structure, 20);

        TextView information = Ui.texte(this,
                "Cette étape enregistre le thème et le format demandé dans Supabase. La rédaction complète et le PDF seront activés lorsque le service de génération sera relié.",
                13, Ui.TXT2, false);
        Ui.ajouter(contenu, information, 14);

        enregistrer = Ui.boutonPilule(this, "Enregistrer la demande", Ui.VIOLET, Ui.BLEU);
        enregistrer.setOnClickListener(v -> enregistrer());
        Ui.ajouter(contenu, enregistrer, 18);

        etat = Ui.texte(this, "", 14, Ui.JAUNE, false);
        etat.setGravity(Gravity.CENTER);
        Ui.ajouter(contenu, etat, 16);

        TextView titreTermines = Ui.texte(this, "Exposés terminés", 18, Ui.TXT, true);
        Ui.ajouter(contenu, titreTermines, 32);
        exposesTermines = new LinearLayout(this);
        exposesTermines.setOrientation(LinearLayout.VERTICAL);
        Ui.ajouter(contenu, exposesTermines, 8);

        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.addView(contenu);
        setContentView(page);
        chargerExposesTermines();
    }

    private void enregistrer() {
        String theme = champTheme.getText() == null ? "" : champTheme.getText().toString().trim();
        if (theme.length() < 3) {
            champTheme.setError("Entre un thème d’au moins 3 caractères.");
            return;
        }

        int nombrePages = choixPages.getSelectedItemPosition() + 3;
        enregistrer.setEnabled(false);
        etat.setText("Enregistrement…");

        new Thread(() -> {
            boolean ok = ScolaireRepository.enregistrerDemandeExpose(theme, nombrePages);
            runOnUiThread(() -> {
                enregistrer.setEnabled(true);
                if (ok) {
                    etat.setText("Demande enregistrée. La génération du contenu reste à raccorder.");
                    Toast.makeText(this, "Demande enregistrée", Toast.LENGTH_SHORT).show();
                } else {
                    etat.setText("Enregistrement impossible. Vérifie la connexion et la migration Supabase.");
                }
            });
        }).start();
    }

    private void chargerExposesTermines() {
        TextView chargement = Ui.texte(this, "Recherche des exposés terminés…", 13, Ui.TXT2, false);
        Ui.ajouter(exposesTermines, chargement, 4);
        new Thread(() -> {
            JSONArray resultats = ScolaireRepository.listerExposesTermines();
            runOnUiThread(() -> afficherExposesTermines(resultats));
        }).start();
    }

    private void afficherExposesTermines(JSONArray resultats) {
        exposesTermines.removeAllViews();
        if (resultats == null) {
            Ui.ajouter(exposesTermines,
                    Ui.texte(this, "Impossible de lire les exposés enregistrés.", 13, Ui.TXT2, false), 4);
            return;
        }
        if (resultats.length() == 0) {
            Ui.ajouter(exposesTermines,
                    Ui.texte(this, "Aucun exposé terminé à exporter pour le moment.", 13, Ui.TXT2, false), 4);
            return;
        }

        for (int i = 0; i < resultats.length(); i++) {
            JSONObject expose = resultats.optJSONObject(i);
            if (expose == null) continue;
            Button bouton = Ui.bouton(this,
                    "📄  Exporter · " + expose.optString("theme", "Exposé"),
                    Ui.CARD);
            bouton.setOnClickListener(v -> demanderEmplacementPdf(expose));
            Ui.ajouter(exposesTermines, bouton, 10);
        }
    }

    private void demanderEmplacementPdf(JSONObject expose) {
        exposeAExporter = expose;
        String nom = expose.optString("theme", "Expose")
                .replaceAll("[^\\p{L}\\p{N} _-]", "")
                .trim()
                .replace(' ', '_');
        if (nom.isEmpty()) nom = "Expose";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, nom + ".pdf");
        try {
            startActivityForResult(intent, DEMANDE_CREATION_PDF);
        } catch (Exception e) {
            etat.setText("Impossible d’ouvrir le sélecteur de fichiers pour le PDF.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != DEMANDE_CREATION_PDF || resultCode != RESULT_OK
                || data == null || data.getData() == null || exposeAExporter == null) return;

        Uri uri = data.getData();
        List<String> pages = pagesPourPdf(exposeAExporter);
        int nombreDemande = exposeAExporter.optInt("nombre_pages", -1);
        if (pages.size() < 3 || pages.size() != nombreDemande) {
            etat.setText("Le contenu de cet exposé ne correspond pas encore au nombre de pages demandé.");
            return;
        }

        try (OutputStream sortie = getContentResolver().openOutputStream(uri)) {
            if (sortie == null) throw new IllegalStateException("Flux de sortie indisponible.");
            ScolairePdfExporter.ecrire(sortie, exposeAExporter.optString("theme", "Exposé"), pages);
            etat.setText("PDF créé : " + pages.size() + " pages.");
        } catch (Exception e) {
            etat.setText("Échec de l’export PDF. Vérifie le contenu et l’espace disponible.");
        }
    }

    /**
     * Convention de stockage : plan = tableau JSON de lignes pour la page 1;
     * contenu_pages = tableau JSON des pages 2 à n, dernière entrée comprise.
     */
    private static List<String> pagesPourPdf(JSONObject expose) {
        List<String> pages = new ArrayList<>();
        pages.add(texteJson(expose.opt("plan")));
        JSONArray contenu = expose.optJSONArray("contenu_pages");
        if (contenu != null) {
            for (int i = 0; i < contenu.length(); i++) {
                pages.add(texteJson(contenu.opt(i)));
            }
        }
        return pages;
    }

    private static String texteJson(Object valeur) {
        if (valeur == null || valeur == JSONObject.NULL) return "";
        if (valeur instanceof JSONArray) {
            JSONArray lignes = (JSONArray) valeur;
            StringBuilder resultat = new StringBuilder();
            for (int i = 0; i < lignes.length(); i++) {
                String ligne = texteJson(lignes.opt(i));
                if (ligne.isEmpty()) continue;
                if (resultat.length() > 0) resultat.append('\n');
                resultat.append("• ").append(ligne);
            }
            return resultat.toString();
        }
        if (valeur instanceof JSONObject) {
            JSONObject objet = (JSONObject) valeur;
            String texte = objet.optString("texte", "");
            return texte.isEmpty() ? objet.toString() : texte;
        }
        return String.valueOf(valeur);
    }
}
