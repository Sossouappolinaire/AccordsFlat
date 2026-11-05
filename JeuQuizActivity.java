package bj.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Questions pour un champion : quiz de culture générale avec 4 réponses lues à voix haute (synthèse vocale du téléphone).
 * On peut jouer seul (10 questions, 20 secondes chacune) ou en ligne avec d'autres joueurs (salon de 2 à 10 joueurs,
 * mêmes questions pour tous, points comptés par le serveur : 10 par bonne réponse + jusqu'à 10 de bonus de rapidité).
 * Les textes des questions sont dans QuizBanque ; la base (section22_quiz.sql) garde les bonnes réponses du jeu en ligne.
 */
public class JeuQuizActivity extends Activity {

    private static final int DUREE_SOLO = 20, NB_SOLO = 10;
    private static final int GRIS = 0xFF2A3F55, VERT_OK = 0xFF2E7D32, ROUGE = 0xFFC62828, SELECT = 0xFF1E88E5;
    private static final int M_MENU = 0, M_SOLO = 1, M_LOBBY = 2, M_SALLE = 3, M_LIGNE = 4, M_FIN = 5;

    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout page;
    private ScrollView sv;
    private int mode = M_MENU;
    private int generation = 0;
    private boolean vivant = true;

    // voix
    private TextToSpeech tts;
    private boolean ttsPret = false, ttsAvertie = false, son = true;

    // question affichée
    private TextView tvEntete, tvScore, tvMinuteur, tvPhase, tvRetour, tvClassement, tvJoueurs, tvCompte, tvGains;
    private Button btnRetrait;
    private int gainsPts;
    private ProgressBar barre;
    private final Button[] btn = new Button[4];
    private LinearLayout listeSalons;
    private int idCourant;
    private boolean repondu;
    private long debutQ;
    private final Runnable tick = this::tic;
    private final Runnable poll = this::pollSalon;
    private final Runnable pollLobby = this::majLobby;

    // solo
    private int[] idsSolo;
    private int iSolo, scoreSolo;

    // en ligne
    private String salonId, codeSalon;
    private boolean suisHote;
    private int gSalon = 0, echecs = 0;
    private JSONObject et;
    private long decalage;
    private int numAffiche = -1, bonneLigne = -1, choixLigne = -1, scoreLigne;
    private boolean compteAffiche, reveleDemande;

