package bj.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Pharmacies de garde du Bénin : départements → communes → pharmacies (par ville / village).
 * Une pharmacie est « de garde » quand la date du jour (heure du Bénin) est dans sa période de garde (lundi → dimanche).
 * Tout le monde lit ; l'administrateur ajoute, modifie, supprime et fixe la garde (section20_pharmacies.sql).
 * La dernière liste reçue est gardée sur le téléphone pour rester lisible sans internet.
 * Import en lot (administrateur) : fichier Excel .xlsx ou CSV lu par PharmaciesImport, envoyé par lots à
 * pharmacies_importer (section21_import_pharmacies.sql).
 */
public class PharmacieActivity extends Activity {
    private static final String ONPB = "https://onpb.bj/category/tour-de-garde/";
    private static final String TOUTES_GARDES = "*";
    private static final int REQ_IMPORT = 21;
    private static final TimeZone BENIN = TimeZone.getTimeZone("Africa/Porto-Novo");

    /** Les 12 départements du Bénin et leurs 77 communes. */
    private static final LinkedHashMap<String, String[]> BENIN_COMMUNES = new LinkedHashMap<>();
    static {
        BENIN_COMMUNES.put("Alibori", new String[]{"Banikoara", "Gogounou", "Kandi", "Karimama", "Malanville", "Ségbana"});
        BENIN_COMMUNES.put("Atacora", new String[]{"Boukoumbé", "Cobly", "Kérou", "Kouandé", "Matéri", "Natitingou", "Pehonko", "Tanguiéta", "Toucountouna"});
        BENIN_COMMUNES.put("Atlantique", new String[]{"Abomey-Calavi", "Allada", "Kpomassè", "Ouidah", "Sô-Ava", "Toffo", "Tori-Bossito", "Zè"});
        BENIN_COMMUNES.put("Borgou", new String[]{"Bembèrèkè", "Kalalé", "N'Dali", "Nikki", "Parakou", "Pèrèrè", "Sinendé", "Tchaourou"});
        BENIN_COMMUNES.put("Collines", new String[]{"Bantè", "Dassa-Zoumè", "Glazoué", "Ouèssè", "Savalou", "Savè"});
        BENIN_COMMUNES.put("Couffo", new String[]{"Aplahoué", "Djakotomey", "Dogbo", "Klouékanmè", "Lalo", "Toviklin"});
        BENIN_COMMUNES.put("Donga", new String[]{"Bassila", "Copargo", "Djougou", "Ouaké"});
        BENIN_COMMUNES.put("Littoral", new String[]{"Cotonou"});
        BENIN_COMMUNES.put("Mono", new String[]{"Athiémé", "Bopa", "Comè", "Grand-Popo", "Houéyogbé", "Lokossa"});
        BENIN_COMMUNES.put("Ouémé", new String[]{"Adjarra", "Adjohoun", "Aguégués", "Akpro-Missérété", "Avrankou", "Bonou", "Dangbo", "Porto-Novo", "Sèmè-Podji"});
        BENIN_COMMUNES.put("Plateau", new String[]{"Adja-Ouèrè", "Ifangni", "Kétou", "Pobè", "Sakété"});
        BENIN_COMMUNES.put("Zou", new String[]{"Abomey", "Agbangnizoun", "Bohicon", "Covè", "Djidja", "Ouinhi", "Za-Kpota", "Zagnanado", "Zogbodomey"});
    }

    private LinearLayout contenu;
    private ScrollView defilement;
    private final List<JSONObject> pharmacies = new ArrayList<>();
    private boolean charge = false;
    private String message = "Chargement…";
    private String dep = null, com = null;
    private boolean gardeSeule = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.fenetre(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackground(Ui.fond());
        defilement = new ScrollView(this);
        contenu = new LinearLayout(this);
        contenu.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        contenu.setPadding(p, p, p, p * 2);
        defilement.addView(contenu);
        page.addView(defilement, new LinearLayout.LayoutParams(-1, -1));
        setContentView(page);
        lireCache();
        afficher();
        recharger();
    }

