package bj.radio;

import android.animation.Animator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private LinearLayout mosaique;
    private TextView bandeauMaj;
    /** Une tuile de l'accueil (réglages d'origine, modifiables par le fichier de configuration de la base). */
    private static final class T {
        String id, emoji, titre, desc, action = "";
        int c1, c2;
        Class<?> cible;
        boolean visible = true;
    }

    private final Map<String, Class<?>> ecrans = new HashMap<>();
    private final List<Animator> animations = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.fenetre(this);

        // Il faut un compte pour entrer dans l'application.
        if (!Compte.connecte()) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        // TV Kouamé : notification « Programme du jour » chaque jour à 00h00 (heure du Bénin)
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 11);
        }
        ProgrammeAlarme.planifier(this);

        // Dossier « TvKouame/Telechargement » dans le stockage : créé dès que l'autorisation est donnée (demandée une fois).
        Dossiers.creer(this);
        if (!Dossiers.autorise(this) && !Dossiers.dejaDemande(this)) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Dossier TvKouame")
                    .setMessage("Pour créer le dossier « TvKouame » dans le stockage du téléphone, où seront rangées toutes "
                            + "les vidéos téléchargées (TvKouame/Telechargement), autorisez l'accès aux fichiers.")
                    .setPositiveButton("Autoriser", (d, w) -> Dossiers.demanderAcces(this))
                    .setNegativeButton("Plus tard", null)
                    .show();
        }

        int p = Ui.dp(this, 18);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, Ui.dp(this, 20), p, Ui.dp(this, 24));

        final JSONObject cfg = Config.contenu(this);
        String titreApp = Config.titreApp(this);
        String nom = Compte.nom();
        String sousDefaut = cfg != null && !cfg.optString("sous_titre", "").trim().isEmpty()
                ? cfg.optString("sous_titre").trim() : "Médias, jeux et création";

        FrameLayout entete = new FrameLayout(this);
        entete.setBackground(Ui.rond(0xFF172438, 22, this));
        entete.setClipToOutline(true);

        ImageView imageEntete = new ImageView(this);
        // Image d'en-tête : cherchée par son nom, pour que l'appli compile même si le fichier n'est pas dans le projet.
        int idHero = getResources().getIdentifier("kouame_playbox_hero", "drawable", getPackageName());
        if (idHero != 0) imageEntete.setImageResource(idHero);
        imageEntete.setScaleType(ImageView.ScaleType.CENTER_CROP);
        entete.addView(imageEntete, new FrameLayout.LayoutParams(-1, -1));

        View voile = new View(this);
        voile.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF20B121B, 0xC90B121B, 0x33101B29}));
        entete.addView(voile, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout contenuEntete = new LinearLayout(this);
        contenuEntete.setOrientation(LinearLayout.VERTICAL);
        contenuEntete.setGravity(Gravity.CENTER_VERTICAL);
        contenuEntete.setPadding(Ui.dp(this, 18), Ui.dp(this, 17), Ui.dp(this, 18), Ui.dp(this, 18));
        entete.addView(contenuEntete, new FrameLayout.LayoutParams(-1, -1));

        TextView surTitre = Ui.texte(this, "KOUAMÉ PLAY BOX  ·  VOTRE ESPACE", 11, 0xFF9FE2C7, true);
        contenuEntete.addView(surTitre);

        TextView titre = Ui.texte(this, titreApp, 28, Ui.TXT, true);
        titre.setMaxLines(1);
        titre.setEllipsize(android.text.TextUtils.TruncateAt.END);
        contenuEntete.addView(titre, Ui.lp(this, -1, -2, 0f, 5, 0));
        animations.add(Ui.titreColore(titre));

        TextView sous = Ui.texte(this, nom.isEmpty() ? sousDefaut : "Bonjour " + nom + " 👋",
                14, Ui.TXT2, false);
        sous.setMaxLines(2);
        contenuEntete.addView(sous, Ui.lp(this, -1, -2, 0f, 5, 0));
        root.addView(entete, Ui.lp(this, -1, Ui.dp(this, 208), 0f, 0, 0));
        Ui.entree(entete, 0);

        String bandeau = cfg == null ? "" : cfg.optString("bandeau", "").trim();
        if (!bandeau.isEmpty()) {
            TextView bd = Ui.texte(this, bandeau, 14, Ui.JAUNE, true);
            bd.setGravity(Gravity.CENTER);
            root.addView(bd, Ui.lp(this, -1, -2, 0f, 10, 0));
        }

        // Mise à jour de l'appli : bandeau visible tant que la nouvelle version n'est pas installée.
        bandeauMaj = Ui.texte(this, "", 15, 0xFF222222, true);
        bandeauMaj.setGravity(Gravity.CENTER);
        bandeauMaj.setBackground(Ui.rond(Ui.JAUNE, 16, this));
        int pm = Ui.dp(this, 12);
        bandeauMaj.setPadding(pm, pm, pm, pm);
        bandeauMaj.setVisibility(View.GONE);
        bandeauMaj.setOnClickListener(v -> {
            JSONObject r = MiseAJour.derniere;
            if (r != null && MiseAJour.disponible(this, r)) MiseAJour.proposer(this, r);
        });
        root.addView(bandeauMaj, Ui.lp(this, -1, -2, 0f, 14, 0));
        afficherBandeauMaj();

        mosaique = new LinearLayout(this);
        mosaique.setOrientation(LinearLayout.VERTICAL);
        TextView explorer = Ui.texte(this, "Explorer", 21, Ui.TXT, true);
        root.addView(explorer, Ui.lp(this, -1, -2, 0f, 19, 0));
        root.addView(mosaique, Ui.lp(this, -1, -2, 0f, 0, 0));

        List<T> liste = new ArrayList<>();
        def(liste, "radio", "📻", "Radio", "Radios par pays et par ville", Ui.ACCENT, 0xFFFF3D6E, RadioActivity.class);
        def(liste, "tv", "📺", "Télévision", "Chaînes du monde, en plein écran", Ui.VIOLET, 0xFF3A7BFF, TvActivity.class);
        def(liste, "tvkouame", "📡", "TV Kouamé", "Programme du jour, nouveau chaque jour à 00h00", 0xFFE91E63, 0xFFFF9800, TvKouameActivity.class);
        def(liste, "videos", "🎬", "Vidéos de ma galerie", "Retrouver et lire les vidéos du téléphone", Ui.BLEU, Ui.VERT, VideosActivity.class);
        def(liste, "films", "🎞", "Films partagés", "Les films ajoutés par les utilisateurs", 0xFF0E5FCB, 0xFF12B5CB, FilmsFacebookActivity.class);
        def(liste, "jeux", "🎮", "JEUX", "Panda Dash, Super Kouamé, Serpent, Commando, Liquide et Combat Mortel", 0xFF8E24AA, 0xFFFF5252, JeuxActivity.class);
        def(liste, "accords", "🎼", "Accords & Musique", "Analyser un morceau, créer une chanson, séparer les voix", 0xFF6D4AFF, 0xFFC13FD6, AccordsHomeActivity.class);
        def(liste, "scolaire", "📚", "Exposés & exercices", "Préparer un exposé et réviser les matières scolaires", 0xFF167D8D, 0xFF36B37E, ScolaireActivity.class);
        def(liste, "pharmacie", "💊", "Pharmacies de garde", "Bénin : départements, communes, villes et villages", 0xFF1E9E5A, 0xFF16A085, PharmacieActivity.class);
        def(liste, "facebook", "📘", "Télécharger Facebook", "Une vidéo Facebook, en qualité standard ou HD", 0xFF1877F2, 0xFF5B3DF5, FacebookActivity.class);
        def(liste, "telechargements", "📥", "Téléchargements", "Facebook, galerie et liens, classés à part", 0xFF2E9E73, 0xFF9BCB3C, HorsLigneActivity.class);
        def(liste, "compilateur", "🛠", "Compiler un projet", "Zip Android → APK à télécharger", 0xFF16A085, 0xFF2D8CFF, CompilateurActivity.class);
        def(liste, "analyse", "🛡", "Analyse du téléphone", "Fichiers malveillants et cachés", 0xFFC0392B, 0xFFE67E22, AnalyseActivity.class);
        def(liste, "sms", "💬", "Messages SMS", "Lire vos SMS reçus et envoyés", 0xFF2980B9, 0xFF8E44AD, SmsActivity.class);
        if (Compte.admin()) def(liste, "admin", "👑", "Administration", "Comptes, en ligne, téléchargements, liens", 0xFF8E44AD, 0xFFE91E63, AdminActivity.class);
        def(liste, "parametres", "⚙", "Paramètres", "Compte, code PIN et empreinte digitale", 0xFF6C7A89, 0xFF34495E, ParametresActivity.class);

        liste = appliquerConfig(liste, cfg);
        afficherGroupes(liste);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(root);
        animations.add(Ui.fondAnime(sv));
        setContentView(sv);
        afficherAnnonce(cfg);
    }

    /** Affiche (ou cache) le bandeau « Nouvelle mise à jour » selon la dernière réponse de la base. */
    private void afficherBandeauMaj() {
        if (bandeauMaj == null) return;
        JSONObject r = MiseAJour.derniere;
        if (r != null && MiseAJour.disponible(this, r)) {
            bandeauMaj.setText("🆕 " + MiseAJour.resume(r) + "\nTouchez ici pour mettre à jour");
            bandeauMaj.setVisibility(View.VISIBLE);
        } else {
            bandeauMaj.setVisibility(View.GONE);
        }
    }

    private void def(List<T> liste, String id, String emoji, String titre, String desc, int c1, int c2, Class<?> cible) {
        T t = new T();
        t.id = id;
        t.emoji = emoji;
        t.titre = titre;
        t.desc = desc;
        t.c1 = c1;
        t.c2 = c2;
        t.cible = cible;
        liste.add(t);
        ecrans.put(id, cible);
    }

    private static int couleur(String s, int defaut) {
        try {
            return s == null || s.isEmpty() ? defaut : Color.parseColor(s.trim());
        } catch (Exception e) {
            return defaut;
        }
    }

    private static T trouver(List<T> liste, String id) {
        for (T t : liste) if (t.id.equals(id)) return t;
        return null;
    }

    /** Applique le fichier de configuration de la base : modifier, masquer, ajouter et ordonner les tuiles. */
    private List<T> appliquerConfig(List<T> liste, JSONObject cfg) {
        if (cfg == null) return liste;
        JSONArray arr = cfg.optJSONArray("tuiles");
        for (int i = 0; arr != null && i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("id", "").trim();
            if (id.isEmpty()) continue;
            T t = trouver(liste, id);
            if (t == null) {
                t = new T();
                t.id = id;
                t.emoji = "⭐";
                t.titre = id;
                t.desc = "";
                t.c1 = Ui.VIOLET;
                t.c2 = 0xFF3A7BFF;
                liste.add(t);
            }
            boolean protege = id.equals("admin") || id.equals("parametres");   // jamais masqués : on ne peut pas se bloquer l'accès
            if (o.has("masquer") && !protege) t.visible = !o.optBoolean("masquer");
            if (o.optBoolean("admin_seulement") && !Compte.admin()) t.visible = false;
            if (o.has("emoji")) t.emoji = o.optString("emoji", t.emoji);
            if (o.has("titre")) t.titre = o.optString("titre", t.titre);
            if (o.has("desc")) t.desc = o.optString("desc", t.desc);
            if (o.has("c1")) t.c1 = couleur(o.optString("c1"), t.c1);
            if (o.has("c2")) t.c2 = couleur(o.optString("c2"), t.c2);
            if (o.has("action")) t.action = o.optString("action", "");
        }
        JSONArray ordre = cfg.optJSONArray("ordre");
        if (ordre == null) return liste;
        List<T> res = new ArrayList<>();
        for (int i = 0; i < ordre.length(); i++) {
            T t = trouver(liste, ordre.optString(i, ""));
            if (t != null && !res.contains(t)) res.add(t);
        }
        for (T t : liste) if (!res.contains(t)) res.add(t);
        return res;
    }

    /** Annonce : fenêtre affichée une seule fois par identifiant. */
    private void afficherAnnonce(JSONObject cfg) {
        JSONObject a = cfg == null ? null : cfg.optJSONObject("annonce");
        if (a == null) return;
        String id = a.optString("id", "").trim();
        String msg = a.optString("message", "").trim();
        if (id.isEmpty() || msg.isEmpty() || id.equals(Config.annonceVue(this))) return;
        Config.annonceVue(this, id);
        new AlertDialog.Builder(this)
                .setTitle(a.optString("titre", "Annonce"))
                .setMessage(msg)
                .setPositiveButton(a.optString("bouton", "OK"), null)
                .show();
    }

    private void ouvrir(T t) {
        String a = t.action == null ? "" : t.action.trim();
        try {
            if (a.startsWith("page:")) {
                startActivity(new Intent(this, PageWebActivity.class).putExtra("id", a.substring(5).trim()));
            } else if (a.startsWith("url:")) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(a.substring(4).trim())));
            } else if (a.startsWith("ecran:")) {
                Class<?> k = ecrans.get(a.substring(6).trim());
                if (k != null) startActivity(new Intent(this, k));
            } else if (t.cible != null) {
                startActivity(new Intent(this, t.cible));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Compte.connecte()) return;
        Dossiers.creer(this);     // crée TvKouame/Telechargement si l'autorisation vient d'être donnée
        new Thread(() -> {
            Supabase.battement();
            Compte.verifierAdmin();
            // Mise à jour automatique : si le fichier de la base a changé, l'accueil se reconstruit tout seul.
            boolean nouveau = Config.rafraichir(this);
            Paquets.synchroniser(this);   // zips de contenu : téléchargés / remplacés s'il y a du nouveau
            if (nouveau) runOnUiThread(() -> {
                if (!isFinishing()) recreate();
            });
            // Nouvelle version de l'application (APK dans la base) : bandeau + fenêtre « Mettre à jour » avec la date de sortie.
            final JSONObject maj = Supabase.majDerniere();
            MiseAJour.derniere = maj;
            if (maj != null && maj.optBoolean("ok") && !MiseAJour.disponible(this, maj)) MiseAJour.nettoyerApkTelecharge(this);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                afficherBandeauMaj();
                if (!nouveau && MiseAJour.disponible(this, maj)) MiseAJour.verifierAvecReponse(this, maj);
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        for (Animator a : animations) a.cancel();
        animations.clear();
        super.onDestroy();
    }

    private void afficherGroupes(List<T> liste) {
        LinkedHashMap<String, List<T>> groupes = new LinkedHashMap<>();
        for (T t : liste) {
            if (!t.visible) continue;
            String groupe;
            switch (t.id) {
                case "radio":
                case "tv":
                case "tvkouame":
                case "films":
                case "videos":
                    groupe = "REGARDER";
                    break;
                case "jeux":
                    groupe = "JOUER";
                    break;
                case "accords":
                case "scolaire":
                    groupe = "CRÉER & APPRENDRE";
                    break;
                case "pharmacie":
                    groupe = "SERVICES";
                    break;
                case "facebook":
                case "telechargements":
                    groupe = "TÉLÉCHARGER & FICHIERS";
                    break;
                case "compilateur":
                case "analyse":
                case "sms":
                case "admin":
                case "parametres":
                    groupe = "OUTILS";
                    break;
                default:
                    groupe = "AUTRES";
                    break;
            }
            List<T> items = groupes.get(groupe);
            if (items == null) {
                items = new ArrayList<>();
                groupes.put(groupe, items);
            }
            items.add(t);
        }

        int largeur = getResources().getConfiguration().screenWidthDp;
        int colonnes = largeur >= 600 ? 3 : largeur < 360 ? 1 : 2;
        int indexAnimation = 1;
        for (Map.Entry<String, List<T>> entree : groupes.entrySet()) {
            List<T> items = entree.getValue();
            if (items.isEmpty()) continue;

            LinearLayout titreGroupe = new LinearLayout(this);
            titreGroupe.setGravity(Gravity.CENTER_VERTICAL);
            View accent = new View(this);
            accent.setBackground(Ui.rond(Ui.JAUNE, 3, this));
            titreGroupe.addView(accent, new LinearLayout.LayoutParams(Ui.dp(this, 4), Ui.dp(this, 20)));
            TextView nomGroupe = Ui.texte(this, entree.getKey(), 14, Ui.TXT, true);
            nomGroupe.setLetterSpacing(0.04f);
            titreGroupe.addView(nomGroupe, Ui.lp(this, 0, -2, 1f, 0, 9));
            TextView nombre = Ui.texte(this, items.size() + (items.size() == 1 ? " option" : " options"),
                    11, Ui.TXT2, false);
            titreGroupe.addView(nombre);
            mosaique.addView(titreGroupe, Ui.lp(this, -1, -2, 0f, 16, 0));

            for (int debut = 0; debut < items.size(); debut += colonnes) {
                int dansLigne = Math.min(colonnes, items.size() - debut);
                LinearLayout rangee = new LinearLayout(this);
                rangee.setOrientation(LinearLayout.HORIZONTAL);
                mosaique.addView(rangee, Ui.lp(this, -1, -2, 0f, 3, 0));
                for (int j = 0; j < dansLigne; j++) {
                    T t = items.get(debut + j);
                    View carte = tuile(t, indexAnimation++);
                    int marge = Ui.dp(this, 5);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            dansLigne == 1 ? -1 : 0, Ui.dp(this, colonnes == 1 ? 132 : 164),
                            dansLigne == 1 ? 0f : 1f);
                    lp.setMargins(marge, marge, marge, marge);
                    rangee.addView(carte, lp);
                }
            }
        }
    }

    /** Tuile lisible et tactile, avec une couleur d'accent propre à chaque destination. */
    private View tuile(final T t, int index) {
        LinearLayout carte = new LinearLayout(this);
        carte.setOrientation(LinearLayout.VERTICAL);
        carte.setGravity(Gravity.CENTER_VERTICAL);
        carte.setBackground(Ui.onde(Ui.assombrir(t.c1, 0.43f), Ui.assombrir(t.c2, 0.43f), 20, this));
        carte.setElevation(Ui.dp(this, 2));
        int q = Ui.dp(this, 14);
        carte.setPadding(q, q, q, q);
        carte.setClickable(true);
        carte.setFocusable(true);
        carte.setMinimumHeight(Ui.dp(this, 120));
        carte.setContentDescription(t.titre + ". " + t.desc + ". Ouvrir.");
        carte.setOnClickListener(v -> ouvrir(t));
        Ui.pression(carte);

        TextView e = Ui.texte(this, t.emoji, 31, Ui.TXT, false);
        carte.addView(e);

        TextView ti = Ui.texte(this, t.titre, 15, Ui.TXT, true);
        ti.setMaxLines(2);
        ti.setEllipsize(android.text.TextUtils.TruncateAt.END);
        carte.addView(ti, Ui.lp(this, -1, -2, 0f, 4, 0));

        TextView d = Ui.texte(this, t.desc, 11, 0xFFD8E0E7, false);
        d.setMaxLines(2);
        d.setEllipsize(android.text.TextUtils.TruncateAt.END);
        carte.addView(d, Ui.lp(this, -1, -2, 0f, 3, 0));

        TextView action = Ui.texte(this, "OUVRIR  →", 10, Ui.eclaircir(t.c1, 0.22f), true);
        carte.addView(action, Ui.lp(this, -1, -2, 0f, 7, 0));
        Ui.entree(carte, index);
        return carte;
    }
}