    // ------------------------------------------------------------------ cycle de vie

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.fenetre(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        son = getSharedPreferences("quiz", MODE_PRIVATE).getBoolean("son", true);

        tts = new TextToSpeech(this, status -> {
            if (tts == null) return;
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.FRANCE);
                ttsPret = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                if (ttsPret) tts.setSpeechRate(0.95f);
                else avertirVoix("La voix française n'est pas installée sur ce téléphone (Réglages > Synthèse vocale).");
            } else {
                avertirVoix("La lecture à voix haute n'est pas disponible sur ce téléphone.");
            }
        });

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        page.setPadding(p, p, p, p);
        sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackground(Ui.fond());
        sv.addView(page);
        setContentView(sv);
        afficherMenu();
    }

    @Override
    protected void onDestroy() {
        vivant = false;
        h.removeCallbacksAndMessages(null);
        quitterSalon();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        arreterVoix();
    }

    @Override
    public void onBackPressed() {
        if (mode == M_MENU) { super.onBackPressed(); return; }
        if (mode == M_SOLO || mode == M_LIGNE) {
            new AlertDialog.Builder(this)
                    .setTitle("Quitter la partie ?")
                    .setNegativeButton("Continuer", null)
                    .setPositiveButton("Quitter", (d, w) -> { quitterSalon(); afficherMenu(); })
                    .show();
            return;
        }
        if (mode == M_SALLE) { quitterSalon(); afficherLobby(); return; }
        quitterSalon();
        afficherMenu();
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    private void avertirVoix(String m) {
        if (ttsAvertie) return;
        ttsAvertie = true;
        runOnUiThread(() -> toast(m));
    }

    // ------------------------------------------------------------------ voix

    private void parler(String texte) {
        if (!son || !ttsPret || tts == null) return;
        tts.speak(texte, TextToSpeech.QUEUE_FLUSH, null, "quiz");
    }

    private void arreterVoix() {
        if (tts != null) tts.stop();
    }

    private void lireQuestion(int id) {
        if (!QuizBanque.existe(id)) return;
        String[] r = QuizBanque.reponses(id);
        parler(QuizBanque.question(id) + " Réponse A : " + r[0] + ". Réponse B : " + r[1]
                + ". Réponse C : " + r[2] + ". Réponse D : " + r[3] + ".");
    }

    private String voixTexte() { return son ? "🔊 Voix activée" : "🔇 Voix coupée"; }

    private void basculerVoix() {
        son = !son;
        getSharedPreferences("quiz", MODE_PRIVATE).edit().putBoolean("son", son).apply();
        if (!son) arreterVoix();
    }

    // ------------------------------------------------------------------ écrans

    private void nouvelEcran(int m) {
        mode = m;
        generation++;
        h.removeCallbacks(tick);
        h.removeCallbacks(pollLobby);
        page.removeAllViews();
        sv.scrollTo(0, 0);
        tvEntete = tvScore = tvMinuteur = tvPhase = tvRetour = tvClassement = tvJoueurs = tvCompte = tvGains = null;
        btnRetrait = null;
        barre = null;
        listeSalons = null;
        for (int i = 0; i < 4; i++) btn[i] = null;
    }

    /** Lance une action plus tard, sauf si l'écran a changé entre-temps. */
    private void apres(long ms, Runnable r) {
        final int g = generation;
        h.postDelayed(() -> { if (vivant && g == generation) r.run(); }, ms);
    }

    private void retourMenu(String texte) {
        Button b = Ui.boutonContour(this, texte, Ui.TXT);
        b.setOnClickListener(v -> { quitterSalon(); afficherMenu(); });
        Ui.ajouter(page, b, 14);
    }

    private void afficherMenu() {
        nouvelEcran(M_MENU);
        arreterVoix();
        Ui.ajouter(page, Ui.texte(this, "🧠 Questions pour un champion", 26, Ui.JAUNE, true), 0);
        Ui.ajouter(page, Ui.texte(this, "Quiz de culture générale : chaque question et ses 4 réponses sont lues à voix haute.", 14, Ui.TXT2, false), 6);
        Ui.ajouter(page, Ui.texte(this, "🏆 Mon record : " + JeuxActivity.record(this, "quiz") + " points", 15, Ui.JAUNE, true), 10);

        if (Compte.connecte()) {
            tvGains = Ui.texte(this, "💰 Points gagnés : chargement…", 15, Ui.VERT, true);
            Ui.ajouter(page, tvGains, 8);
            btnRetrait = Ui.bouton(this, "💸  Retirer mes points gagnés", Ui.ACCENT);
            btnRetrait.setVisibility(View.GONE);
            btnRetrait.setOnClickListener(v -> formulaireRetrait());
            Ui.ajouter(page, btnRetrait, 8);
            chargerGains();
        }

        Button solo = Ui.bouton(this, "👤  Jouer seul", Ui.VERT);
        solo.setOnClickListener(v -> lancerSolo());
        Ui.ajouter(page, solo, 18);

        Button ligne = Ui.bouton(this, "🌍  Jouer en ligne avec d'autres joueurs", Ui.VIOLET);
        ligne.setOnClickListener(v -> afficherLobby());
        Ui.ajouter(page, ligne, 10);

        final Button voix = Ui.boutonContour(this, voixTexte(), Ui.TXT);
        voix.setOnClickListener(v -> { basculerVoix(); voix.setText(voixTexte()); });
        Ui.ajouter(page, voix, 10);

        Ui.ajouter(page, Ui.texte(this, "Seul : 10 questions, 20 secondes chacune, jusqu'à 20 points par bonne réponse (bonus de rapidité). Aucun gain à gagner, c'est juste pour s'entraîner.\n"
                + "En ligne : 2 à 10 joueurs, les mêmes questions pour tous, classement en direct.\n\n"
                + "En ligne : le premier qui clique sur la bonne réponse gagne le point de la question. "
                + "Après 10 questions, le meilleur score gagne des points gagnés (1 point = 1 F), différents du score du jeu. "
                + "Retrait sur demande, confirmé par l'administrateur.", 12, Ui.TXT2, false), 18);
    }

    // ------------------------------------------------------------------ points gagnés et retrait

    private void chargerGains() {
        final int g = generation;
        new Thread(() -> {
            final JSONObject r = QuizReseau.mesGains();
            runOnUiThread(() -> {
                if (!vivant || g != generation || tvGains == null) return;
                if (r == null || !r.optBoolean("ok")) {
                    tvGains.setText("💰 Points gagnés : indisponible (connexion)");
                    return;
                }
                int pts = r.optInt("points");
                gainsPts = pts;
                StringBuilder sb = new StringBuilder("💰 Points gagnés : " + pts + " (= " + pts + " F)   •   🏅 " + r.optInt("victoires") + " victoire(s)");
                JSONObject att = r.optJSONObject("attente");
                if (att != null) {
                    sb.append("\n⏳ Demande de retrait de ").append(att.optInt("montant")).append(" F en attente de confirmation de l'administrateur.");
                } else {
                    String d = r.optString("dernier", "");
                    if (d.startsWith("confirme:")) sb.append("\n✅ Dernier retrait confirmé : ").append(d.substring(9)).append(" F");
                    else if (d.startsWith("refuse:")) sb.append("\n❌ Votre dernière demande a été refusée par l'administrateur.");
                }
                tvGains.setText(sb.toString());
                if (btnRetrait != null) btnRetrait.setVisibility(pts > 0 && att == null ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    private void formulaireRetrait() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        int pd = Ui.dp(this, 20);
        f.setPadding(pd, pd / 2, pd, 0);
        final EditText pays = Ui.champ(this, "Pays");
        final EditText num = Ui.champ(this, "Numéro de téléphone (avec l'indicatif)");
        final EditText nom = Ui.champ(this, "Nom");
        final EditText prenom = Ui.champ(this, "Prénom");
        pays.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nom.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        prenom.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        num.setInputType(InputType.TYPE_CLASS_PHONE);
        Ui.ajouter(f, pays, 8);
        Ui.ajouter(f, num, 8);
        Ui.ajouter(f, nom, 8);
        Ui.ajouter(f, prenom, 8);
        new AlertDialog.Builder(this)
                .setTitle("Retirer " + gainsPts + " points (" + gainsPts + " F)")
                .setMessage("Ces informations sont envoyées à l'administrateur, qui vous paiera puis confirmera la demande.")
                .setView(f)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Envoyer", (d, w) -> envoyerRetrait(pays.getText().toString(), num.getText().toString(),
                        nom.getText().toString(), prenom.getText().toString()))
                .show();
    }

    private void envoyerRetrait(final String pays, final String num, final String nom, final String prenom) {
        new Thread(() -> {
            final JSONObject r = QuizReseau.demanderRetrait(pays, num, nom, prenom);
            runOnUiThread(() -> {
                if (!vivant) return;
                if (r == null) toast("Demande non envoyée. Vérifiez votre connexion.");
                else if (!r.optBoolean("ok")) toast(r.optString("erreur", "Erreur"));
                else toast("✅ Demande envoyée à l'administrateur : " + r.optInt("montant") + " F");
                if (mode == M_MENU) chargerGains();
            });
        }).start();
    }

    // ------------------------------------------------------------------ affichage d'une question (solo et en ligne)

    private void colorer(Button b, int couleur) {
        b.setBackground(Ui.onde(Ui.eclaircir(couleur, 0.22f), Ui.assombrir(couleur, 0.18f), 14, this));
    }

    private void construireQuestion(int id, String entete, boolean ligne) {
        page.removeAllViews();
        sv.scrollTo(0, 0);
        tvEntete = Ui.texte(this, entete, 13, Ui.TXT2, true);
        tvScore = Ui.texte(this, "", 15, Ui.JAUNE, true);
        LinearLayout haut = new LinearLayout(this);
        haut.setGravity(Gravity.CENTER_VERTICAL);
        haut.addView(tvEntete, new LinearLayout.LayoutParams(0, -2, 1f));
        haut.addView(tvScore);
        Ui.ajouter(page, haut, 0);

        tvMinuteur = Ui.texte(this, "", 22, Ui.TXT, true);
        tvMinuteur.setGravity(Gravity.CENTER);
        Ui.ajouter(page, tvMinuteur, 10);
        barre = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barre.setMax(1000);
        barre.setProgress(1000);
        barre.setProgressTintList(ColorStateList.valueOf(Ui.VERT));
        Ui.ajouter(page, barre, 4);

        TextView q = Ui.texte(this, QuizBanque.question(id), 21, Ui.TXT, true);
        q.setGravity(Gravity.CENTER);
        Ui.ajouter(page, q, 18);

        String[] rep = QuizBanque.reponses(id);
        String[] lettres = {"A", "B", "C", "D"};
        for (int i = 0; i < 4; i++) {
            final int choix = i;
            Button b = Ui.bouton(this, lettres[i] + "    " + rep[i], GRIS);
            b.setTextSize(17);
            b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            b.setOnClickListener(v -> choisir(choix));
            btn[i] = b;
            Ui.ajouter(page, b, 10);
        }

        tvRetour = Ui.texte(this, "", 16, Ui.TXT, true);
        tvRetour.setGravity(Gravity.CENTER);
        Ui.ajouter(page, tvRetour, 14);
        tvPhase = Ui.texte(this, "", 13, Ui.TXT2, false);
        tvPhase.setGravity(Gravity.CENTER);
        Ui.ajouter(page, tvPhase, 4);

        LinearLayout voix = new LinearLayout(this);
        Button ecouter = Ui.boutonContour(this, "🔊 Réécouter", Ui.TXT);
        ecouter.setOnClickListener(v -> lireQuestion(idCourant));
        final Button couper = Ui.boutonContour(this, son ? "🔇 Couper la voix" : "🔊 Activer la voix", Ui.TXT);
        couper.setOnClickListener(v -> {
            basculerVoix();
            couper.setText(son ? "🔇 Couper la voix" : "🔊 Activer la voix");
            if (son) lireQuestion(idCourant);
        });
        voix.addView(ecouter, new LinearLayout.LayoutParams(0, -2, 1f));
        voix.addView(couper, new LinearLayout.LayoutParams(0, -2, 1f));
        Ui.ajouter(page, voix, 10);

        if (ligne) {
            Ui.ajouter(page, Ui.texte(this, "📊 Classement en direct", 15, Ui.JAUNE, true), 16);
            tvClassement = Ui.texte(this, "", 14, Ui.TXT, false);
            Ui.ajouter(page, tvClassement, 4);
        }
    }

    private void choisir(int c) {
        if (repondu) return;
        if (mode == M_SOLO) repondreSolo(c);
        else if (mode == M_LIGNE) repondreLigne(c);
    }

    private void montrerCorrection(int bonne, int choix) {
        for (int i = 0; i < 4; i++) {
            if (btn[i] == null) continue;
            if (i == bonne) colorer(btn[i], VERT_OK);
            else if (i == choix) colorer(btn[i], ROUGE);
        }
    }

    private String reponseTexte(int id, int index) {
        if (!QuizBanque.existe(id) || index < 0 || index > 3) return "";
        return QuizBanque.reponses(id)[index];
    }

    private void majMinuteur(double reste, int duree) {
        if (tvMinuteur == null || barre == null) return;
        boolean court = reste <= 5;
        tvMinuteur.setText("⏱ " + (int) Math.ceil(reste) + " s");
        tvMinuteur.setTextColor(court ? 0xFFFF5252 : Ui.TXT);
        barre.setProgress((int) (1000 * Math.max(0, reste) / duree));
        barre.setProgressTintList(ColorStateList.valueOf(court ? ROUGE : Ui.VERT));
    }

    private void tic() {
        if (mode == M_SOLO) ticSolo();
        else if (mode == M_LIGNE) ticLigne();
    }

    // ------------------------------------------------------------------ solo

    private void lancerSolo() {
        nouvelEcran(M_SOLO);
        List<Integer> l = new ArrayList<>();
        for (int i = 1; i <= QuizBanque.taille(); i++) l.add(i);
        Collections.shuffle(l);
        idsSolo = new int[NB_SOLO];
        for (int i = 0; i < NB_SOLO; i++) idsSolo[i] = l.get(i);
        iSolo = 0;
        scoreSolo = 0;
        questionSolo();
    }

    private void questionSolo() {
        if (iSolo >= NB_SOLO) { finSolo(); return; }
        idCourant = idsSolo[iSolo];
        repondu = false;
        construireQuestion(idCourant, "Question " + (iSolo + 1) + "/" + NB_SOLO + "  •  " + QuizBanque.categorie(idCourant), false);
        tvScore.setText("Score : " + scoreSolo);
        majMinuteur(DUREE_SOLO, DUREE_SOLO);
        debutQ = SystemClock.elapsedRealtime();
        lireQuestion(idCourant);
        h.removeCallbacks(tick);
        h.postDelayed(tick, 200);
    }

    private void ticSolo() {
        if (repondu || tvMinuteur == null) return;
        double reste = DUREE_SOLO - (SystemClock.elapsedRealtime() - debutQ) / 1000.0;
        if (reste <= 0) { repondreSolo(-1); return; }
        majMinuteur(reste, DUREE_SOLO);
        h.postDelayed(tick, 200);
    }

    private void repondreSolo(int choix) {
        if (repondu) return;
        repondu = true;
        h.removeCallbacks(tick);
        int bonne = QuizBanque.bonne(idCourant);
        double reste = Math.max(0, DUREE_SOLO - (SystemClock.elapsedRealtime() - debutQ) / 1000.0);
        int pts = choix == bonne ? 10 + (int) Math.floor(10 * reste / DUREE_SOLO) : 0;
        scoreSolo += pts;
        tvScore.setText("Score : " + scoreSolo);
        montrerCorrection(bonne, choix);
        String bonneTxt = reponseTexte(idCourant, bonne);
        if (choix == bonne) {
            tvRetour.setText("✅ Bonne réponse ! +" + pts + " points");
            parler("Bonne réponse !");
        } else if (choix < 0) {
            tvRetour.setText("⏱ Temps écoulé ! Bonne réponse : " + bonneTxt);
            parler("Temps écoulé. La bonne réponse était : " + bonneTxt);
        } else {
            tvRetour.setText("❌ Raté. Bonne réponse : " + bonneTxt);
            parler("Raté. La bonne réponse était : " + bonneTxt);
        }
        majMinuteur(0, DUREE_SOLO);
        apres(4000, () -> { iSolo++; questionSolo(); });
    }

    private void finSolo() {
        nouvelEcran(M_FIN);
        int total = scoreSolo;
        boolean record = total > JeuxActivity.record(this, "quiz");
        JeuxActivity.enregistrer(this, "quiz", total);
        Ui.ajouter(page, Ui.texte(this, "🏁 Partie terminée", 26, Ui.JAUNE, true), 0);
        TextView t = Ui.texte(this, total + " points", 44, Ui.TXT, true);
        t.setGravity(Gravity.CENTER);
        Ui.ajouter(page, t, 20);
        String msg = total >= 150 ? "🏆 Champion ! Quelle culture !" : total >= 100 ? "👏 Très bien joué !" : total >= 50 ? "👍 Pas mal, continuez !" : "💪 Courage, la prochaine sera la bonne !";
        TextView m = Ui.texte(this, msg, 16, Ui.TXT, true);
        m.setGravity(Gravity.CENTER);
        Ui.ajouter(page, m, 8);
        TextView r = Ui.texte(this, (record && total > 0 ? "🎉 Nouveau record ! " : "") + "🏆 Mon record : " + JeuxActivity.record(this, "quiz") + " points", 14, Ui.JAUNE, true);
        r.setGravity(Gravity.CENTER);
        Ui.ajouter(page, r, 8);
        Button encore = Ui.bouton(this, "🔁  Rejouer", Ui.VERT);
        encore.setOnClickListener(v -> lancerSolo());
        Ui.ajouter(page, encore, 22);
        retourMenu("Menu");
        parler("Partie terminée. Vous avez " + total + " points.");
    }

    // ------------------------------------------------------------------ en ligne : salons

    private void afficherLobby() {
        quitterSalon();
        nouvelEcran(M_LOBBY);
        arreterVoix();
        Ui.ajouter(page, Ui.texte(this, "🌍 Jouer en ligne", 26, Ui.JAUNE, true), 0);
        if (!Compte.connecte()) {
            Ui.ajouter(page, Ui.texte(this, "Connectez-vous à votre compte pour jouer avec les autres joueurs.", 15, Ui.TXT, false), 14);
            retourMenu("Retour");
            return;
        }
        if (!JeuxActivity.internet(this)) {
            Ui.ajouter(page, Ui.texte(this, "Pas d'internet. Vous pouvez jouer seul hors connexion.", 15, Ui.TXT, false), 14);
            retourMenu("Retour");
            return;
        }
        Ui.ajouter(page, Ui.texte(this, "Créez un salon ou rejoignez celui d'un autre joueur.", 14, Ui.TXT2, false), 6);

        Button creer = Ui.bouton(this, "➕  Créer un salon (10 questions)", Ui.VERT);
        creer.setOnClickListener(v -> creerSalon(v));
        Ui.ajouter(page, creer, 16);

        final EditText code = Ui.champ(this, "Code du salon (4 lettres)");
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        Ui.ajouter(page, code, 14);
        Button rej = Ui.bouton(this, "Rejoindre avec ce code", Ui.VIOLET);
        rej.setOnClickListener(v -> {
            String c = code.getText().toString().trim();
            if (c.isEmpty()) { toast("Entrez le code du salon"); return; }
            rejoindre(c, v);
        });
        Ui.ajouter(page, rej, 8);

        Ui.ajouter(page, Ui.texte(this, "Salons ouverts", 18, Ui.JAUNE, true), 20);
        listeSalons = new LinearLayout(this);
        listeSalons.setOrientation(LinearLayout.VERTICAL);
        Ui.ajouter(page, listeSalons, 6);
        Ui.ajouter(page, Ui.texte(this, "Recherche des salons…", 13, Ui.TXT2, false), 0);
        retourMenu("Retour");
        majLobby();
    }

    private void majLobby() {
        if (mode != M_LOBBY) return;
        final int g = generation;
        new Thread(() -> {
            final JSONArray a = QuizReseau.ouverts();
            runOnUiThread(() -> {
                if (!vivant || g != generation || listeSalons == null) return;
                remplirSalons(a);
                h.postDelayed(pollLobby, 3000);
            });
        }).start();
    }

    private void remplirSalons(JSONArray a) {
        listeSalons.removeAllViews();
        if (a == null) {
            Ui.ajouter(listeSalons, Ui.texte(this, "Impossible de lire les salons. Vérifiez votre connexion.", 13, Ui.TXT2, false), 4);
            return;
        }
        if (a.length() == 0) {
            Ui.ajouter(listeSalons, Ui.texte(this, "Aucun salon ouvert pour le moment. Créez-en un !", 13, Ui.TXT2, false), 4);
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            final String code = o.optString("code");
            LinearLayout carte = new LinearLayout(this);
            carte.setGravity(Gravity.CENTER_VERTICAL);
            carte.setBackground(Ui.rond(Ui.CARD, 14, this));
            int q = Ui.dp(this, 12);
            carte.setPadding(q, q, q, q);
            int n = o.optInt("joueurs");
            TextView t = Ui.texte(this, "🎲 " + o.optString("hote", "Joueur") + "\n" + n + " joueur" + (n > 1 ? "s" : "") + " • " + o.optInt("nb") + " questions", 14, Ui.TXT, true);
            carte.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            Button b = Ui.bouton(this, "Rejoindre", Ui.VIOLET);
            b.setOnClickListener(v -> rejoindre(code, v));
            carte.addView(b, new LinearLayout.LayoutParams(-2, -2));
            Ui.ajouter(listeSalons, carte, 8);
        }
    }

    private void creerSalon(final View v) {
        v.setEnabled(false);
        new Thread(() -> {
            final JSONObject r = QuizReseau.creer(10);
            runOnUiThread(() -> {
                if (!vivant) return;
                v.setEnabled(true);
                if (r == null) { toast("Impossible de créer le salon. Vérifiez votre connexion."); return; }
                if (!r.optBoolean("ok")) { toast(r.optString("erreur", "Erreur")); return; }
                entrerSalon(r.optString("salon"), r.optString("code"), true);
            });
        }).start();
    }

    private void rejoindre(final String code, final View v) {
        v.setEnabled(false);
        new Thread(() -> {
            final JSONObject r = QuizReseau.rejoindre(code);
            runOnUiThread(() -> {
                if (!vivant) return;
                v.setEnabled(true);
                if (r == null) { toast("Impossible de rejoindre. Vérifiez votre connexion."); return; }
                if (!r.optBoolean("ok")) { toast(r.optString("erreur", "Erreur")); return; }
                entrerSalon(r.optString("salon"), r.optString("code"), false);
            });
        }).start();
    }

    private void entrerSalon(String id, String code, boolean hote) {
        gSalon++;
        salonId = id;
        codeSalon = code;
        suisHote = hote;
        et = null;
        echecs = 0;
        numAffiche = -1;
        afficherSalle();
        h.removeCallbacks(poll);
        h.post(poll);
    }

    private void quitterSalon() {
        gSalon++;
        h.removeCallbacks(poll);
        final String id = salonId;
        salonId = null;
        suisHote = false;
        et = null;
        if (id != null) new Thread(() -> QuizReseau.quitter(id)).start();
    }

    private void afficherSalle() {
        nouvelEcran(M_SALLE);
        Ui.ajouter(page, Ui.texte(this, "🎲 Salon de jeu", 24, Ui.JAUNE, true), 0);
        Ui.ajouter(page, Ui.texte(this, "Code du salon", 13, Ui.TXT2, false), 12);
        TextView c = Ui.texte(this, codeSalon == null ? "" : codeSalon, 44, Ui.TXT, true);
        c.setGravity(Gravity.CENTER);
        Ui.ajouter(page, c, 0);
        Ui.ajouter(page, Ui.texte(this, "Donnez ce code à vos amis, ou attendez : les autres joueurs voient ce salon dans « Salons ouverts ».", 13, Ui.TXT2, false), 6);
        tvJoueurs = Ui.texte(this, "👥 Joueurs…", 16, Ui.TXT, true);
        Ui.ajouter(page, tvJoueurs, 18);
        if (suisHote) {
            Button go = Ui.bouton(this, "▶  Lancer la partie", Ui.VERT);
            go.setOnClickListener(v -> lancerPartie(v));
            Ui.ajouter(page, go, 20);
            Ui.ajouter(page, Ui.texte(this, "Il faut au moins 2 joueurs.", 12, Ui.TXT2, false), 4);
        } else {
            Ui.ajouter(page, Ui.texte(this, "⏳ En attente que l'hôte lance la partie…", 15, Ui.TXT, true), 20);
        }
        Button q = Ui.boutonContour(this, "Quitter le salon", Ui.TXT);
        q.setOnClickListener(v -> { quitterSalon(); afficherLobby(); });
        Ui.ajouter(page, q, 16);
        majSalle();
    }

    private void majSalle() {
        if (tvJoueurs == null || et == null) return;
        JSONArray j = et.optJSONArray("joueurs");
        StringBuilder sb = new StringBuilder("👥 Joueurs (" + (j == null ? 0 : j.length()) + "/10)");
        for (int i = 0; j != null && i < j.length(); i++) {
            JSONObject o = j.optJSONObject(i);
            if (o == null) continue;
            sb.append("\n• ").append(o.optString("nom", "Joueur"));
            if (o.optBoolean("moi")) sb.append("  (vous)");
        }
        tvJoueurs.setText(sb.toString());
    }

    private void lancerPartie(final View v) {
        final String id = salonId;
        if (id == null) return;
        v.setEnabled(false);
        new Thread(() -> {
            final JSONObject r = QuizReseau.demarrer(id);
            runOnUiThread(() -> {
                if (!vivant) return;
                v.setEnabled(true);
                if (r == null) toast("Impossible de lancer la partie. Vérifiez votre connexion.");
                else if (!r.optBoolean("ok")) toast(r.optString("erreur", "Erreur"));
            });
        }).start();
    }

    // ------------------------------------------------------------------ en ligne : suivi du salon

    private void pollSalon() {
        final String id = salonId;
        final int g = gSalon;
        if (id == null) return;
        new Thread(() -> {
            final JSONObject r = QuizReseau.etat(id);
            runOnUiThread(() -> {
                if (!vivant || g != gSalon) return;
                traiterEtat(r);
                if (salonId != null && g == gSalon) h.postDelayed(poll, mode == M_LIGNE ? 700 : 1500);
            });
        }).start();
    }

    private void traiterEtat(JSONObject r) {
        if (r == null) {
            if (++echecs >= 8) {
                toast("Connexion perdue.");
                quitterSalon();
                afficherLobby();
            }
            return;
        }
        if (!r.optBoolean("ok")) {
            toast(r.optString("erreur", "Le salon est fermé."));
            quitterSalon();
            afficherLobby();
            return;
        }
        echecs = 0;
        et = r;
        decalage = r.optLong("maintenant") - System.currentTimeMillis();
        majScoreMoi();
        String etat = r.optString("etat");
        if ("attente".equals(etat)) {
            if (mode == M_SALLE) majSalle();
            return;
        }
        if (mode == M_SALLE) {
            nouvelEcran(M_LIGNE);
            numAffiche = -1;
            compteAffiche = false;
            scoreLigne = 0;
            h.post(tick);
        } else if (mode == M_LIGNE) {
            majClassement();
        }
    }

    private static String texte(JSONObject o, String cle) {
        return o == null || o.isNull(cle) ? "" : o.optString(cle, "");
    }

    /** Avant le début : on annonce avec qui on joue et les règles (écran et voix), puis « Soyez prêts ». */
    private void afficherRegles() {
        compteAffiche = true;
        page.removeAllViews();
        tvMinuteur = null;
        barre = null;
        JSONArray j = et == null ? null : et.optJSONArray("joueurs");
        List<String> autres = new ArrayList<>();
        for (int i = 0; j != null && i < j.length(); i++) {
            JSONObject o = j.optJSONObject(i);
            if (o != null && !o.optBoolean("moi")) autres.add(o.optString("nom", "Joueur"));
        }
        String noms = autres.isEmpty() ? "d'autres joueurs" : android.text.TextUtils.join(", ", autres);
        int gain = et == null ? 25 : et.optInt("gain", 25);
        Ui.ajouter(page, Ui.texte(this, "🧠 Vous voulez jouer avec", 18, Ui.TXT2, true), 24);
        TextView n = Ui.texte(this, noms + " ?", 24, Ui.JAUNE, true);
        n.setGravity(Gravity.CENTER);
        Ui.ajouter(page, n, 6);
        Ui.ajouter(page, Ui.texte(this, "📜 Les règles", 18, Ui.TXT, true), 22);
        Ui.ajouter(page, Ui.texte(this, "• Le premier qui clique sur la bonne réponse gagne le point de la question.\n"
                + "• Si deux joueurs choisissent la même réponse, celui qui a cliqué le premier l'emporte.\n"
                + "• Une mauvaise réponse ne permet plus de répondre à la question.\n"
                + "• Après 10 questions, le joueur qui a le plus de points gagne " + gain + " points gagnés.", 15, Ui.TXT, false), 8);
        Ui.ajouter(page, Ui.texte(this, "Soyez prêts !", 22, Ui.VERT, true), 20);
        tvCompte = Ui.texte(this, "", 64, Ui.TXT, true);
        tvCompte.setGravity(Gravity.CENTER);
        Ui.ajouter(page, tvCompte, 10);
        parler("Vous voulez jouer avec " + noms + ". Voici les règles. Le premier qui clique sur la bonne réponse gagne le point. "
                + "Si deux joueurs choisissent la même réponse, le plus rapide l'emporte. "
                + "Après dix questions, le meilleur score gagne " + gain + " points gagnés. Soyez prêts !");
    }

    private void afficherQuestionLigne(int num) {
        numAffiche = num;
        compteAffiche = false;
        repondu = false;
        choixLigne = -1;
        bonneLigne = -1;
        reveleDemande = false;
        JSONArray qs = et.optJSONArray("questions");
        int id = qs == null ? 0 : qs.optInt(num, 0);
        if (!QuizBanque.existe(id)) {
            idCourant = 0;
            page.removeAllViews();
            tvMinuteur = null;
            barre = null;
            Ui.ajouter(page, Ui.texte(this, "Une question de cette partie n'existe pas dans votre version de l'appli. Mettez l'appli à jour.", 15, Ui.TXT, true), 30);
            return;
        }
        idCourant = id;
        construireQuestion(id, "Question " + (num + 1) + "/" + et.optInt("nb", 10) + "  •  " + QuizBanque.categorie(id), true);
        tvScore.setText("Score : " + scoreLigne);
        majClassement();
        lireQuestion(id);
    }

    /** Le serveur décide de tout (question en cours, gagnant du point) ; l'appli affiche et fait défiler le temps. */
    private void ticLigne() {
        if (et == null) { h.postDelayed(tick, 250); return; }
        if ("fini".equals(et.optString("etat"))) { finLigne(); return; }
        long serveur = System.currentTimeMillis() + decalage;
        long debut = et.optLong("debut", 0);
        int duree = et.optInt("duree", 25), pause = et.optInt("pause", 4), nb = et.optInt("nb", 10);
        if (debut == 0) { h.postDelayed(tick, 250); return; }
        double t = (serveur - debut) / 1000.0;
        if (t < 0) {
            if (!compteAffiche || tvCompte == null) afficherRegles();
            tvCompte.setText(String.valueOf((int) Math.ceil(-t)));
            h.postDelayed(tick, 250);
            return;
        }
        int num = et.optInt("num", 0);
        if (num != numAffiche) afficherQuestionLigne(num);
        if (tvMinuteur != null) {
            long qd = et.optLong("q_debut", debut);
            boolean fermee = !et.isNull("q_fin");
            if (!fermee) {
                double reste = duree - (serveur - qd) / 1000.0;
                majMinuteur(Math.max(0, reste), duree);
                tvPhase.setText(repondu ? "Réponse envoyée." : "Soyez le premier à cliquer sur la bonne réponse !");
            } else {
                majMinuteur(0, duree);
                repondu = true;
                if (!reveleDemande) cloturerLigne(num);
                double suiv = (et.optLong("q_fin") + pause * 1000L - serveur) / 1000.0;
                int sec = (int) Math.max(0, Math.ceil(suiv));
                tvPhase.setText(num + 1 >= nb ? "Résultats dans " + sec + " s" : "Question suivante dans " + sec + " s");
            }
        }
        h.postDelayed(tick, 250);
    }

    private void repondreLigne(final int choix) {
        if (repondu || salonId == null || btn[choix] == null) return;
        repondu = true;
        choixLigne = choix;
        final int num = numAffiche;
        final String id = salonId;
        colorer(btn[choix], SELECT);
        tvRetour.setText("⏳ Réponse envoyée…");
        arreterVoix();
        new Thread(() -> {
            final JSONObject r = QuizReseau.repondre(id, num, choix);
            runOnUiThread(() -> {
                if (!vivant || num != numAffiche || mode != M_LIGNE || tvRetour == null) return;
                if (r != null && r.optBoolean("ok")) {
                    if (r.optBoolean("juste")) {
                        bonneLigne = r.optInt("bonne");
                        scoreLigne = r.optInt("score");
                        tvScore.setText("Score : " + scoreLigne);
                        montrerCorrection(bonneLigne, choix);
                        tvRetour.setText("⚡ Vous avez été le plus rapide ! +1 point");
                        parler("Bravo, vous gagnez le point !");
                    } else {
                        colorer(btn[choix], ROUGE);
                        tvRetour.setText("❌ Mauvaise réponse. Vous ne pouvez plus répondre à cette question.");
                        parler("Raté.");
                    }
                } else if (r != null && r.optBoolean("tard")) {
                    String g = texte(r, "gagnant");
                    tvRetour.setText(g.isEmpty() ? "⏱ Temps écoulé." : "⚡ " + g + " a été plus rapide.");
                } else {
                    tvRetour.setText("⚠ " + (r == null ? "Réponse non envoyée (connexion)." : r.optString("erreur", "Erreur")));
                }
            });
        }).start();
    }

    /** La question est fermée (point gagné, tous ont répondu ou temps écoulé) : annonce du gagnant et bonne réponse. */
    private void cloturerLigne(final int num) {
        final String id = salonId;
        if (id == null) return;
        reveleDemande = true;
        final String g = texte(et, "gagnant");
        final boolean moi = et.optBoolean("gagnant_moi");
        new Thread(() -> {
            final JSONObject r = QuizReseau.revele(id, num);
            runOnUiThread(() -> {
                if (!vivant || num != numAffiche || mode != M_LIGNE) return;
                if (r != null && r.optBoolean("ok")) {
                    bonneLigne = r.optInt("bonne");
                    montrerCorrection(bonneLigne, choixLigne >= 0 && choixLigne != bonneLigne ? choixLigne : -1);
                    String bonneTxt = reponseTexte(idCourant, bonneLigne);
                    if (tvRetour != null) {
                        if (moi) tvRetour.setText("⚡ Vous avez été le plus rapide ! +1 point");
                        else if (!g.isEmpty()) tvRetour.setText("⚡ " + g + " gagne le point. Bonne réponse : " + bonneTxt);
                        else tvRetour.setText("⏱ Personne n'a trouvé. Bonne réponse : " + bonneTxt);
                    }
                    if (!moi) parler(!g.isEmpty() ? g + " gagne le point. La bonne réponse était : " + bonneTxt
                            : "Personne n'a trouvé. La bonne réponse était : " + bonneTxt);
                } else {
                    apres(700, () -> reveleDemande = false);
                }
            });
        }).start();
    }

    private void majScoreMoi() {
        JSONArray j = et == null ? null : et.optJSONArray("joueurs");
        for (int i = 0; j != null && i < j.length(); i++) {
            JSONObject o = j.optJSONObject(i);
            if (o != null && o.optBoolean("moi")) { scoreLigne = o.optInt("score"); break; }
        }
        if (tvScore != null && mode == M_LIGNE) tvScore.setText("Score : " + scoreLigne);
    }

    private String lignesClassement() {
        if (et == null) return "";
        JSONArray j = et.optJSONArray("joueurs");
        String[] med = {"🥇", "🥈", "🥉"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; j != null && i < j.length(); i++) {
            JSONObject o = j.optJSONObject(i);
            if (o == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(i < 3 ? med[i] : (i + 1) + ".").append(' ').append(o.optString("nom", "Joueur"))
                    .append(" — ").append(o.optInt("score")).append(" pts");
            if (o.optBoolean("moi")) sb.append("   ← vous");
        }
        return sb.toString();
    }

    /** Message sur les points gagnés (1er d'une partie en ligne de 10 questions). */
    private String texteVictoire() {
        if (et == null) return "";
        JSONObject v = et.optJSONObject("victoire");
        if (v == null) return "";
        int pts = v.optInt("points");
        if (pts > 0) {
            return v.optBoolean("moi")
                    ? "🎉 Vous gagnez " + pts + " points gagnés (" + pts + " F) ! Retirez-les depuis le menu du jeu."
                    : "🏅 " + v.optString("nom", "Un joueur") + " gagne " + pts + " points gagnés.";
        }
        return "ℹ " + v.optString("motif", "");
    }

    private void majClassement() {
        if (tvClassement != null) tvClassement.setText(lignesClassement());
    }

    private void finLigne() {
        final String id = salonId;
        gSalon++;
        h.removeCallbacks(poll);
        nouvelEcran(M_FIN);
        arreterVoix();
        Ui.ajouter(page, Ui.texte(this, "🏁 Partie terminée", 26, Ui.JAUNE, true), 0);
        tvJoueurs = Ui.texte(this, "Calcul du classement…", 17, Ui.TXT, true);
        Ui.ajouter(page, tvJoueurs, 18);
        Button encore = Ui.bouton(this, "🔁  Autre partie en ligne", Ui.VIOLET);
        encore.setOnClickListener(v -> afficherLobby());
        Ui.ajouter(page, encore, 22);
        retourMenu("Menu");
        remplirPodium(false);
        if (id == null) return;
        new Thread(() -> {
            JSONObject r = null;
            for (int i = 0; i < 4; i++) {
                r = QuizReseau.etat(id);
                if (r != null && r.optBoolean("ok") && "fini".equals(r.optString("etat"))) break;
                try { Thread.sleep(1500); } catch (InterruptedException e) { break; }
            }
            final JSONObject fin = r;
            runOnUiThread(() -> {
                if (!vivant || mode != M_FIN) return;
                if (fin != null && fin.optBoolean("ok")) et = fin;
                salonId = null;
                remplirPodium(true);
            });
        }).start();
    }

    private void remplirPodium(boolean final_) {
        if (tvJoueurs == null) return;
        String txt = lignesClassement();
        String vic = texteVictoire();
        if (!txt.isEmpty()) tvJoueurs.setText(vic.isEmpty() ? txt : txt + "\n\n" + vic);
        if (!final_ || et == null) return;
        JSONArray j = et.optJSONArray("joueurs");
        for (int i = 0; j != null && i < j.length(); i++) {
            JSONObject o = j.optJSONObject(i);
            if (o == null || !o.optBoolean("moi")) continue;
            int score = o.optInt("score");
            JeuxActivity.enregistrer(this, "quiz", score);
            JSONObject v = et.optJSONObject("victoire");
            int gain = v != null && v.optBoolean("moi") ? v.optInt("points") : 0;
            if (gain > 0) parler("Félicitations, vous gagnez la partie et " + gain + " points gagnés !");
            else parler(i == 0 ? "Partie terminée. Vous êtes premier avec " + score + " points." : "Partie terminée. Vous êtes " + (i + 1) + "e avec " + score + " points.");
        }
    }
}