    @Override
    public void onBackPressed() {
        if (com != null) { com = null; afficher(); }
        else if (dep != null) { dep = null; afficher(); }
        else super.onBackPressed();
    }

    // ------------------------------------------------------------------ données

    private SharedPreferences prefs() { return getSharedPreferences("pharmacies", MODE_PRIVATE); }

    private void lireCache() {
        try {
            String s = prefs().getString("liste", null);
            if (s != null) { remplir(new JSONArray(s)); message = "Hors ligne : dernière liste reçue"; }
        } catch (Exception ignore) { }
    }

    private void remplir(JSONArray a) {
        pharmacies.clear();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) pharmacies.add(o);
        }
        charge = true;
    }

    private void recharger() {
        new Thread(() -> {
            final String r = Supabase.rest("POST", "rpc/pharmacies_liste", "{}", null);
            final String erreur = Supabase.derniereErreur;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (r == null) {
                    if (!charge) {
                        message = erreur != null && erreur.contains("404")
                                ? "La base n'est pas prête : exécutez section20_pharmacies.sql dans Supabase."
                                : (erreur == null || erreur.isEmpty() ? "Connexion impossible : vérifiez internet" : erreur);
                    } else {
                        message = "Hors ligne : dernière liste reçue";
                    }
                } else {
                    try {
                        remplir(new JSONArray(r));
                        prefs().edit().putString("liste", r).apply();
                        message = "";
                    } catch (Exception e) {
                        if (!charge) message = "Réponse inattendue du serveur.";
                    }
                }
                afficher();
            });
        }).start();
    }

    // ------------------------------------------------------------------ garde

    private static String texte(JSONObject o, String cle) { return o.isNull(cle) ? "" : o.optString(cle, ""); }

    private static String aujourdhui() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);
        f.setTimeZone(BENIN);
        return f.format(new Date());
    }

    private static boolean deGarde(JSONObject p) {
        String d = texte(p, "garde_debut"), f = texte(p, "garde_fin");
        if (d.isEmpty() || f.isEmpty()) return false;
        String t = aujourdhui();
        return d.compareTo(t) <= 0 && t.compareTo(f) <= 0;
    }

    /** « 2026-11-08 » → « 08/11 ». */
    private static String jourCourt(String iso) {
        return iso.length() >= 10 ? iso.substring(8, 10) + "/" + iso.substring(5, 7) : iso;
    }

    /** Lundi et dimanche de la semaine en cours (heure du Bénin), au format yyyy-MM-dd. */
    private static String[] semaine() {
        Calendar c = Calendar.getInstance(BENIN);
        int dow = c.get(Calendar.DAY_OF_WEEK);
        c.add(Calendar.DAY_OF_YEAR, -(dow == Calendar.SUNDAY ? 6 : dow - Calendar.MONDAY));
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);
        f.setTimeZone(BENIN);
        String lundi = f.format(c.getTime());
        c.add(Calendar.DAY_OF_YEAR, 6);
        return new String[]{lundi, f.format(c.getTime())};
    }

    private int compter(String d, String c, boolean seulementGarde) {
        int n = 0;
        for (JSONObject p : pharmacies) {
            if (d != null && !d.equals(TOUTES_GARDES) && !d.equals(texte(p, "departement"))) continue;
            if (c != null && !c.equals(texte(p, "commune"))) continue;
            if (!seulementGarde || deGarde(p)) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ écrans

    private void afficher() {
        contenu.removeAllViews();
        boolean admin = Compte.admin();

        if (dep != null) {
            Button retour = Ui.lien(this, "‹  Retour", Ui.TXT2);
            retour.setOnClickListener(v -> onBackPressed());
            Ui.ajouter(contenu, retour, 0);
        }

        String titre = com != null ? com : dep == null ? "💊 Pharmacies de garde"
                : dep.equals(TOUTES_GARDES) ? "🟢 De garde en ce moment" : dep;
        Ui.ajouter(contenu, Ui.texte(this, titre, 24, Ui.TXT, true), 0);
        String sous = dep == null ? "Bénin · départements, communes, villes et villages"
                : com != null ? "Département " + dep : dep.equals(TOUTES_GARDES) ? "Toutes les pharmacies de garde aujourd'hui" : "Choisissez une commune";
        Ui.ajouter(contenu, Ui.texte(this, sous, 13, Ui.TXT2, false), 4);
        if (!message.isEmpty()) Ui.ajouter(contenu, Ui.texte(this, message, 13, Ui.JAUNE, true), 8);

        if (dep == null) ecranDepartements();
        else if (dep.equals(TOUTES_GARDES)) ecranGardes();
        else if (com == null) ecranCommunes();
        else ecranPharmacies(admin);
        defilement.scrollTo(0, 0);
    }

    private View ligne(String titre, String detail, int couleur, View.OnClickListener l) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.rond(Ui.CARD, 16, this));
        int q = Ui.dp(this, 14);
        c.setPadding(q, q, q, q);
        c.setClickable(true);
        c.setOnClickListener(l);
        Ui.pression(c);
        c.addView(Ui.texte(this, titre, 17, Ui.TXT, true));
        TextView d = Ui.texte(this, detail, 12, couleur, false);
        c.addView(d, Ui.lp(this, -1, -2, 0f, 3, 0));
        return c;
    }

    private void ecranDepartements() {
        Button officiel = Ui.bouton(this, "📋 Programme officiel des gardes (ONPB)", Ui.VIOLET);
        officiel.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ONPB))); }
            catch (Exception e) { Toast.makeText(this, "Impossible d'ouvrir le lien", Toast.LENGTH_SHORT).show(); }
        });
        Ui.ajouter(contenu, officiel, 12);

        if (Compte.admin()) {
            Button imp = Ui.boutonContour(this, "📥 Importer des pharmacies (Excel / CSV)", Ui.VERT);
            imp.setOnClickListener(v -> explicationImport());
            Ui.ajouter(contenu, imp, 8);
        }

        int g = compter(null, null, true);
        Ui.ajouter(contenu, ligne("🟢 De garde en ce moment", g + (g > 1 ? " pharmacies de garde aujourd'hui" : " pharmacie de garde aujourd'hui"),
                g > 0 ? Ui.VERT : Ui.TXT2, v -> { dep = TOUTES_GARDES; com = null; afficher(); }), 12);

        for (Map.Entry<String, String[]> e : BENIN_COMMUNES.entrySet()) {
            final String d = e.getKey();
            int n = compter(d, null, false), ng = compter(d, null, true);
            String detail = e.getValue().length + (e.getValue().length > 1 ? " communes" : " commune") + "  ·  " + n
                    + (n > 1 ? " pharmacies" : " pharmacie") + (ng > 0 ? "  ·  " + ng + " de garde" : "");
            Ui.ajouter(contenu, ligne(d, detail, ng > 0 ? Ui.VERT : Ui.TXT2, v -> { dep = d; com = null; afficher(); }), 8);
        }
    }

    private void ecranCommunes() {
        String[] liste = BENIN_COMMUNES.get(dep);
        if (liste == null) return;
        // communes ajoutées par l'administrateur mais absentes de la liste officielle : affichées aussi
        List<String> noms = new ArrayList<>();
        Collections.addAll(noms, liste);
        for (JSONObject p : pharmacies) {
            if (dep.equals(texte(p, "departement")) && !noms.contains(texte(p, "commune"))) noms.add(texte(p, "commune"));
        }
        for (final String c : noms) {
            int n = compter(dep, c, false), ng = compter(dep, c, true);
            String detail = n + (n > 1 ? " pharmacies" : " pharmacie") + (ng > 0 ? "  ·  " + ng + " de garde" : "");
            Ui.ajouter(contenu, ligne(c, detail, ng > 0 ? Ui.VERT : Ui.TXT2, v -> { com = c; afficher(); }), 8);
        }
    }

    private void ecranGardes() {
        List<JSONObject> l = new ArrayList<>();
        for (JSONObject p : pharmacies) if (deGarde(p)) l.add(p);
        if (l.isEmpty()) {
            Ui.ajouter(contenu, Ui.texte(this, "Aucune pharmacie de garde enregistrée pour aujourd'hui.\n"
                    + "Le programme officiel est publié chaque semaine par l'Ordre des pharmaciens (bouton « Programme officiel » sur la page d'accueil de cette section).",
                    14, Ui.TXT2, false), 14);
            return;
        }
        for (JSONObject p : l) Ui.ajouter(contenu, carte(p, true, Compte.admin()), 10);
    }

    private void ecranPharmacies(boolean admin) {
        Button filtre = Ui.boutonContour(this, gardeSeule ? "Afficher toutes les pharmacies" : "Afficher seulement celles de garde", Ui.VERT);
        filtre.setOnClickListener(v -> { gardeSeule = !gardeSeule; afficher(); });
        Ui.ajouter(contenu, filtre, 10);

        if (admin) {
            Button ajout = Ui.bouton(this, "➕ Ajouter une pharmacie à " + com, Ui.VERT);
            ajout.setOnClickListener(v -> formulaire(null));
            Ui.ajouter(contenu, ajout, 8);
        }

        List<JSONObject> l = new ArrayList<>();
        for (JSONObject p : pharmacies) {
            if (!dep.equals(texte(p, "departement")) || !com.equals(texte(p, "commune"))) continue;
            if (gardeSeule && !deGarde(p)) continue;
            l.add(p);
        }
        // de garde d'abord, puis par ville / village et par nom
        Collections.sort(l, (a, b) -> {
            int ga = deGarde(a) ? 0 : 1, gb = deGarde(b) ? 0 : 1;
            if (ga != gb) return ga - gb;
            int c = texte(a, "localite").compareToIgnoreCase(texte(b, "localite"));
            return c != 0 ? c : texte(a, "nom").compareToIgnoreCase(texte(b, "nom"));
        });
        if (l.isEmpty()) {
            Ui.ajouter(contenu, Ui.texte(this, gardeSeule ? "Aucune pharmacie de garde enregistrée dans cette commune."
                    : "Aucune pharmacie enregistrée dans cette commune pour le moment.", 14, Ui.TXT2, false), 14);
            return;
        }
        String derniereLocalite = null;
        for (JSONObject p : l) {
            String loc = texte(p, "localite");
            if (!loc.equals(derniereLocalite)) {
                Ui.ajouter(contenu, Ui.texte(this, loc.isEmpty() ? "Autres" : "📍 " + loc, 14, Ui.JAUNE, true), 14);
                derniereLocalite = loc;
            }
            Ui.ajouter(contenu, carte(p, false, admin), 8);
        }
    }

    private View carte(final JSONObject p, boolean avecLieu, boolean admin) {
        boolean garde = deGarde(p);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.rond(Ui.CARD, 16, this));
        int q = Ui.dp(this, 14);
        c.setPadding(q, q, q, q);

        c.addView(Ui.texte(this, "💊 " + texte(p, "nom"), 17, Ui.TXT, true));
        TextView badge = Ui.texte(this, garde ? "🟢 DE GARDE jusqu'au " + jourCourt(texte(p, "garde_fin")) : "⚪ Pas de garde en ce moment",
                13, garde ? Ui.VERT : Ui.TXT2, true);
        c.addView(badge, Ui.lp(this, -1, -2, 0f, 4, 0));
        if (garde) {
            c.addView(Ui.texte(this, "Garde du " + jourCourt(texte(p, "garde_debut")) + " au " + jourCourt(texte(p, "garde_fin")), 12, Ui.TXT2, false));
        }
        String lieu = (avecLieu ? texte(p, "departement") + " › " + texte(p, "commune") + (texte(p, "localite").isEmpty() ? "" : " › " + texte(p, "localite")) : "");
        if (!lieu.isEmpty()) c.addView(Ui.texte(this, "📍 " + lieu, 12, Ui.TXT2, false), Ui.lp(this, -1, -2, 0f, 4, 0));
        if (!texte(p, "adresse").isEmpty()) c.addView(Ui.texte(this, texte(p, "adresse"), 13, Ui.TXT2, false), Ui.lp(this, -1, -2, 0f, 3, 0));

        final String tel = texte(p, "telephone");
        if (!tel.isEmpty()) {
            Button appel = Ui.bouton(this, "📞 Appeler  " + tel, Ui.BLEU);
            appel.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + tel.replace(" ", "")))); }
                catch (Exception e) { Toast.makeText(this, "Impossible d'appeler", Toast.LENGTH_SHORT).show(); }
            });
            Ui.ajouter(c, appel, 10);
        }

        if (admin) {
            Button g = Ui.boutonContour(this, garde ? "Retirer la garde" : "✅ De garde cette semaine", garde ? Ui.ROSE : Ui.VERT);
            g.setOnClickListener(v -> {
                if (garde) actionServeur("pharmacie_garde", corpsGarde(p, null, null), "Garde retirée");
                else {
                    String[] s = semaine();
                    actionServeur("pharmacie_garde", corpsGarde(p, s[0], s[1]), "De garde du " + jourCourt(s[0]) + " au " + jourCourt(s[1]));
                }
            });
            Ui.ajouter(c, g, 8);
            LinearLayout r = new LinearLayout(this);
            Button m = Ui.boutonContour(this, "Modifier", Ui.TXT);
            m.setOnClickListener(v -> formulaire(p));
            Button s = Ui.boutonContour(this, "Supprimer", Ui.ROSE);
            s.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Supprimer cette pharmacie ?")
                    .setMessage(texte(p, "nom"))
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Supprimer", (d, w) -> {
                        try {
                            actionServeur("pharmacie_supprimer", new JSONObject().put("p_id", texte(p, "id")), "Pharmacie supprimée");
                        } catch (Exception e) { /* ne peut pas arriver */ }
                    }).show());
            r.addView(m, new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(s, new LinearLayout.LayoutParams(0, -2, 1));
            c.addView(r, Ui.lp(this, -1, -2, 0f, 4, 0));
        }
        return c;
    }

    private JSONObject corpsGarde(JSONObject p, String debut, String fin) {
        try {
            return new JSONObject().put("p_id", texte(p, "id"))
                    .put("p_debut", debut == null ? JSONObject.NULL : debut)
                    .put("p_fin", fin == null ? JSONObject.NULL : fin);
        } catch (Exception e) { return new JSONObject(); }
    }

    // ------------------------------------------------------------------ administrateur

    /** Ajouter (p == null) ou modifier une pharmacie ; le département et la commune sont ceux de l'écran. */
    private void formulaire(final JSONObject p) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pd = Ui.dp(this, 20);
        box.setPadding(pd, pd / 2, pd, 0);
        final EditText nom = Ui.champ(this, "Nom de la pharmacie");
        final EditText loc = Ui.champ(this, "Ville ou village");
        final EditText adr = Ui.champ(this, "Adresse / repère (facultatif)");
        final EditText tel = Ui.champ(this, "Téléphone (facultatif)");
        tel.setInputType(InputType.TYPE_CLASS_PHONE);
        if (p != null) {
            nom.setText(texte(p, "nom")); loc.setText(texte(p, "localite"));
            adr.setText(texte(p, "adresse")); tel.setText(texte(p, "telephone"));
        }
        Ui.ajouter(box, nom, 0); Ui.ajouter(box, loc, 8); Ui.ajouter(box, adr, 8); Ui.ajouter(box, tel, 8);
        new AlertDialog.Builder(this)
                .setTitle(p == null ? "Nouvelle pharmacie — " + com : "Modifier la pharmacie")
                .setView(box)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String n = nom.getText().toString().trim();
                    if (n.isEmpty()) { Toast.makeText(this, "Entrez le nom de la pharmacie", Toast.LENGTH_SHORT).show(); return; }
                    try {
                        JSONObject o = new JSONObject()
                                .put("p_id", p == null ? JSONObject.NULL : texte(p, "id"))
                                .put("p_departement", p == null ? dep : texte(p, "departement"))
                                .put("p_commune", p == null ? com : texte(p, "commune"))
                                .put("p_localite", loc.getText().toString().trim())
                                .put("p_nom", n)
                                .put("p_adresse", adr.getText().toString().trim())
                                .put("p_telephone", tel.getText().toString().trim());
                        actionServeur("pharmacie_enregistrer", o, p == null ? "Pharmacie ajoutée" : "Pharmacie modifiée");
                    } catch (Exception e) {
                        Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ import en lot (administrateur)

    private void dialogue(String titre, String texte) {
        new AlertDialog.Builder(this).setTitle(titre).setMessage(texte).setPositiveButton("OK", null).show();
    }

    private void explicationImport() {
        new AlertDialog.Builder(this)
                .setTitle("Importer des pharmacies")
                .setMessage("Choisissez un fichier Excel (.xlsx) ou CSV : une pharmacie par ligne, avec les colonnes\n\n"
                        + "departement · commune · localite (ville ou village) · nom · adresse · telephone · garde_debut · garde_fin\n\n"
                        + "adresse, telephone, garde_debut et garde_fin sont facultatives. Les dates s'écrivent jj/mm/aaaa ou aaaa-mm-jj.\n\n"
                        + "Une pharmacie déjà enregistrée (même département, commune, ville et nom) est mise à jour, pas ajoutée en double.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Choisir le fichier", (d, w) -> choisirFichierImport())
                .show();
    }

    private void choisirFichierImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel",
                "text/plain", "application/octet-stream"});
        try { startActivityForResult(i, REQ_IMPORT); }
        catch (Exception e) { Toast.makeText(this, "Impossible d'ouvrir le choix de fichier", Toast.LENGTH_SHORT).show(); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMPORT || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        message = "Lecture du fichier…";
        afficher();
        new Thread(() -> {
            PharmaciesImport.Resultat r = null;
            String erreur = null;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception("Fichier illisible.");
                r = PharmaciesImport.lire(in, nomDe(uri), BENIN_COMMUNES);
            } catch (Exception e) {
                erreur = e.getMessage() == null || e.getMessage().isEmpty() ? "Fichier illisible." : e.getMessage();
            }
            final PharmaciesImport.Resultat rf = r;
            final String ef = erreur;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                message = "";
                afficher();
                if (ef != null) dialogue("Import impossible", ef);
                else apercuImport(rf);
            });
        }).start();
    }

    private String nomDe(Uri uri) {
        String n = "";
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) n = c.getString(0);
        } catch (Exception ignore) { }
        return n == null ? "" : n;
    }

    private void apercuImport(final PharmaciesImport.Resultat r) {
        int n = r.valides.size(), e = r.erreurs.size();
        StringBuilder sb = new StringBuilder();
        sb.append(r.lues).append(r.lues > 1 ? " lignes lues" : " ligne lue").append(" · ")
                .append(n).append(n > 1 ? " pharmacies prêtes" : " pharmacie prête").append('\n');
        if (e > 0) {
            sb.append('\n').append(e).append(e > 1 ? " lignes à corriger (ignorées) :\n" : " ligne à corriger (ignorée) :\n");
            for (int i = 0; i < Math.min(8, e); i++) sb.append("• ").append(r.erreurs.get(i)).append('\n');
            if (e > 8) sb.append("… et ").append(e - 8).append(" autres\n");
        }
        if (n == 0) { dialogue("Rien à importer", sb.toString().trim()); return; }
        new AlertDialog.Builder(this)
                .setTitle("Importer ce fichier ?")
                .setMessage(sb.toString().trim())
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Importer " + n, (d, w) -> envoyerImport(r.valides))
                .show();
    }

    /** Envoie les lignes par lots de 200 à pharmacies_importer, puis affiche le bilan. */
    private void envoyerImport(final List<PharmaciesImport.Ligne> lignes) {
        final int total = lignes.size();
        message = "Import en cours : 0 / " + total + "…";
        afficher();
        new Thread(() -> {
            int ajoutees = 0, modifiees = 0, refusees = 0;
            String arret = null;
            List<String> detail = new ArrayList<>();
            for (int debut = 0; debut < total && arret == null; debut += 200) {
                List<PharmaciesImport.Ligne> lot = lignes.subList(debut, Math.min(total, debut + 200));
                try {
                    JSONArray arr = new JSONArray();
                    for (PharmaciesImport.Ligne l : lot) {
                        arr.put(new JSONObject().put("ligne", l.numero)
                                .put("departement", l.departement).put("commune", l.commune).put("localite", l.localite)
                                .put("nom", l.nom).put("adresse", l.adresse).put("telephone", l.telephone)
                                .put("garde_debut", l.debut).put("garde_fin", l.fin));
                    }
                    String rep = Supabase.rest("POST", "rpc/pharmacies_importer", new JSONObject().put("p_lignes", arr).toString(), null);
                    if (rep == null) {
                        String reseau = Supabase.derniereErreur;
                        arret = reseau != null && reseau.contains("404")
                                ? "La base n'est pas prête : exécutez section21_import_pharmacies.sql dans Supabase."
                                : (reseau == null || reseau.isEmpty() ? "Connexion impossible : vérifiez internet" : reseau);
                    } else {
                        JSONObject o = new JSONObject(rep);
                        if (!o.optBoolean("ok")) {
                            arret = o.optString("erreur", "Refusé par le serveur.");
                        } else {
                            ajoutees += o.optInt("ajoutees");
                            modifiees += o.optInt("modifiees");
                            refusees += o.optInt("refusees");
                            JSONArray dt = o.optJSONArray("detail");
                            if (dt != null) for (int k = 0; k < dt.length(); k++) detail.add(dt.optString(k));
                        }
                    }
                } catch (Exception ex) {
                    arret = "Erreur : " + ex.getMessage();
                }
                final int fait = Math.min(total, debut + 200);
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    message = "Import en cours : " + fait + " / " + total + "…";
                    afficher();
                });
            }
            final int a = ajoutees, m = modifiees, rf = refusees;
            final String stop = arret;
            final List<String> det = detail;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                message = "";
                StringBuilder sb = new StringBuilder();
                sb.append(a).append(a > 1 ? " pharmacies ajoutées" : " pharmacie ajoutée").append('\n')
                        .append(m).append(m > 1 ? " pharmacies mises à jour" : " pharmacie mise à jour").append('\n');
                if (rf > 0) sb.append(rf).append(rf > 1 ? " lignes refusées par le serveur" : " ligne refusée par le serveur").append('\n');
                for (String x : det) sb.append("• ").append(x).append('\n');
                if (stop != null) sb.append("\n⚠ Import interrompu : ").append(stop);
                dialogue(stop == null ? "✔ Import terminé" : "Import incomplet", sb.toString().trim());
                recharger();
            });
        }).start();
    }

    private void actionServeur(final String fonction, final JSONObject corps, final String confirmation) {
        new Thread(() -> {
            final String r = Supabase.rest("POST", "rpc/" + fonction, corps.toString(), null);
            final String reseau = Supabase.derniereErreur;
            String erreur = null;
            if (r == null) {
                erreur = reseau != null && reseau.contains("404")
                        ? "La base n'est pas prête : exécutez section20_pharmacies.sql dans Supabase."
                        : (reseau == null || reseau.isEmpty() ? "Connexion impossible : vérifiez internet" : reseau);
            } else {
                try {
                    JSONObject o = new JSONObject(r);
                    if (!o.optBoolean("ok")) erreur = o.optString("erreur", "Refusé par le serveur.");
                } catch (Exception e) { erreur = "Réponse inattendue du serveur."; }
            }
            final String err = erreur;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                Toast.makeText(this, err == null ? "✔ " + confirmation : err, Toast.LENGTH_LONG).show();
                if (err == null) recharger();
            });
        }).start();
    }
}
