package bj.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Administrateur : cherche la dernière mise à jour dans la base, affiche la dernière mise à jour appliquée
 * (avec la date de création du fichier dans la base) et permet de modifier / publier le fichier de configuration (JSON).
 * Une publication s'applique toute seule à tous les utilisateurs, à leur prochaine ouverture de l'appli.
 */
public class MajActivity extends Activity {

    private static final String EXEMPLE = "{\n"
            + "  \"titre\": \"Kouamé Play Box\",\n"
            + "  \"bandeau\": \"Bienvenue !\",\n"
            + "  \"theme\": {\"fond_haut\": \"#6A6E77\", \"fond_bas\": \"#5C6068\", \"jaune\": \"#FFC857\", \"rayon\": 1},\n"
            + "  \"annonce\": {\"id\": \"a1\", \"titre\": \"Nouveau\", \"message\": \"Une nouveauté est arrivée.\"},\n"
            + "  \"tuiles\": [\n"
            + "    {\"id\": \"radio\", \"titre\": \"Radio\", \"c1\": \"#FF6B35\", \"c2\": \"#FF3D6E\"},\n"
            + "    {\"id\": \"sms\", \"masquer\": true},\n"
            + "    {\"id\": \"promo\", \"emoji\": \"⭐\", \"titre\": \"Promo\", \"desc\": \"Notre page spéciale\",\n"
            + "     \"c1\": \"#8E24AA\", \"c2\": \"#FF5252\", \"action\": \"page:promo\"}\n"
            + "  ],\n"
            + "  \"ordre\": [\"tvkouame\", \"radio\"],\n"
            + "  \"pages\": {\n"
            + "    \"promo\": {\"titre\": \"Promo\", \"html\": \"<h1>Bonjour</h1><p>Contenu de la page.</p>\"},\n"
            + "    \"site\": {\"titre\": \"Mon site\", \"zip\": \"site.zip\", \"index\": \"index.html\"}\n"
            + "  }\n"
            + "}";

    private TextView derniereTxt, statut, apkInfo, apkPretTxt, progTxt;
    private EditText editeur, note, noteApk, versionApk;
    private CheckBox apkObligatoire;
    private Button chercher, publier, apkBtn, apkEnvoyer, zipBtn;
    private ProgressBar barre;

    // APK choisi, prêt à envoyer
    private File apkPret;
    private int apkCode;
    private String apkNom = "";
    private long apkTaille;
    private boolean apkMemeSignature = true;
    private volatile int publieeCode = 0;      // dernière version publiée dans la base (0 = aucune)
    private volatile boolean envoiEnCours = false;
    private static final int CODE_ZIP = 21;
    private static final int CODE_APK = 22;
    private static final long APK_MAX = 50L * 1024 * 1024;   // limite du stockage Supabase (plan gratuit)

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.fenetre(this);
        if (!Compte.admin()) {
            finish();
            return;
        }
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackground(Ui.fond());
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        c.setPadding(p, p, p, p);

        Ui.ajouter(c, Ui.texte(this, "⬆ Mise à jour", 24, Ui.TXT, true), 0);
        Ui.ajouter(c, Ui.texte(this, "Le fichier de configuration est dans la base de données. À chaque ouverture, l'appli de chaque "
                + "utilisateur le lit et se met à jour toute seule : titres, tuiles, couleurs, annonces et pages entières.", 13, Ui.TXT2, false), 6);

        derniereTxt = Ui.texte(this, "", 14, Ui.JAUNE, false);
        Ui.ajouter(c, derniereTxt, 12);

        chercher = Ui.bouton(this, "🔍 Chercher une mise à jour", Ui.VIOLET);
        chercher.setOnClickListener(v -> chercher());
        Ui.ajouter(c, chercher, 12);

        statut = Ui.texte(this, "", 14, Ui.JAUNE, false);
        Ui.ajouter(c, statut, 10);

        Ui.ajouter(c, Ui.texte(this, "📱 Nouvelle version de l'application", 18, Ui.TXT, true), 20);
        Ui.ajouter(c, Ui.texte(this, "1. Choisissez l'APK (ou le zip téléchargé depuis GitHub, je l'ouvre tout seul).  "
                + "2. Vérifiez la version.  3. Envoyez : tous les comptes la voient à leur prochaine ouverture.", 13, Ui.TXT2, false), 4);
        apkInfo = Ui.texte(this, "", 14, Ui.JAUNE, false);
        Ui.ajouter(c, apkInfo, 8);

        apkBtn = Ui.bouton(this, "📱 1. Choisir l'APK ou le zip", Ui.VERT);
        apkBtn.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, CODE_APK);
        });
        Ui.ajouter(c, apkBtn, 8);

        apkPretTxt = Ui.texte(this, "", 14, Ui.TXT, true);
        Ui.ajouter(c, apkPretTxt, 8);
        versionApk = Ui.champ(this, "2. Version affichée (ex. 1.23)");
        versionApk.setVisibility(View.GONE);
        Ui.ajouter(c, versionApk, 6);

        apkEnvoyer = Ui.bouton(this, "🚀 3. Envoyer et mettre à jour tout le monde", Ui.ACCENT);
        apkEnvoyer.setEnabled(false);
        apkEnvoyer.setOnClickListener(v -> envoyerEtPublier());
        Ui.ajouter(c, apkEnvoyer, 8);

        barre = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barre.setMax(1000);
        barre.setVisibility(View.GONE);
        barre.setProgressTintList(ColorStateList.valueOf(Ui.VERT));
        Ui.ajouter(c, barre, 8);
        progTxt = Ui.texte(this, "", 13, Ui.JAUNE, false);
        Ui.ajouter(c, progTxt, 4);

        // Options rarement utilisées : cachées pour ne pas encombrer l'écran
        LinearLayout optApk = new LinearLayout(this);
        optApk.setOrientation(LinearLayout.VERTICAL);
        noteApk = Ui.champ(this, "Nouveautés de cette version (facultatif)");
        Ui.ajouter(optApk, noteApk, 6);
        apkObligatoire = new CheckBox(this);
        apkObligatoire.setText("Mise à jour obligatoire (impossible de la repousser)");
        apkObligatoire.setTextColor(Ui.TXT);
        Ui.ajouter(optApk, apkObligatoire, 4);
        Button apkRetirer = Ui.boutonContour(this, "↩ Retirer la dernière version publiée", Ui.TXT);
        apkRetirer.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Retirer la dernière version ?")
                .setMessage("Les comptes ne verront plus cette mise à jour. Ceux qui l'ont déjà installée la gardent.")
                .setPositiveButton("Retirer", (d, w) -> new Thread(() -> {
                    final boolean ok = Supabase.adminMajRetirer();
                    runOnUiThread(() -> {
                        statut.setText(ok ? "✅ Dernière version retirée." : "⚠ Retrait impossible.");
                        rafraichirApk();
                    });
                }).start())
                .setNegativeButton("Annuler", null)
                .show());
        Ui.ajouter(optApk, apkRetirer, 8);
        pliable(c, "Options de la version (nouveautés, obligatoire, retirer)", optApk);

        // Contenu zip + fichier JSON : cachés aussi
        LinearLayout avance = new LinearLayout(this);
        avance.setOrientation(LinearLayout.VERTICAL);
        Ui.ajouter(avance, Ui.texte(this, "📦 Contenu (zip de pages web)", 16, Ui.TXT, true), 6);
        Ui.ajouter(avance, Ui.texte(this, "Pour des pages web (index.html, images…), PAS pour l'APK. Renvoyer un zip du même nom le remplace pour tout le monde.", 13, Ui.TXT2, false), 4);
        zipBtn = Ui.bouton(this, "📦 Choisir et envoyer un zip", Ui.ACCENT);
        zipBtn.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, CODE_ZIP);
        });
        Ui.ajouter(avance, zipBtn, 8);

        Ui.ajouter(avance, Ui.texte(this, "✏ Modifier le fichier (JSON)", 16, Ui.TXT, true), 16);
        editeur = Ui.champ(this, "Fichier JSON");
        editeur.setSingleLine(false);
        editeur.setMinLines(12);
        editeur.setGravity(Gravity.TOP | Gravity.START);
        editeur.setTypeface(Typeface.MONOSPACE);
        editeur.setTextSize(12);
        editeur.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        Ui.ajouter(avance, editeur, 8);

        Button exemple = Ui.boutonContour(this, "📄 Insérer un exemple", Ui.TXT);
        exemple.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Remplacer le texte par l'exemple ?")
                .setPositiveButton("Remplacer", (d, w) -> editeur.setText(EXEMPLE))
                .setNegativeButton("Annuler", null)
                .show());
        Ui.ajouter(avance, exemple, 8);

        note = Ui.champ(this, "Note pour cette mise à jour (facultatif)");
        Ui.ajouter(avance, note, 8);

        publier = Ui.bouton(this, "📤 Publier pour tous les utilisateurs", Ui.VERT);
        publier.setOnClickListener(v -> confirmerPublication());
        Ui.ajouter(avance, publier, 8);
        pliable(c, "Avancé : contenu zip et fichier JSON", avance);

        ScrollView sv = new ScrollView(this);
        sv.addView(c);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);

        remplirEditeur();
        rafraichirEntete();
        rafraichirApk();
        chercher();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rafraichirEntete();
    }

    private void rafraichirEntete() {
        String d = Config.info(this);
        String z = Paquets.info(this);
        derniereTxt.setText((d == null ? "Dernière mise à jour : aucune pour l'instant (l'appli utilise ses réglages d'origine)."
                : "✅ Dernière mise à jour :\n   " + d) + (z == null ? "" : "\n\nContenu :\n" + z));
    }

    private void remplirEditeur() {
        JSONObject cfg = Config.contenu(this);
        try {
            editeur.setText(cfg == null ? "{\n  \n}" : cfg.toString(2));
        } catch (Exception e) {
            editeur.setText("{\n  \n}");
        }
    }

    /** Bouton qui montre ou cache un bloc (garde l'écran simple). */
    private void pliable(LinearLayout parent, final String titre, final LinearLayout contenu) {
        contenu.setVisibility(View.GONE);
        final Button b = Ui.boutonContour(this, "▸ " + titre, Ui.TXT);
        b.setOnClickListener(v -> {
            boolean voir = contenu.getVisibility() != View.VISIBLE;
            contenu.setVisibility(voir ? View.VISIBLE : View.GONE);
            b.setText((voir ? "▾ " : "▸ ") + titre);
        });
        Ui.ajouter(parent, b, 16);
        Ui.ajouter(parent, contenu, 4);
    }

    private void chercher() {
        statut.setText("Recherche dans la base de données…");
        chercher.setEnabled(false);
        new Thread(() -> {
            final JSONObject r = Supabase.configDerniere();
            final boolean nouveau = r != null && r.optBoolean("ok") && Config.rafraichir(this);
            final boolean contenu = r != null && r.optBoolean("ok") && Paquets.synchroniser(this);
            runOnUiThread(() -> {
                chercher.setEnabled(true);
                if (r == null || !r.optBoolean("ok")) {
                    statut.setText("⚠ Impossible de consulter la base : vérifiez internet.");
                } else if (r.optBoolean("aucune") && !nouveau) {
                    statut.setText("Aucune mise à jour publiée dans la base pour le moment.");
                } else if (nouveau || contenu) {
                    statut.setText("🆕 Nouvelle mise à jour appliquée. Elle s'affiche à l'accueil.");
                    if (nouveau) remplirEditeur();
                } else {
                    statut.setText("✅ Aucune nouvelle mise à jour : vous avez déjà la dernière.");
                }
                rafraichirEntete();
            });
        }).start();
    }

    // ------------------------------------------------------------------ outils d'affichage

    private static String reste(long ms) {
        long s = Math.max(1, ms / 1000);
        if (s < 90) return s + " s";
        if (s < 5400) return (s / 60) + " min";
        return String.format(java.util.Locale.FRANCE, "%.1f h", s / 3600.0);
    }

    /** Barre + texte : « 12,0 Mo / 44,5 Mo — 27 % — 1,2 Mo/s — reste 25 s ». Appelable depuis n'importe quel thread. */
    private void montrerProgres(final String etape, final long envoye, final long total, final int essai, final long debut) {
        runOnUiThread(() -> {
            barre.setVisibility(View.VISIBLE);
            int pour1000 = total > 0 ? (int) Math.min(1000, envoye * 1000 / total) : 0;
            barre.setProgress(pour1000);
            long dt = Math.max(1, System.currentTimeMillis() - debut);
            double vitesse = envoye * 1000.0 / dt;                       // octets par seconde
            StringBuilder sb = new StringBuilder(etape).append("\n")
                    .append(Supabase.taille(envoye)).append(" / ").append(Supabase.taille(total))
                    .append(" — ").append(pour1000 / 10).append(" %");
            if (envoye > 0 && vitesse > 0) {
                sb.append(" — ").append(String.format(java.util.Locale.FRANCE, "%.1f Mo/s", vitesse / 1048576.0));
                if (total > envoye) sb.append(" — reste ").append(reste((long) ((total - envoye) / vitesse * 1000)));
            }
            if (essai > 1) sb.append("\n⚠ Nouvel essai (").append(essai).append("/3) après une coupure");
            progTxt.setText(sb.toString());
        });
    }

    private void occupe(boolean oui) {
        envoiEnCours = oui;
        apkBtn.setEnabled(!oui);
        zipBtn.setEnabled(!oui);
        apkEnvoyer.setEnabled(!oui && apkPret != null);
        if (oui) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   // l'écran reste allumé pendant l'envoi
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /** Explication claire d'un code d'erreur. */
    private String expliquer(String e) {
        if ("version_trop_ancienne".equals(e))
            return "Une version égale ou plus récente est déjà publiée. Augmentez versionCode dans app.gradle, recompilez, puis recommencez.";
        if ("apk_introuvable".equals(e)) return "L'APK n'est pas arrivé dans la base. Recommencez l'envoi.";
        if ("http 413".equals(e) || "http 400".equals(e)) return "Fichier refusé par la base (trop gros ? maximum 50 Mo).";
        if ("http 401".equals(e) || "http 403".equals(e)) return "Droits refusés : reconnectez-vous avec le compte administrateur.";
        if ("reseau".equals(e)) return Supabase.derniereErreur + ". 3 essais faits : vérifiez internet puis recommencez.";
        return e + (Supabase.derniereErreur.isEmpty() ? "" : " — " + Supabase.derniereErreur);
    }

    // ------------------------------------------------------------------ envoi d'un zip de contenu

    @Override
    protected void onActivityResult(int code, int resultat, Intent data) {
        super.onActivityResult(code, resultat, data);
        if (code == CODE_APK) {
            apkChoisi(resultat, data);
            return;
        }
        if (code != CODE_ZIP || resultat != RESULT_OK || data == null || data.getData() == null) return;
        if (envoiEnCours) return;
        final Uri uri = data.getData();
        String nom = "contenu.zip";
        long taille = -1;
        try (Cursor cur = getContentResolver().query(uri, null, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                int i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && cur.getString(i) != null) nom = cur.getString(i);
                int j = cur.getColumnIndex(OpenableColumns.SIZE);
                if (j >= 0 && !cur.isNull(j)) taille = cur.getLong(j);
            }
        } catch (Exception ignore) {
            // on garde le nom par défaut
        }
        final String propre = nom.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!propre.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            statut.setText("⚠ Choisissez un fichier .zip.");
            return;
        }
        final long t = taille;
        statut.setText("Envoi du zip " + propre + "…");
        occupe(true);
        new Thread(() -> {
            final long debut = System.currentTimeMillis();
            final String err = Supabase.envoyerPaquet(propre, () -> getContentResolver().openInputStream(uri), t,
                    (env, tot, essai) -> montrerProgres("📦 Envoi de " + propre, env, tot, essai, debut));
            if (err.isEmpty()) Paquets.synchroniser(this);
            runOnUiThread(() -> {
                occupe(false);
                barre.setVisibility(View.GONE);
                progTxt.setText("");
                if (err.isEmpty()) {
                    statut.setText("✅ Zip envoyé : " + propre + "\nPour l'afficher, ajoutez dans le JSON (Avancé) puis publiez :\n"
                            + "\"pages\": {\"ma_page\": {\"titre\": \"Titre\", \"zip\": \"" + propre + "\"}}\n"
                            + "et une tuile avec \"action\": \"page:ma_page\". Si ce zip est déjà dans le JSON, "
                            + "tout le monde reçoit déjà la nouvelle version.");
                } else {
                    statut.setText("⚠ Envoi impossible : " + expliquer(err));
                }
                rafraichirEntete();
            });
        }).start();
    }

    // ------------------------------------------------------------------ nouvelle version de l'appli (APK)

    private void rafraichirApk() {
        new Thread(() -> {
            final JSONObject r = Supabase.majDerniere();
            runOnUiThread(() -> {
                String ici = "Installée sur ce téléphone : " + MiseAJour.nomInstalle(this) + " (n° " + MiseAJour.versionInstallee(this) + ")";
                if (r == null || !r.optBoolean("ok")) {
                    apkInfo.setText(ici + "\nVersion publiée : impossible de consulter la base.");
                } else if (r.optBoolean("aucune")) {
                    publieeCode = 0;
                    apkInfo.setText(ici + "\nAucune version publiée dans la base pour le moment.");
                } else {
                    publieeCode = (int) r.optLong("version_code");
                    apkInfo.setText(ici + "\nPubliée pour tous : " + r.optString("version_name") + " (n° " + r.optLong("version_code")
                            + "), sortie le " + Config.date(r.optLong("cree_ms", 0L)));
                }
            });
        }).start();
    }

    /** Copie le fichier choisi dans le cache. Si c'est un zip (téléchargement GitHub), en sort le .apk. */
    private void apkChoisi(int resultat, Intent data) {
        if (resultat != RESULT_OK || data == null || data.getData() == null || envoiEnCours) return;
        final Uri uri = data.getData();
        apkPret = null;
        apkEnvoyer.setEnabled(false);
        versionApk.setVisibility(View.GONE);
        apkPretTxt.setText("");
        statut.setText("Lecture du fichier…");
        new Thread(() -> {
            final File brut = new File(getCacheDir(), "maj_admin_brut");
            File apk = new File(getCacheDir(), "maj_admin.apk");
            try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(brut)) {
                if (in == null) throw new Exception("lecture");
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } catch (Exception e) {
                runOnUiThread(() -> statut.setText("⚠ Lecture du fichier impossible."));
                return;
            }
            PackageInfo pi = getPackageManager().getPackageArchiveInfo(brut.getAbsolutePath(), 0);
            if (pi != null) {
                brut.renameTo(apk);
            } else {
                // pas un APK : un zip qui en contient un ?
                if (!sortirApkDuZip(brut, apk)) {
                    brut.delete();
                    runOnUiThread(() -> statut.setText("⚠ Ce fichier n'est ni un APK ni un zip contenant un APK."));
                    return;
                }
                brut.delete();
                pi = getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            }
            if (pi == null) {
                runOnUiThread(() -> statut.setText("⚠ Ce fichier n'est pas un APK valide."));
                return;
            }
            if (!getPackageName().equals(pi.packageName)) {
                final String autre = pi.packageName;
                runOnUiThread(() -> statut.setText("⚠ Cet APK est une autre application (" + autre + ")."));
                return;
            }
            final long taille = apk.length();
            if (taille > APK_MAX) {
                runOnUiThread(() -> statut.setText("⚠ APK trop gros (" + Supabase.taille(taille) + ") : maximum 50 Mo."));
                return;
            }
            final long vc = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
            final String vn = pi.versionName == null ? String.valueOf(vc) : pi.versionName;
            final boolean meme = MiseAJour.memeSignatureQueInstallee(this, apk);
            final File pret = apk;
            runOnUiThread(() -> {
                apkPret = pret;
                apkCode = (int) vc;
                apkNom = vn;
                apkTaille = taille;
                apkMemeSignature = meme;
                apkPretTxt.setText("✅ APK prêt : version " + vn + " (n° " + vc + "), " + Supabase.taille(taille)
                        + (meme ? "" : "\n⚠ Signature différente de l'appli installée (voir l'avertissement à l'envoi)."));
                versionApk.setText(vn);
                versionApk.setVisibility(View.VISIBLE);
                apkEnvoyer.setEnabled(true);
                statut.setText(vc <= publieeCode
                        ? "⚠ La version n° " + publieeCode + " est déjà publiée : celle-ci (n° " + vc + ") n'est pas plus récente. Augmentez versionCode dans app.gradle."
                        : "Vérifiez la version puis touchez « Envoyer ».");
            });
        }).start();
    }

    /** Extrait le plus gros fichier .apk d'un zip vers « sortie ». Faux si le fichier n'est pas un zip ou n'a pas d'APK. */
    private boolean sortirApkDuZip(File zip, File sortie) {
        try (ZipFile z = new ZipFile(zip)) {
            ZipEntry meilleure = null;
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || !e.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) continue;
                if (meilleure == null || e.getSize() > meilleure.getSize()) meilleure = e;
            }
            if (meilleure == null) return false;
            try (InputStream in = z.getInputStream(meilleure); OutputStream out = new FileOutputStream(sortie)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void envoyerEtPublier() {
        if (apkPret == null || envoiEnCours) return;
        if (apkCode <= publieeCode) {
            statut.setText("⚠ La version n° " + publieeCode + " est déjà publiée : celle-ci (n° " + apkCode
                    + ") n'est pas plus récente. Augmentez versionCode dans app.gradle, recompilez, puis recommencez.");
            return;
        }
        String ecrite = versionApk.getText().toString().trim();
        final String nomVersion = ecrite.isEmpty() ? apkNom : ecrite;
        if (apkMemeSignature) {
            lancerEnvoi(nomVersion);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Signature différente")
                .setMessage("Cet APK n'a PAS la même signature que l'appli installée sur ce téléphone : les téléphones qui ont déjà l'appli "
                        + "ne pourront pas la remplacer sans la désinstaller. Compilez avec le fichier sossokouame.keystore.\n\nEnvoyer quand même ?")
                .setPositiveButton("Envoyer", (d, w) -> lancerEnvoi(nomVersion))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void lancerEnvoi(final String nomVersion) {
        final File f = apkPret;
        final int versionCode = apkCode;
        final long taille = apkTaille;
        final String nomApk = "sossokouame-" + versionCode + ".apk";
        final String n = noteApk.getText().toString().trim();
        final boolean obligatoire = apkObligatoire.isChecked();
        occupe(true);
        statut.setText("Envoi de la version " + nomVersion + " (n° " + versionCode + ")…");
        new Thread(() -> {
            final long debut = System.currentTimeMillis();
            String err = Supabase.envoyerApk(nomApk, () -> new FileInputStream(f), taille,
                    (env, tot, essai) -> montrerProgres("📱 Étape 1/2 : envoi de l'APK", env, tot, essai, debut));
            if (err.isEmpty()) {
                runOnUiThread(() -> progTxt.setText("📢 Étape 2/2 : annonce de la version à tous les comptes…"));
                err = Supabase.adminMajPublier(versionCode, nomVersion, n, nomApk, taille, obligatoire);
            }
            final String e2 = err;
            runOnUiThread(() -> {
                occupe(false);
                barre.setVisibility(View.GONE);
                progTxt.setText("");
                if (e2.isEmpty()) {
                    statut.setText("✅ Version " + nomVersion + " publiée : tous les comptes la verront à leur prochaine ouverture.");
                    noteApk.setText("");
                    apkObligatoire.setChecked(false);
                    apkPret = null;
                    apkEnvoyer.setEnabled(false);
                    versionApk.setVisibility(View.GONE);
                    apkPretTxt.setText("");
                    f.delete();
                } else {
                    statut.setText("⚠ " + expliquer(e2));
                }
                rafraichirApk();
            });
        }).start();
    }

    private void confirmerPublication() {
        final JSONObject contenu;
        try {
            contenu = new JSONObject(editeur.getText().toString().trim());
        } catch (Exception e) {
            statut.setText("⚠ Le fichier n'est pas du JSON valide : " + e.getMessage());
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Publier pour tous ?")
                .setMessage("Tous les utilisateurs recevront cette mise à jour à leur prochaine ouverture de l'appli.")
                .setPositiveButton("Publier", (d, w) -> publier(contenu))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void publier(final JSONObject contenu) {
        statut.setText("Publication…");
        publier.setEnabled(false);
        final String n = note.getText().toString().trim();
        new Thread(() -> {
            final String err = Supabase.adminConfigPublier(contenu, n);
            if (err.isEmpty()) Config.rafraichir(this);
            runOnUiThread(() -> {
                publier.setEnabled(true);
                if (err.isEmpty()) {
                    statut.setText("✅ Mise à jour publiée pour tous les utilisateurs.");
                    note.setText("");
                } else if ("json_invalide".equals(err)) {
                    statut.setText("⚠ Le fichier doit être un objet JSON { … }.");
                } else if ("trop_gros".equals(err)) {
                    statut.setText("⚠ Le fichier est trop gros.");
                } else {
                    statut.setText("⚠ Publication impossible (" + err + ").");
                }
                rafraichirEntete();
            });
        }).start();
    }
}
