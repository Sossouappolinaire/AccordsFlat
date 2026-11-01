package bj.radio;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Random;

/**
 * Football Kouamé (mode « foot » : arcade, 6 contre 6) et Foot Pro Kouamé (mode « pes » : simulation, 11 contre 11).
 * Jeux originaux dessinés en 2D avec perspective (caméra de retransmission TV), sans logo ni nom de club réel.
 * Commandes comme sur console : stick à gauche, TIR (maintenir pour la puissance), PASSE, PASSE LONGUE, SPRINT.
 * En défense : TACLE, CHANGER de joueur, GLISSADE.
 */
public class JeuFootActivity extends Activity {

    private VueFoot vue;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersif();
        boolean pes = "pes".equals(getIntent().getStringExtra("mode"));
        String duel = getIntent().getStringExtra("online_duel");
        boolean enLigne = duel != null && !duel.isEmpty();
        vue = new VueFoot(this, pes, enLigne ? duel : "", enLigne && getIntent().getBooleanExtra("online_host", false),
                getIntent().getStringExtra("online_adversaire"));
        setContentView(vue);
        if (enLigne) vue.lancerSynchronisation();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) immersif();
    }

    private void immersif() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (vue != null) {
            if (vue.enLigne) vue.quitterEnLigne();
            else vue.pauser();
        }
    }

    @Override
    protected void onDestroy() {
        if (vue != null && vue.enLigne) vue.quitterEnLigne();
        super.onDestroy();
    }

    // =====================================================================================================

    private static final class Joueur {
        int eq, role, num, peau;
        float hu, hv;                       // position de base dans la formation
        float x, y, vx, vy, dx = 1f, dy;    // position, vitesse, direction regardée
        float ax, ay;                       // direction voulue (-1..1)
        float phase, stamina = 1f, vit = 1f;
        float stun, glisse, lock, kick, decide, tacleCd, prot, hold;
        boolean sprint, plongeon, glisseTente;
    }

    private static final class VueFoot extends View {

        // Terrain en dixièmes de mètre : 105 m x 68 m
        private static final float L = 1050f, W = 680f, CY = 340f, BUT_DEMI = 38f, BUT_H = 24f, G = 620f;
        private static final float HAUT = 360f, Y0 = 66f, Y1 = 338f;
        private static final int MENU = 0, ENGAGE = 1, JEU = 2, BUT = 3, ARRET = 4, MITEMPS = 5, FIN = 6, PAUSE = 7;
        private static final int GK = 0, DEF = 1, MIL = 2, ATT = 3;

        private static final String[] CODE = {"BEN", "FRA", "BRE", "ARG", "POR", "MAR", "SEN", "CIV", "NGA", "ALL", "ESP", "ITA"};
        private static final String[] NOM = {"Bénin", "France", "Brésil", "Argentine", "Portugal", "Maroc",
                "Sénégal", "Côte d'Ivoire", "Nigeria", "Allemagne", "Espagne", "Italie"};
        private static final int[] MAILLOT = {0xFF1B9E3E, 0xFF1F3F9E, 0xFFF5D000, 0xFF7EC8F0, 0xFFC8102E, 0xFFC8102E,
                0xFFF2F2F2, 0xFFF77F00, 0xFF14883A, 0xFFF2F2F2, 0xFFD62828, 0xFF1D5FD1};
        private static final int[] RAYURE = {0, 0, 0, 0xFFFFFFFF, 0, 0, 0, 0, 0, 0, 0, 0};
        private static final int[] SHORT = {0xFFF5D000, 0xFFFFFFFF, 0xFF1F3F9E, 0xFF111111, 0xFF1B6E3A, 0xFF1B6E3A,
                0xFF1B9E3E, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF111111, 0xFF1F3F9E, 0xFFFFFFFF};
        private static final int[] CHAUSS = {0xFFD62828, 0xFFC8102E, 0xFFF5D000, 0xFFFFFFFF, 0xFFC8102E, 0xFFC8102E,
                0xFFF2F2F2, 0xFF1B9E3E, 0xFF14883A, 0xFF111111, 0xFFD62828, 0xFF1D5FD1};
        private static final int[] PEAU = {0xFFF1C27D, 0xFFE0AC69, 0xFFC68642, 0xFF8D5524, 0xFF6B4226};
        private static final String[] NIV_NOM = {"Amateur", "Pro", "Légende"};
        private static final float[] NIV_VIT = {0.88f, 0.97f, 1.06f};
        private static final float[] NIV_ARRET = {0.45f, 0.62f, 0.78f};
        private static final float[] NIV_TACLE = {0.22f, 0.32f, 0.42f};
        private static final float[] NIV_TIR = {0.35f, 0.6f, 0.82f};
        private static final float[] NIV_PTS = {1f, 1.5f, 2.2f};

        private final Context ctx;
        private final boolean pes;
        private final String cle;
        private final int nb;
        private final float gs, demi, etendue;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rf = new RectF();
        private final Random rnd = new Random();
        private final Joueur[] js;
        private final int[] ordre;
        private final float[] foulX = new float[220], foulY = new float[220];
        private final int[] foulC = new int[220];

        private int etat = MENU, avant = JEU;
        private float k = 1f, largeur = 640f, S0 = 1.5f;
        private long dernier, tEtat;
        private int tMoi = 0, tAdv = 3, niveau = 1, mi = 0, butsMoi, butsAdv, extraMin, record, engageApres;
        private float tMi, minuteur, camX = L / 2f, temps, autoT;
        private String msg = "";
        private Joueur buteur;
        private Shader tribune, ombreHerbe;

        // balle
        private float bx = L / 2f, by = CY, bz, bvx, bvy, bvz, brot;
        private Joueur owner, dernierToucheur, attendu;
        private boolean tir, gkVerifie, blocVerifie;
        private int tirEq, ctrl, ctrlAdv;

        // En ligne, l'hôte simule la partie et reçoit les commandes de l'invité.
        private final String duel;
        private final boolean enLigne, hote;
        private final String nomAdversaire;
        private final Handler reseauUi = new Handler(Looper.getMainLooper());
        private volatile boolean reseauActif;
        private volatile boolean snapshotRecu;
        private volatile JSONObject instantaneAEnvoyer;
        private volatile float entreeJx, entreeJy;
        private volatile boolean entreeSprint;
        private volatile int entreeAction, entreeSeq;
        private int derniereActionDistante = -1, entreeActionDistante = -1;
        private float distantJx, distantJy, distantChargeV;
        private boolean distantSprint, distantTirCharge, entreeDistanteActive;
        private long derniereEntreeDistante;
        private long dernierInstantane;
        private String etatReseau = "Connexion au match…";

        // arrêt de jeu
        private int arretEq, arretType;
        private float arretX, arretY;

        // entrées
        private float jx, jy, jcx = 82f, jcy = 288f, chargeV, cibleX, cibleY;
        private int joyId = -1, shootId = -1, sprintId = -1;
        private boolean sprint, charge;

        VueFoot(Context c, boolean pes, String duel, boolean hote, String nomAdversaire) {
            super(c);
            ctx = c;
            this.pes = pes;
            this.duel = duel == null ? "" : duel;
            this.enLigne = !this.duel.isEmpty();
            this.hote = hote;
            this.nomAdversaire = nomAdversaire == null || nomAdversaire.isEmpty() ? "Adversaire" : nomAdversaire;
            cle = pes ? "pes" : "foot";
            nb = pes ? 11 : 6;
            gs = pes ? 1.05f : 1.35f;
            demi = pes ? 130f : 70f;
            etendue = pes ? 500f : 410f;
            js = new Joueur[nb * 2];
            ordre = new int[nb * 2 + 1];
            record = JeuxActivity.record(c, cle);
            for (int i = 0; i < foulX.length; i++) {
                foulX[i] = rnd.nextFloat() * 1400f;
                foulY[i] = rnd.nextFloat();
                foulC[i] = FOULE[rnd.nextInt(FOULE.length)];
            }
            creerJoueurs();
            if (enLigne && hote) demarrerMatch();
        }

        void lancerSynchronisation() {
            if (!enLigne || reseauActif) return;
            reseauActif = true;
            Thread t = new Thread(() -> {
                while (reseauActif) {
                    long debut = SystemClock.elapsedRealtime();
                    JSONObject entree;
                    JSONObject instantane = hote ? instantaneAEnvoyer : null;
                    try {
                        entree = new JSONObject()
                                .put("jx", hote ? 0f : entreeJx)
                                .put("jy", hote ? 0f : entreeJy)
                                .put("sprint", !hote && entreeSprint)
                                .put("action", hote ? 0 : entreeAction)
                                .put("seq", hote ? 0 : entreeSeq);
                    } catch (Exception e) {
                        entree = new JSONObject();
                    }
                    JSONObject retour = FootReseau.synchroniser(duel, entree, instantane);
                    if (retour != null) {
                        JSONObject reponse = retour;
                        reseauUi.post(() -> {
                            if (!reseauActif) return;
                            String statut = reponse.optString("etat", "");
                            if ("termine".equals(statut) || "annule".equals(statut)
                                    || "introuvable".equals(statut) || "refuse".equals(statut)) {
                                etatReseau = "L’adversaire a quitté le match.";
                                if (etat != FIN) {
                                    etat = FIN;
                                    tEtat = SystemClock.uptimeMillis();
                                }
                                reseauActif = false;
                                invalidate();
                                return;
                            }
                            etatReseau = "En ligne · " + nomAdversaire;
                            if (hote) {
                                recevoirEntreeDistante(reponse.optJSONObject("adversaire"));
                            } else {
                                JSONObject instantaneRecu = reponse.optJSONObject("snapshot");
                                if (instantaneRecu != null) appliquerInstantane(instantaneRecu);
                            }
                        });
                    } else {
                        reseauUi.post(() -> {
                            if (reseauActif) {
                                etatReseau = "Connexion instable…";
                                invalidate();
                            }
                        });
                    }
                    long reste = 165L - (SystemClock.elapsedRealtime() - debut);
                    if (reste > 0L) {
                        try {
                            Thread.sleep(reste);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
            }, "foot-en-ligne");
            t.setDaemon(true);
            t.start();
        }

        void quitterEnLigne() {
            if (!enLigne) return;
            boolean notifier = reseauActif || !sortieDemandee;
            sortieDemandee = true;
            reseauActif = false;
            if (notifier) new Thread(() -> FootReseau.quitter(duel), "foot-quitter").start();
        }

        private boolean sortieDemandee;

        private void publierInstantane() {
            if (!enLigne || !hote) return;
            long maintenant = SystemClock.elapsedRealtime();
            if (maintenant - dernierInstantane < 165L) return;
            dernierInstantane = maintenant;
            try {
                JSONArray joueurs = new JSONArray();
                for (Joueur q : js) {
                    joueurs.put(new JSONObject()
                            .put("eq", q.eq).put("role", q.role).put("num", q.num).put("peau", q.peau)
                            .put("hu", q.hu).put("hv", q.hv)
                            .put("x", q.x).put("y", q.y).put("vx", q.vx).put("vy", q.vy)
                            .put("dx", q.dx).put("dy", q.dy).put("ax", q.ax).put("ay", q.ay)
                            .put("phase", q.phase).put("stamina", q.stamina).put("vit", q.vit)
                            .put("stun", q.stun).put("glisse", q.glisse).put("lock", q.lock)
                            .put("kick", q.kick).put("decide", q.decide).put("tacleCd", q.tacleCd)
                            .put("prot", q.prot).put("hold", q.hold).put("sprint", q.sprint)
                            .put("plongeon", q.plongeon).put("glisseTente", q.glisseTente));
                }
                instantaneAEnvoyer = new JSONObject()
                        .put("joueurs", joueurs).put("etat", etat).put("avant", avant)
                        .put("tMoi", tMoi).put("tAdv", tAdv).put("niveau", niveau)
                        .put("mi", mi).put("butsMoi", butsMoi).put("butsAdv", butsAdv)
                        .put("extraMin", extraMin).put("engageApres", engageApres)
                        .put("tMi", tMi).put("minuteur", minuteur).put("temps", temps)
                        .put("camX", camX).put("autoT", autoT).put("msg", msg)
                        .put("bx", bx).put("by", by).put("bz", bz)
                        .put("bvx", bvx).put("bvy", bvy).put("bvz", bvz).put("brot", brot)
                        .put("owner", indexSnapshot(owner)).put("dernierToucheur", indexSnapshot(dernierToucheur))
                        .put("attendu", indexSnapshot(attendu)).put("buteur", indexSnapshot(buteur))
                        .put("tir", tir).put("gkVerifie", gkVerifie).put("blocVerifie", blocVerifie)
                        .put("tirEq", tirEq).put("ctrl", ctrl).put("ctrlAdv", ctrlAdv)
                        .put("arretEq", arretEq).put("arretType", arretType)
                        .put("arretX", arretX).put("arretY", arretY)
                        .put("charge", charge).put("chargeV", chargeV)
                        .put("remoteCharge", distantTirCharge).put("remoteChargeV", distantChargeV);
            } catch (Exception ignore) {
                instantaneAEnvoyer = null;
            }
        }

        private void appliquerInstantane(JSONObject o) {
            JSONArray joueurs = o.optJSONArray("joueurs");
            if (joueurs == null || joueurs.length() != js.length) return;
            int ancienEtat = etat;
            for (int i = 0; i < js.length; i++) {
                JSONObject v = joueurs.optJSONObject(i);
                if (v == null) return;
                Joueur q = js[i];
                q.eq = v.optInt("eq", q.eq);
                q.role = v.optInt("role", q.role);
                q.num = v.optInt("num", q.num);
                q.peau = borne(v.optInt("peau", q.peau), 0, PEAU.length - 1);
                q.hu = (float) v.optDouble("hu", q.hu);
                q.hv = (float) v.optDouble("hv", q.hv);
                q.x = (float) v.optDouble("x", q.x);
                q.y = (float) v.optDouble("y", q.y);
                q.vx = (float) v.optDouble("vx", q.vx);
                q.vy = (float) v.optDouble("vy", q.vy);
                q.dx = (float) v.optDouble("dx", q.dx);
                q.dy = (float) v.optDouble("dy", q.dy);
                q.ax = (float) v.optDouble("ax", q.ax);
                q.ay = (float) v.optDouble("ay", q.ay);
                q.phase = (float) v.optDouble("phase", q.phase);
                q.stamina = (float) v.optDouble("stamina", q.stamina);
                q.vit = (float) v.optDouble("vit", q.vit);
                q.stun = (float) v.optDouble("stun", q.stun);
                q.glisse = (float) v.optDouble("glisse", q.glisse);
                q.lock = (float) v.optDouble("lock", q.lock);
                q.kick = (float) v.optDouble("kick", q.kick);
                q.decide = (float) v.optDouble("decide", q.decide);
                q.tacleCd = (float) v.optDouble("tacleCd", q.tacleCd);
                q.prot = (float) v.optDouble("prot", q.prot);
                q.hold = (float) v.optDouble("hold", q.hold);
                q.sprint = v.optBoolean("sprint", q.sprint);
                q.plongeon = v.optBoolean("plongeon", q.plongeon);
                q.glisseTente = v.optBoolean("glisseTente", q.glisseTente);
            }

            etat = o.optInt("etat", etat);
            if (etat == FIN && ancienEtat != FIN) tEtat = SystemClock.uptimeMillis();
            avant = o.optInt("avant", avant);
            tMoi = borne(o.optInt("tMoi", tMoi), 0, CODE.length - 1);
            tAdv = borne(o.optInt("tAdv", tAdv), 0, CODE.length - 1);
            niveau = borne(o.optInt("niveau", niveau), 0, NIV_NOM.length - 1);
            mi = o.optInt("mi", mi);
            butsMoi = o.optInt("butsMoi", butsMoi);
            butsAdv = o.optInt("butsAdv", butsAdv);
            extraMin = o.optInt("extraMin", extraMin);
            engageApres = o.optInt("engageApres", engageApres);
            tMi = (float) o.optDouble("tMi", tMi);
            minuteur = (float) o.optDouble("minuteur", minuteur);
            temps = (float) o.optDouble("temps", temps);
            camX = (float) o.optDouble("camX", camX);
            autoT = (float) o.optDouble("autoT", autoT);
            msg = o.optString("msg", msg);
            bx = (float) o.optDouble("bx", bx);
            by = (float) o.optDouble("by", by);
            bz = (float) o.optDouble("bz", bz);
            bvx = (float) o.optDouble("bvx", bvx);
            bvy = (float) o.optDouble("bvy", bvy);
            bvz = (float) o.optDouble("bvz", bvz);
            brot = (float) o.optDouble("brot", brot);
            owner = joueurOuNull(o.optInt("owner", -1));
            dernierToucheur = joueurOuNull(o.optInt("dernierToucheur", -1));
            attendu = joueurOuNull(o.optInt("attendu", -1));
            buteur = joueurOuNull(o.optInt("buteur", -1));
            tir = o.optBoolean("tir", tir);
            gkVerifie = o.optBoolean("gkVerifie", gkVerifie);
            blocVerifie = o.optBoolean("blocVerifie", blocVerifie);
            tirEq = o.optInt("tirEq", tirEq);
            ctrl = borne(o.optInt("ctrl", ctrl), 0, js.length - 1);
            ctrlAdv = borne(o.optInt("ctrlAdv", ctrlAdv), 0, js.length - 1);
            arretEq = o.optInt("arretEq", arretEq);
            arretType = o.optInt("arretType", arretType);
            arretX = (float) o.optDouble("arretX", arretX);
            arretY = (float) o.optDouble("arretY", arretY);
            charge = o.optBoolean("charge", charge);
            chargeV = (float) o.optDouble("chargeV", chargeV);
            distantTirCharge = o.optBoolean("remoteCharge", distantTirCharge);
            distantChargeV = (float) o.optDouble("remoteChargeV", distantChargeV);
            snapshotRecu = true;
            dernier = SystemClock.uptimeMillis();
            invalidate();
        }

        private Joueur joueurOuNull(int index) {
            return index < 0 || index >= js.length ? null : js[index];
        }

        private int indexSnapshot(Joueur q) {
            if (q == null) return -1;
            for (int i = 0; i < js.length; i++) if (js[i] == q) return i;
            return -1;
        }

        private static final int[] FOULE = {0xFFB71C1C, 0xFF1565C0, 0xFFF9A825, 0xFFECEFF1, 0xFF2E7D32, 0xFF6A1B9A, 0xFF263238};

        private void creerJoueurs() {
            float[] hu = pes ? new float[]{0.05f, .24f, .24f, .24f, .24f, .48f, .48f, .48f, .48f, .72f, .72f}
                    : new float[]{0.05f, .26f, .26f, .5f, .5f, .72f};
            float[] hv = pes ? new float[]{.5f, .12f, .37f, .63f, .88f, .12f, .37f, .63f, .88f, .36f, .64f}
                    : new float[]{.5f, .3f, .7f, .25f, .75f, .5f};
            int[] roles = pes ? new int[]{GK, DEF, DEF, DEF, DEF, MIL, MIL, MIL, MIL, ATT, ATT}
                    : new int[]{GK, DEF, DEF, MIL, MIL, ATT};
            int[] nums = pes ? new int[]{1, 2, 4, 5, 3, 7, 8, 10, 11, 9, 14} : new int[]{1, 4, 5, 8, 10, 9};
            for (int eq = 0; eq < 2; eq++) {
                for (int i = 0; i < nb; i++) {
                    Joueur q = new Joueur();
                    q.eq = eq;
                    q.role = roles[i];
                    q.num = nums[i];
                    q.hu = hu[i];
                    q.hv = hv[i];
                    q.peau = rnd.nextInt(PEAU.length);
                    q.vit = 0.94f + rnd.nextFloat() * 0.12f;
                    js[eq * nb + i] = q;
                }
            }
        }

        // ------------------------------------------------------------------ utilitaires

        private float dist(float x1, float y1, float x2, float y2) {
            float a = x1 - x2, b = y1 - y2;
            return (float) Math.sqrt(a * a + b * b);
        }

        private float borne(float v, float a, float b) {
            return v < a ? a : (v > b ? b : v);
        }

        private int borne(int v, int a, int b) {
            return Math.max(a, Math.min(b, v));
        }

        private float dirDe(Joueur q) {
            return q.eq == 0 ? 1f : -1f;
        }

        private int indexDe(Joueur q) {
            for (int i = 0; i < js.length; i++) if (js[i] == q) return i;
            return 0;
        }

        private Joueur gardien(int eq) {
            return js[eq * nb];
        }

        private boolean humain(Joueur q) {
            if (q.eq == 0) return js[ctrl] == q;
            return enLigne && hote && q.eq == 1 && js[ctrlAdv] == q;
        }

        private float distSegment(float px, float py, float ax, float ay, float bx2, float by2) {
            float dx = bx2 - ax, dy = by2 - ay;
            float l2 = dx * dx + dy * dy;
            if (l2 < 0.01f) return dist(px, py, ax, ay);
            float t = borne(((px - ax) * dx + (py - ay) * dy) / l2, 0f, 1f);
            return dist(px, py, ax + dx * t, ay + dy * t);
        }

        private Joueur plusProche(int eq, float x, float y, boolean avecGk) {
            Joueur best = null;
            float bd = 1e9f;
            for (int i = eq * nb; i < eq * nb + nb; i++) {
                Joueur q = js[i];
                if (q.role == GK && !avecGk) continue;
                float d = dist(q.x, q.y, x, y);
                if (d < bd) {
                    bd = d;
                    best = q;
                }
            }
            return best;
        }

        private float distAdversaire(float x, float y, int eqAmi) {
            float bd = 1e9f;
            for (Joueur q : js) {
                if (q.eq == eqAmi) continue;
                float d = dist(q.x, q.y, x, y);
                if (d < bd) bd = d;
            }
            return bd;
        }

        // ------------------------------------------------------------------ cycle de la partie

        void pauser() {
            if (enLigne) {
                quitterEnLigne();
                return;
            }
            if (etat == JEU || etat == ENGAGE || etat == ARRET || etat == BUT) {
                avant = etat;
                etat = PAUSE;
            }
            relacherTout();
            invalidate();
        }

        private void relacherTout() {
            boolean tirEnCours = shootId >= 0;
            joyId = -1;
            shootId = -1;
            sprintId = -1;
            jx = 0f;
            jy = 0f;
            jcx = 82f;
            jcy = 288f;
            sprint = false;
            charge = false;
            chargeV = 0f;
            if (enLigne && !hote) {
                entreeJx = 0f;
                entreeJy = 0f;
                entreeSprint = false;
                if (tirEnCours) envoyerAction(4);
                else {
                    entreeAction = 0;
                    entreeSeq++;
                }
            }
        }

        private void demarrerMatch() {
            butsMoi = 0;
            butsAdv = 0;
            mi = 0;
            tMi = 0f;
            extraMin = 1 + rnd.nextInt(3);
            for (Joueur q : js) q.stamina = 1f;
            relacherTout();
            placerEngagement(0);
            etat = ENGAGE;
            minuteur = 1.4f;
            msg = "COUP D'ENVOI";
            dernier = SystemClock.uptimeMillis();
            invalidate();
        }

        private void placerEngagement(int eqEngage) {
            for (Joueur q : js) {
                float hx = q.eq == 0 ? q.hu * L : L - q.hu * L;
                float hy = q.eq == 0 ? q.hv * W : (1f - q.hv) * W;
                if (q.eq == 0) hx = Math.min(hx, L / 2f - 25f);
                else hx = Math.max(hx, L / 2f + 25f);
                q.x = hx;
                q.y = hy;
                q.vx = 0f;
                q.vy = 0f;
                q.ax = 0f;
                q.ay = 0f;
                q.dx = q.eq == 0 ? 1f : -1f;
                q.dy = 0f;
                q.stun = 0f;
                q.glisse = 0f;
                q.lock = 0f;
                q.kick = 0f;
                q.prot = 1.5f;
                q.sprint = false;
                q.plongeon = false;
            }
            Joueur pr = null;
            for (Joueur q : js) if (q.eq == eqEngage && q.role == ATT) pr = q;
            pr.x = L / 2f - (eqEngage == 0 ? 7f : -7f);
            pr.y = CY;
            bx = L / 2f;
            by = CY;
            bz = 0f;
            bvx = 0f;
            bvy = 0f;
            bvz = 0f;
            tir = false;
            attendu = null;
            owner = pr;
            dernierToucheur = pr;
            if (eqEngage == 0) ctrl = indexDe(pr);
            else ctrlAdv = indexDe(pr);
            if (eqEngage == 0) ctrlAdv = indexDe(plusProche(1, L / 2f, CY, false));
            else ctrl = indexDe(plusProche(0, L / 2f, CY, false));
            camX = L / 2f;
            autoT = 0f;
        }

        private void marquer(int eqBut) {
            annulerTirDistant();
            if (eqBut == 0) butsMoi++;
            else butsAdv++;
            buteur = dernierToucheur;
            msg = "BUT !";
            etat = BUT;
            minuteur = 3.2f;
            engageApres = 1 - eqBut;
            owner = null;
            tir = false;
            bvx = 0f;
            bvy = 0f;
            bvz = 0f;
            bz = 0f;
            bx = borne(bx, -14f, L + 14f);
            charge = false;
            chargeV = 0f;
            for (Joueur q : js) {
                q.ax = 0f;
                q.ay = 0f;
                q.sprint = false;
            }
        }

        private void arret(int eqRepris, float x, float y, int type) {
            annulerTirDistant();
            etat = ARRET;
            minuteur = 1.5f;
            arretEq = eqRepris;
            arretX = x;
            arretY = y;
            arretType = type;
            owner = null;
            tir = false;
            attendu = null;
            bx = x;
            by = y;
            bz = 0f;
            bvx = 0f;
            bvy = 0f;
            bvz = 0f;
            charge = false;
            chargeV = 0f;
            msg = type == 1 ? "TOUCHE" : type == 2 ? "CORNER" : type == 3 ? "DÉGAGEMENT" : "FAUTE !";
        }

        private void executerArret() {
            Joueur t = arretType == 3 ? gardien(arretEq) : plusProche(arretEq, arretX, arretY, false);
            float dirIn = arretEq == 0 ? 1f : -1f;
            t.x = arretType == 3 ? arretX - dirIn * 12f : arretX - dirIn * 4f;
            t.y = arretY;
            if (arretType == 1) t.y = arretY < CY ? arretY - 6f : arretY + 6f;
            t.vx = 0f;
            t.vy = 0f;
            t.stun = 0f;
            t.glisse = 0f;
            t.lock = 0f;
            t.prot = 1.4f;
            t.dx = dirIn;
            t.dy = 0f;
            prendre(t);
            t.hold = 0.6f;
            autoT = arretEq == 0 ? 2.8f : 0f;
            etat = JEU;
        }

        private void prendre(Joueur q) {
            owner = q;
            dernierToucheur = q;
            tir = false;
            attendu = null;
            bz = 0f;
            if (q.role == GK) q.hold = 0.7f + rnd.nextFloat() * 0.6f;
            if (q.eq == 0 && q.role != GK) ctrl = indexDe(q);
            else if (q.eq == 1 && q.role != GK) ctrlAdv = indexDe(q);
        }

        private void finMiTemps() {
            annulerTirDistant();
            relacherTout();
            owner = null;
            if (mi == 0) {
                etat = MITEMPS;
                msg = "MI-TEMPS";
            } else {
                etat = FIN;
                msg = "FIN DU MATCH";
                int pts = butsMoi * 100 - butsAdv * 25 + (butsMoi > butsAdv ? 400 : (butsMoi == butsAdv ? 120 : 0));
                pts = Math.max(0, (int) (pts * NIV_PTS[niveau]));
                if (!enLigne && pts > 0) JeuxActivity.enregistrer(ctx, cle, pts);
                if (pts > record) record = pts;
                dernierPoints = pts;
            }
            tEtat = SystemClock.uptimeMillis();
        }

        private int dernierPoints;

        private void secondeMiTemps() {
            mi = 1;
            tMi = 0f;
            extraMin = 1 + rnd.nextInt(3);
            placerEngagement(1);
            etat = ENGAGE;
            minuteur = 1.4f;
            msg = "COUP D'ENVOI";
            dernier = SystemClock.uptimeMillis();
        }

        // ------------------------------------------------------------------ mise à jour

        private void maj(float dt) {
            temps += dt;
            if (etat == ENGAGE) {
                minuteur -= dt;
                animerRepos(dt);
                if (minuteur <= 0f) etat = JEU;
            } else if (etat == BUT) {
                minuteur -= dt;
                animerRepos(dt);
                if (minuteur <= 0f) {
                    placerEngagement(engageApres);
                    etat = ENGAGE;
                    minuteur = 1.3f;
                    msg = "COUP D'ENVOI";
                }
            } else if (etat == ARRET) {
                minuteur -= dt;
                ia(dt);
                deplacer(dt);
                if (minuteur <= 0f) executerArret();
            } else if (etat == JEU) {
                jouer(dt);
            }
            camera(dt);
        }

        private void animerRepos(float dt) {
            for (Joueur q : js) {
                q.vx = 0f;
                q.vy = 0f;
                q.prot = Math.max(q.prot, 0.5f);
            }
            if (owner != null) {
                bx = owner.x + owner.dx * 8f;
                by = owner.y + owner.dy * 6f;
            }
        }

        private void jouer(float dt) {
            tMi += dt;
            float fin = demi + extraMin * demi / 45f;
            if (tMi >= fin) {
                finMiTemps();
                return;
            }
            entrees(dt);
            if (enLigne && hote) entreesDistantes(dt);
            ia(dt);
            deplacer(dt);
            balle(dt);
            if (etat == JEU) tacles(dt);
        }

        private void camera(float dt) {
            float cible = bx + (owner != null ? owner.dx * 45f : bvx * 0.15f);
            float m = etendue * 0.38f;
            cible = borne(cible, m, L - m);
            camX += (cible - camX) * Math.min(1f, dt * 3.5f);
        }

        // ------------------------------------------------------------------ entrées du joueur humain

        private void entrees(float dt) {
            selectionAuto(0);
            Joueur c = js[ctrl];
            if (c.stun > 0f || c.glisse > 0f) {
                c.ax = 0f;
                c.ay = 0f;
                return;
            }
            c.ax = jx;
            c.ay = jy;
            c.sprint = sprint && Math.hypot(jx, jy) > 0.2f;
            if (charge) chargeV = Math.min(1f, chargeV + dt * 1.4f);
            if (autoT > 0f) {
                autoT -= dt;
                if (autoT <= 0f && owner == c && c.role != GK) {
                    Joueur t = meilleurePasse(c, 0f, 0f, false);
                    if (t != null) passer(c, t, false);
                }
            }
        }

        private void entreesDistantes(float dt) {
            if (SystemClock.elapsedRealtime() - derniereEntreeDistante > 1500L) {
                entreeDistanteActive = false;
                distantJx = 0f;
                distantJy = 0f;
                distantSprint = false;
                distantTirCharge = false;
                distantChargeV = 0f;
            }
            if (!entreeDistanteActive) return;
            selectionAuto(1);
            Joueur c = js[ctrlAdv];
            if (c.stun > 0f || c.glisse > 0f) {
                c.ax = 0f;
                c.ay = 0f;
            } else {
                c.ax = distantJx;
                c.ay = distantJy;
                c.sprint = distantSprint && Math.hypot(distantJx, distantJy) > 0.2f;
            }
            if (distantTirCharge) distantChargeV = Math.min(1f, distantChargeV + dt * 1.4f);

            int action = entreeActionDistante;
            if (action >= 0) {
                entreeActionDistante = -1;
                switch (action) {
                    case 1:
                        actionPasse(1, distantJx, distantJy, false);
                        break;
                    case 2:
                        actionPasse(1, distantJx, distantJy, true);
                        break;
                    case 3:
                        pressionTirDistante();
                        break;
                    case 4:
                        relacherTirDistant();
                        break;
                }
            }
        }

        private void recevoirEntreeDistante(JSONObject entree) {
            if (!enLigne || !hote || entree == null) return;
            distantJx = borne((float) entree.optDouble("jx", 0f), -1f, 1f);
            distantJy = borne((float) entree.optDouble("jy", 0f), -1f, 1f);
            distantSprint = entree.optBoolean("sprint", false);
            derniereEntreeDistante = SystemClock.elapsedRealtime();
            entreeDistanteActive = true;
            int seq = entree.optInt("seq", 0);
            if (seq > derniereActionDistante) {
                derniereActionDistante = seq;
                int action = entree.optInt("action", 0);
                entreeActionDistante = action == 0 ? -1 : action;
            }
        }

        private void pressionTirDistante() {
            if (etat != JEU) return;
            Joueur c = js[ctrlAdv];
            if (owner == c) {
                distantTirCharge = true;
                distantChargeV = 0f;
            } else if (owner != null && owner.eq == 0 && c.glisse <= 0f && c.stun <= 0f && c.tacleCd <= 0f) {
                float dx = owner.x - c.x, dy = owner.y - c.y;
                float d = (float) Math.hypot(dx, dy);
                if (d < 1f) {
                    dx = c.dx;
                    dy = c.dy;
                    d = 1f;
                }
                c.glisse = 0.45f;
                c.glisseTente = false;
                c.tacleCd = 1.1f;
                c.vx = dx / d * 235f * gs;
                c.vy = dy / d * 235f * gs;
            } else if (owner == null && c.glisse <= 0f && c.stun <= 0f && c.tacleCd <= 0f
                    && dist(c.x, c.y, bx, by) < 70f) {
                float dx = bx - c.x, dy = by - c.y;
                float d = Math.max(1f, (float) Math.hypot(dx, dy));
                c.glisse = 0.45f;
                c.glisseTente = true;
                c.tacleCd = 1.1f;
                c.vx = dx / d * 235f * gs;
                c.vy = dy / d * 235f * gs;
            }
        }

        private void relacherTirDistant() {
            if (distantTirCharge && etat == JEU) {
                Joueur c = js[ctrlAdv];
                if (owner == c) tirerHumain(c, 0.45f + 0.55f * distantChargeV, distantJy);
            }
            distantTirCharge = false;
            distantChargeV = 0f;
        }

        private void annulerTirDistant() {
            distantTirCharge = false;
            distantChargeV = 0f;
            entreeActionDistante = -1;
        }

        private void selectionAuto() {
            selectionAuto(0);
        }

        private int controleur(int eq) {
            return eq == 0 ? ctrl : ctrlAdv;
        }

        private void choisirControleur(int eq, int index) {
            if (eq == 0) ctrl = index;
            else ctrlAdv = index;
        }

        private void selectionAuto(int eq) {
            if (owner != null && owner.eq == eq && owner.role != GK) {
                choisirControleur(eq, indexDe(owner));
                return;
            }
            int index = controleur(eq);
            Joueur cur = js[index];
            float fx = bx + bvx * 0.25f, fy = by + bvy * 0.25f;
            Joueur best = plusProche(eq, fx, fy, false);
            if (best != null && best != cur) {
                if (cur.role == GK || dist(cur.x, cur.y, fx, fy) > dist(best.x, best.y, fx, fy) + 18f) {
                    choisirControleur(eq, indexDe(best));
                }
            }
        }

        private void changerJoueur() {
            changerJoueur(0);
        }

        private void changerJoueur(int eq) {
            Joueur cur = js[controleur(eq)];
            Joueur best = null;
            float bd = 1e9f;
            for (int i = eq * nb; i < (eq + 1) * nb; i++) {
                Joueur q = js[i];
                if (q == cur || q.role == GK) continue;
                float d = dist(q.x, q.y, bx, by);
                if (d < bd) {
                    bd = d;
                    best = q;
                }
            }
            if (best != null) choisirControleur(eq, indexDe(best));
        }

        private void actionPasse(boolean profond) {
            actionPasse(0, jx, jy, profond);
        }

        private void actionPasse(int eq, float ux, float uy, boolean profond) {
            if (etat != JEU) return;
            Joueur c = js[controleur(eq)];
            if (owner == c) {
                Joueur t = meilleurePasse(c, ux, uy, true);
                if (t != null) passer(c, t, profond);
                autoT = 0f;
            } else if (owner != null && owner.eq != eq) {
                if (profond) {
                    changerJoueur(eq);
                } else if (c.tacleCd <= 0f && c.stun <= 0f && c.glisse <= 0f) {
                    c.tacleCd = 0.55f;
                    float d = dist(c.x, c.y, owner.x, owner.y);
                    if (d <= 22f) {
                        if (rnd.nextFloat() < 0.62f) dispossess(owner, c);
                    } else {
                        c.vx += (owner.x - c.x) / d * 60f;
                        c.vy += (owner.y - c.y) / d * 60f;
                    }
                }
            } else if (profond) {
                changerJoueur(eq);
            }
        }

        private void pressionTir() {
            pressionTir(0);
        }

        private void pressionTir(int eq) {
            if (etat != JEU) return;
            Joueur c = js[controleur(eq)];
            if (owner == c) {
                charge = true;
                chargeV = 0f;
            } else if (owner != null && owner.eq != eq && c.glisse <= 0f && c.stun <= 0f && c.tacleCd <= 0f) {
                float dx = owner.x - c.x, dy = owner.y - c.y;
                float d = (float) Math.hypot(dx, dy);
                if (d < 1f) {
                    dx = c.dx;
                    dy = c.dy;
                    d = 1f;
                }
                c.glisse = 0.45f;
                c.glisseTente = false;
                c.tacleCd = 1.1f;
                c.vx = dx / d * 235f * gs;
                c.vy = dy / d * 235f * gs;
            } else if (owner == null) {
                // balle libre : glissade vers la balle
                if (c.glisse <= 0f && c.stun <= 0f && c.tacleCd <= 0f && dist(c.x, c.y, bx, by) < 70f) {
                    float dx = bx - c.x, dy = by - c.y;
                    float d = Math.max(1f, (float) Math.hypot(dx, dy));
                    c.glisse = 0.45f;
                    c.glisseTente = true;
                    c.tacleCd = 1.1f;
                    c.vx = dx / d * 235f * gs;
                    c.vy = dy / d * 235f * gs;
                }
            }
        }

        private void relacherTir() {
            if (charge && etat == JEU) {
                Joueur c = js[ctrl];
                if (owner == c) tirerHumain(c, 0.45f + 0.55f * chargeV);
            }
            charge = false;
            chargeV = 0f;
        }

        // ------------------------------------------------------------------ intelligence artificielle

        private void ia(float dt) {
            for (int i = 0; i < js.length; i++) {
                Joueur q = js[i];
                if (etat == JEU && i == ctrl) continue;
                if (etat == JEU && enLigne && hote && entreeDistanteActive && i == ctrlAdv) continue;
                q.ax = 0f;
                q.ay = 0f;
                q.sprint = false;
                q.plongeon = false;
                if (q.stun > 0f || q.glisse > 0f) continue;
                if (q.role == GK) {
                    iaGardien(q, dt);
                } else if (owner == q) {
                    porteur(q, dt);
                } else {
                    iaChamp(q);
                }
            }
        }

        private void iaChamp(Joueur q) {
            boolean eux = owner != null && owner.eq != q.eq;
            float tx, ty;
            boolean cours = false;
            if (owner == null) {
                if (attendu != null && attendu.eq == q.eq) {
                    if (q == attendu) {
                        tx = bx + bvx * 0.3f;
                        ty = by + bvy * 0.3f;
                        cours = true;
                    } else {
                        formation(q, true);
                        tx = cibleX;
                        ty = cibleY;
                    }
                } else if (q == plusProche(q.eq, bx + bvx * 0.25f, by + bvy * 0.25f, false)) {
                    tx = bx + bvx * 0.25f;
                    ty = by + bvy * 0.25f;
                    cours = true;
                } else {
                    formation(q, dernierToucheur != null && dernierToucheur.eq == q.eq);
                    tx = cibleX;
                    ty = cibleY;
                }
            } else if (eux) {
                if (q == plusProche(q.eq, owner.x, owner.y, false)) {
                    tx = owner.x + owner.dx * 10f;
                    ty = owner.y + owner.dy * 10f;
                    cours = true;
                } else {
                    formation(q, false);
                    tx = cibleX;
                    ty = cibleY;
                }
            } else {
                formation(q, true);
                tx = cibleX;
                ty = cibleY;
            }
            viser(q, tx, ty, cours);
        }

        private void formation(Joueur q, boolean attaque) {
            float dir = dirDe(q);
            float hx = q.eq == 0 ? q.hu * L : L - q.hu * L;
            float hy = q.eq == 0 ? q.hv * W : (1f - q.hv) * W;
            float dec = (bx - L / 2f) * 0.5f;
            if (attaque) dec += dir * (q.role == ATT ? 160f : q.role == MIL ? 105f : 55f);
            else dec -= dir * (q.role == ATT ? 40f : 0f);
            cibleX = borne(hx + dec, 90f, L - 90f);
            cibleY = borne(hy + (by - W / 2f) * 0.22f, 30f, W - 30f);
        }

        private void viser(Joueur q, float tx, float ty, boolean cours) {
            float dx = tx - q.x, dy = ty - q.y;
            float d = (float) Math.hypot(dx, dy);
            if (d < 4f) return;
            float f = Math.min(1f, d / 22f);
            q.ax = dx / d * f;
            q.ay = dy / d * f;
            q.sprint = (cours && d > 60f && q.stamina > 0.25f) || (d > 220f && q.stamina > 0.5f);
        }

        private void porteur(Joueur q, float dt) {
            float dir = dirDe(q);
            float gX = q.eq == 0 ? L : 0f;
            float dg = dist(q.x, q.y, gX, CY);
            q.decide -= dt;
            if (q.decide <= 0f) {
                q.decide = 0.22f + rnd.nextFloat() * 0.25f;
                float portee = pes ? 300f : 340f;
                if (dg < 115f || (dg < portee && Math.abs(q.y - CY) < 260f
                        && rnd.nextFloat() < 0.25f + 0.55f * (1f - dg / portee))) {
                    tirerIA(q);
                    return;
                }
                float dn = distAdversaire(q.x, q.y, q.eq);
                if (dn < (pes ? 42f : 36f) && rnd.nextFloat() < 0.75f) {
                    Joueur t = meilleurePasse(q, 0f, 0f, false);
                    if (t != null) {
                        passer(q, t, false);
                        return;
                    }
                } else if (rnd.nextFloat() < 0.10f) {
                    Joueur t = meilleurePasse(q, 0f, 0f, false);
                    if (t != null && dir * (t.x - q.x) > 50f) {
                        passer(q, t, false);
                        return;
                    }
                }
            }
            float tx = gX;
            float ty = q.y + (CY - q.y) * 0.35f;
            Joueur o = null;
            float bd = 1e9f;
            for (Joueur e : js) {
                if (e.eq == q.eq) continue;
                float d = dist(e.x, e.y, q.x, q.y);
                if (d < bd) {
                    bd = d;
                    o = e;
                }
            }
            if (o != null && bd < 60f && dir * (o.x - q.x) > -10f) ty += (q.y >= o.y ? 1f : -1f) * 70f;
            ty = borne(ty, 40f, W - 40f);
            viser(q, tx, ty, true);
            q.sprint = q.stamina > 0.3f && bd > 70f;
        }

        private Joueur meilleurePasse(Joueur q, float ux, float uy, boolean humainAction) {
            float dir = dirDe(q);
            Joueur best = null;
            float bs = -1e9f;
            float um = (float) Math.hypot(ux, uy);
            for (Joueur t : js) {
                if (t.eq != q.eq || t == q) continue;
                if (t.role == GK) continue;
                float d = dist(q.x, q.y, t.x, t.y);
                if (d < 35f || d > 480f) continue;
                float s = -d * 0.12f + dir * (t.x - q.x) * 0.45f;
                s += Math.min(distAdversaire(t.x, t.y, t.eq), 90f) * 0.9f;
                for (Joueur o : js) {
                    if (o.eq == q.eq) continue;
                    float ds = distSegment(o.x, o.y, q.x, q.y, t.x, t.y);
                    if (ds < 22f) s -= (22f - ds) * 7f;
                }
                if (humainAction && um > 0.2f) {
                    float cs = ((t.x - q.x) * ux + (t.y - q.y) * uy) / (d * um);
                    s += cs * 220f;
                    if (cs < 0.2f) s -= 200f;
                }
                if (s > bs) {
                    bs = s;
                    best = t;
                }
            }
            return best;
        }

        private void iaGardien(Joueur q, float dt) {
            float dirIn = q.eq == 0 ? 1f : -1f;
            float gx = q.eq == 0 ? 0f : L;
            if (owner == q) {
                q.hold -= dt;
                if (q.hold <= 0f) {
                    Joueur t = meilleurePasse(q, 0f, 0f, false);
                    if (t != null) passer(q, t, false);
                    else lancer(q, dirIn * 420f, (rnd.nextFloat() - 0.5f) * 200f, 150f);
                }
                return;
            }
            float tx, ty;
            float dBG = dist(bx, by, gx, CY);
            if (tir && tirEq != q.eq && bvx * dirIn < -30f) {
                float tt = (gx - bx) / bvx;
                float yp = borne(by + bvy * tt, CY - BUT_DEMI - 10f, CY + BUT_DEMI + 10f);
                tx = gx + dirIn * 14f;
                ty = yp;
                q.plongeon = true;
            } else if (owner == null && dBG < 150f && Math.hypot(bvx, bvy) < 120f
                    && plusProche(q.eq, bx, by, true) == q) {
                tx = bx;
                ty = by;
            } else {
                tx = gx + dirIn * (18f + Math.min(40f, dBG * 0.06f));
                ty = CY + borne((by - CY) * 0.3f, -60f, 60f);
            }
            viser(q, tx, ty, q.plongeon);
        }

        // ------------------------------------------------------------------ frappes

        private void lancer(Joueur q, float vx, float vy, float vz) {
            owner = null;
            dernierToucheur = q;
            q.lock = 0.35f;
            q.kick = 0.3f;
            bvx = vx;
            bvy = vy;
            bvz = vz;
            tir = false;
            attendu = null;
        }

        private void passer(Joueur q, Joueur t, boolean profond) {
            float tx = t.x, ty = t.y;
            if (profond) {
                tx = borne(t.x + dirDe(q) * 70f + t.vx * 0.5f, 20f, L - 20f);
                ty = borne(t.y + t.vy * 0.5f, 20f, W - 20f);
            }
            float d = dist(q.x, q.y, tx, ty);
            if (d < 1f) return;
            float v = borne(170f + d * 1.3f, 230f, 620f) * (pes ? 1f : 1.1f);
            float ang = (float) Math.atan2(ty - q.y, tx - q.x) + (rnd.nextFloat() - 0.5f) * (profond ? 0.10f : 0.05f);
            float vz = d > 330f ? 90f : 0f;
            lancer(q, (float) Math.cos(ang) * v, (float) Math.sin(ang) * v, vz);
            attendu = t;
            if (q.eq == 0 && t.role != GK) ctrl = indexDe(t);
            else if (q.eq == 1 && t.role != GK) ctrlAdv = indexDe(t);
        }

        private void tirerHumain(Joueur q, float puiss) {
            tirerHumain(q, puiss, jy);
        }

        private void tirerHumain(Joueur q, float puiss, float directionY) {
            float gX = q.eq == 0 ? L : 0f;
            float ty = CY + directionY * BUT_DEMI * 1.25f
                    + (rnd.nextFloat() - 0.5f) * BUT_DEMI * (1.1f - puiss) * 1.2f;
            tirer(q, gX, ty, puiss);
        }

        private void tirerIA(Joueur q) {
            float gX = q.eq == 0 ? L : 0f;
            float lat = (q.y < CY ? 1f : -1f) * BUT_DEMI * 0.75f;
            float prec = NIV_TIR[q.eq == 1 ? niveau : 1];
            float ty = CY + lat + (rnd.nextFloat() - 0.5f) * BUT_DEMI * 2.4f * (1.15f - prec);
            tirer(q, gX, ty, 0.55f + rnd.nextFloat() * 0.4f);
        }

        private void tirer(Joueur q, float gX, float ty, float puiss) {
            float d = Math.max(1f, dist(q.x, q.y, gX, ty));
            float v = (330f + 330f * puiss) * (pes ? 1f : 1.12f);
            float t = d / v;
            float zt = 4f + rnd.nextFloat() * 16f;
            if (puiss > 0.92f) zt += rnd.nextFloat() * 18f;
            float vz = (zt + 0.5f * G * t * t) / t;
            lancer(q, (gX - q.x) / d * v, (ty - q.y) / d * v, vz);
            tir = true;
            tirEq = q.eq;
            gkVerifie = false;
            blocVerifie = false;
        }

        // ------------------------------------------------------------------ déplacements des joueurs

        private void deplacer(float dt) {
            for (Joueur q : js) {
                q.lock = Math.max(0f, q.lock - dt);
                q.kick = Math.max(0f, q.kick - dt);
                q.tacleCd = Math.max(0f, q.tacleCd - dt);
                q.prot = Math.max(0f, q.prot - dt);
                if (q.stun > 0f) {
                    q.stun -= dt;
                    q.vx *= 0.88f;
                    q.vy *= 0.88f;
                    q.x += q.vx * dt;
                    q.y += q.vy * dt;
                    continue;
                }
                if (q.glisse > 0f) {
                    q.glisse -= dt;
                    q.vx *= 0.97f;
                    q.vy *= 0.97f;
                    q.x += q.vx * dt;
                    q.y += q.vy * dt;
                    if (q.glisse <= 0f) q.stun = 0.55f;
                    continue;
                }
                float vmax = 78f * gs * q.vit * (q.eq == 1 ? NIV_VIT[niveau] : 1f);
                if (q.role == GK) vmax = 70f * gs * (q.plongeon ? 2.6f : 1f);
                float mag = (float) Math.hypot(q.ax, q.ay);
                if (mag > 1f) {
                    q.ax /= mag;
                    q.ay /= mag;
                    mag = 1f;
                }
                boolean court = q.sprint && q.stamina > 0.05f && mag > 0.1f;
                float fat = 0.8f + 0.2f * q.stamina;
                float allure = (court ? 1.12f : 0.82f) * fat;
                if (court) q.stamina = Math.max(0f, q.stamina - dt * (pes ? 0.14f : 0.11f));
                else q.stamina = Math.min(1f, q.stamina + dt * 0.05f);
                float tvx = q.ax * vmax * allure, tvy = q.ay * vmax * allure;
                float acc = 460f * gs * dt;
                q.vx += borne(tvx - q.vx, -acc, acc);
                q.vy += borne(tvy - q.vy, -acc, acc);
                q.x += q.vx * dt;
                q.y += q.vy * dt;
                float sp = (float) Math.hypot(q.vx, q.vy);
                if (sp > 8f) {
                    float f = Math.min(1f, dt * 12f);
                    q.dx += (q.vx / sp - q.dx) * f;
                    q.dy += (q.vy / sp - q.dy) * f;
                    float n = (float) Math.hypot(q.dx, q.dy);
                    if (n > 0.001f) {
                        q.dx /= n;
                        q.dy /= n;
                    }
                }
                q.phase += sp * dt * 0.22f;
                q.x = borne(q.x, -25f, L + 25f);
                q.y = borne(q.y, -25f, W + 25f);
            }
            // les joueurs ne se traversent pas
            for (int i = 0; i < js.length; i++) {
                Joueur a = js[i];
                for (int j = i + 1; j < js.length; j++) {
                    Joueur b = js[j];
                    float dx = b.x - a.x, dy = b.y - a.y;
                    float d2 = dx * dx + dy * dy;
                    if (d2 < 81f && d2 > 0.0001f) {
                        float d = (float) Math.sqrt(d2);
                        float push = (9f - d) * 0.5f;
                        a.x -= dx / d * push;
                        a.y -= dy / d * push;
                        b.x += dx / d * push;
                        b.y += dy / d * push;
                    }
                }
            }
        }

        // ------------------------------------------------------------------ balle

        private void balle(float dt) {
            int n = Math.max(1, (int) Math.ceil(dt / 0.011f));
            float h = dt / n;
            for (int s = 0; s < n && etat == JEU; s++) pasBalle(h);
        }

        private void pasBalle(float h) {
            if (owner != null) {
                float ex = owner.x + owner.dx * 8.5f, ey = owner.y + owner.dy * 6.8f;
                float f = Math.min(1f, h * 22f);
                bx += (ex - bx) * f;
                by += (ey - by) * f;
                float sp = (float) Math.hypot(owner.vx, owner.vy);
                bz = Math.abs((float) Math.sin(owner.phase * 2f)) * 2.2f * Math.min(1f, sp / 70f);
                bvx = owner.vx;
                bvy = owner.vy;
                bvz = 0f;
                brot += sp * h * 0.08f;
                return;
            }
            bx += bvx * h;
            by += bvy * h;
            if (bz > 0f || bvz != 0f) {
                bvz -= G * h;
                bz += bvz * h;
                float dr = 1f - 0.15f * h;
                bvx *= dr;
                bvy *= dr;
                if (bz <= 0f) {
                    bz = 0f;
                    if (bvz < -90f) bvz = -bvz * 0.5f;
                    else bvz = 0f;
                    bvx *= 0.9f;
                    bvy *= 0.9f;
                }
            } else {
                float f = Math.max(0f, 1f - 1.0f * h);
                bvx *= f;
                bvy *= f;
            }
            float sp = (float) Math.hypot(bvx, bvy);
            if (sp > 900f) {
                bvx *= 900f / sp;
                bvy *= 900f / sp;
                sp = 900f;
            }
            brot += sp * h * 0.08f;

            verifierLimites();
            if (etat != JEU) return;

            // arrêt du gardien sur un tir
            if (tir && !gkVerifie) {
                Joueur g = gardien(1 - tirEq);
                if (bz < 36f && Math.abs(bx - g.x) < 20f && Math.abs(by - g.y) < 30f) {
                    gkVerifie = true;
                    float chance = (g.eq == 1 ? NIV_ARRET[niveau] : NIV_ARRET[1]) - (sp - 450f) / 900f
                            - Math.max(0f, Math.abs(by - g.y) - 14f) * 0.012f;
                    if (rnd.nextFloat() < chance) {
                        if (sp < 360f && bz < 28f) {
                            prendre(g);
                            g.hold = 0.8f + rnd.nextFloat() * 0.8f;
                        } else {
                            float dirIn = g.eq == 0 ? 1f : -1f;
                            bvx = dirIn * (30f + rnd.nextFloat() * 90f);
                            bvy = (rnd.nextFloat() - 0.5f) * 320f;
                            bvz = 120f + rnd.nextFloat() * 100f;
                            tir = false;
                            dernierToucheur = g;
                            g.lock = 0.4f;
                        }
                        return;
                    }
                }
            }
            // tir contré par un défenseur
            if (tir && !blocVerifie && bz < 18f) {
                for (Joueur q : js) {
                    if (q.eq == tirEq || q.role == GK) continue;
                    if (dist(q.x, q.y, bx, by) < 8f) {
                        blocVerifie = true;
                        if (rnd.nextFloat() < 0.4f) {
                            bvx = (bvx * -0.3f + (rnd.nextFloat() - 0.5f) * 120f) * 0.5f;
                            bvy += (rnd.nextFloat() - 0.5f) * 250f;
                            tir = false;
                            dernierToucheur = q;
                        }
                        break;
                    }
                }
            }
            // contrôle de la balle
            if (owner == null && bz < 13f) {
                Joueur best = null;
                float bd = 1e9f;
                for (Joueur q : js) {
                    if (q.stun > 0f || q.lock > 0f) continue;
                    float rr = 10.5f + (q.glisse > 0f ? 9f : 0f) + (q.role == GK ? 5f : 0f);
                    float d = dist(q.x, q.y, bx, by);
                    if (q == attendu) d *= 0.6f;
                    if (d < rr && d < bd) {
                        bd = d;
                        best = q;
                    }
                }
                if (best != null && !(tir && best.role == GK)) {
                    float v = (float) Math.hypot(bvx, bvy);
                    if (v < 480f || (best == attendu && v < 620f)) {
                        if (v > 330f && best != attendu && rnd.nextFloat() < 0.5f) {
                            bvx = bvx * 0.4f + (rnd.nextFloat() - 0.5f) * 100f;
                            bvy = bvy * 0.4f + (rnd.nextFloat() - 0.5f) * 100f;
                            best.lock = 0.3f;
                            tir = false;
                        } else {
                            prendre(best);
                        }
                    }
                }
            }
        }

        private void verifierLimites() {
            int touche = dernierToucheur == null ? 0 : dernierToucheur.eq;
            if (bx < 0f || bx > L) {
                boolean gauche = bx < 0f;
                if (Math.abs(by - CY) < BUT_DEMI && bz < BUT_H) {
                    marquer(gauche ? 1 : 0);
                    return;
                }
                int defenseur = gauche ? 0 : 1;
                if (touche == defenseur) arret(1 - defenseur, gauche ? 6f : L - 6f, by < CY ? 6f : W - 6f, 2);
                else arret(defenseur, gauche ? 45f : L - 45f, CY, 3);
                return;
            }
            if (by < 0f || by > W) arret(1 - touche, borne(bx, 20f, L - 20f), by < 0f ? 4f : W - 4f, 1);
        }

        // ------------------------------------------------------------------ tacles

        private void dispossess(Joueur o, Joueur q) {
            owner = null;
            o.lock = 0.5f;
            o.stun = 0.35f;
            float ax = q.x - o.x, ay = q.y - o.y;
            float d = Math.max(0.01f, (float) Math.hypot(ax, ay));
            bvx = ax / d * 110f + (rnd.nextFloat() - 0.5f) * 80f;
            bvy = ay / d * 110f + (rnd.nextFloat() - 0.5f) * 80f;
            bvz = 0f;
            dernierToucheur = q;
            tir = false;
            attendu = null;
        }

        private void tacles(float dt) {
            if (owner == null) return;
            Joueur o = owner;
            if (o.prot > 0f) return;
            for (Joueur q : js) {
                if (q.eq == o.eq || q.stun > 0f || q.role == GK) continue;
                float d = dist(q.x, q.y, o.x, o.y);
                float reach = 12.5f + (q.glisse > 0f ? 12f : 0f);
                if (d > reach) continue;
                if (q.glisse > 0f) {
                    if (!q.glisseTente) {
                        q.glisseTente = true;
                        if (rnd.nextFloat() < 0.62f) {
                            if (q.eq == 0 && rnd.nextFloat() < 0.12f) {
                                arret(o.eq, o.x, o.y, 4);
                            } else {
                                dispossess(o, q);
                            }
                            return;
                        }
                    }
                } else if (!humain(q) && q.tacleCd <= 0f) {
                    q.tacleCd = 0.7f;
                    float pr = NIV_TACLE[q.eq == 1 ? niveau : 1];
                    if (humain(o)) pr *= 0.85f;
                    if (o.sprint) pr *= 0.7f;
                    if (rnd.nextFloat() < pr) {
                        dispossess(o, q);
                        return;
                    }
                }
            }
        }

        // ------------------------------------------------------------------ projection (caméra TV)

        private float sc(float wy) {
            return S0 * (0.64f + 0.36f * (wy / W));
        }

        private float px(float wx, float wy) {
            return largeur / 2f + (wx - camX) * sc(wy);
        }

        private float py(float wy) {
            return Y0 + (Y1 - Y0) * (wy / W);
        }

        private float pyz(float wy, float z) {
            return py(wy) - z * sc(wy) * 0.9f;
        }

        private void ligne(Canvas c, float x1, float y1, float x2, float y2) {
            c.drawLine(px(x1, y1), py(y1), px(x2, y2), py(y2), p);
        }

        // ------------------------------------------------------------------ dessin

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            k = h / HAUT;
            largeur = w / k;
            S0 = largeur / etendue;
            tribune = new LinearGradient(0, 0, 0, Y0, 0xFF0A1226, 0xFF26324F, Shader.TileMode.CLAMP);
            ombreHerbe = new LinearGradient(0, Y0 - 12f, 0, Y1, 0x55000000, 0x00000000, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.034f, (now - dernier) / 1000f);
            dernier = now;
            boolean actif = etat == ENGAGE || etat == JEU || etat == BUT || etat == ARRET;
            if (actif && (!enLigne || hote)) maj(dt);
            if (enLigne && hote) publierInstantane();
            c.save();
            c.scale(k, k);
            dessinerTerrain(c);
            if (etat == MENU) {
                dessinerMenu(c);
            } else {
                dessinerEntites(c);
                dessinerHud(c);
                dessinerControles(c);
                dessinerMessages(c);
            }
            c.restore();
            if (actif) postInvalidateOnAnimation();
        }

        private void rect(Canvas c, float l, float t, float r, float b, int couleur) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(couleur);
            c.drawRect(l, t, r, b, p);
        }

        private void rrect(Canvas c, float l, float t, float r, float b, float rad, int couleur) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(couleur);
            rf.set(l, t, r, b);
            c.drawRoundRect(rf, rad, rad, p);
        }

        private void rond(Canvas c, float x, float y, float r, int couleur) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(couleur);
            c.drawCircle(x, y, r, p);
        }

        private void texte(Canvas c, String t, float x, float y, float taille, int couleur, Paint.Align al) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(couleur);
            p.setTextSize(taille);
            p.setTextAlign(al);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(t, x, y, p);
        }

        private void dessinerTerrain(Canvas c) {
            // tribunes
            p.setStyle(Paint.Style.FILL);
            p.setShader(tribune);
            c.drawRect(0f, 0f, largeur, Y0, p);
            p.setShader(null);
            for (int i = 0; i < foulX.length; i++) {
                float x = ((foulX[i] - camX * 0.35f) % 1400f + 1400f) % 1400f;
                if (x > largeur) continue;
                float y = 6f + foulY[i] * 40f;
                p.setColor(foulC[i]);
                c.drawRect(x, y, x + 3f, y + 3.4f, p);
            }
            // panneaux publicitaires (côté éloigné)
            int t0 = (int) Math.floor((camX - 480f) / 110f);
            for (int i = t0; i < t0 + 10; i++) {
                float x1 = i * 110f, x2 = x1 + 108f;
                float l = px(x1, -12f), r = px(x2, -12f);
                float bas = pyz(-12f, 0f), haut = pyz(-12f, 12f);
                rect(c, l, haut, r, bas, (i & 1) == 0 ? 0xFF14307A : 0xFF7A1420);
                if (r - l > 40f) texte(c, "KOUAMÉ FOOT", (l + r) / 2f, (haut + bas) / 2f + 2.5f, 7f * sc(-12f), 0xFFFFFFFF, Paint.Align.CENTER);
            }
            // pelouse en bandes
            int b0 = (int) Math.floor((camX - 700f) / 70f);
            for (int i = b0; i < b0 + 22; i++) {
                float x1 = i * 70f, x2 = x1 + 70f;
                p.setStyle(Paint.Style.FILL);
                p.setColor((i & 1) == 0 ? 0xFF2F8F3C : 0xFF37A046);
                path.reset();
                path.moveTo(px(x1, -12f), py(-12f));
                path.lineTo(px(x2, -12f), py(-12f));
                path.lineTo(px(x2, W + 80f), py(W + 80f));
                path.lineTo(px(x1, W + 80f), py(W + 80f));
                path.close();
                c.drawPath(path, p);
            }
            p.setShader(ombreHerbe);
            p.setStyle(Paint.Style.FILL);
            c.drawRect(0f, Y0 - 12f, largeur, HAUT, p);
            p.setShader(null);

            // lignes
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0xEEFFFFFF);
            p.setStrokeWidth(1.8f);
            ligne(c, 0f, 0f, L, 0f);
            ligne(c, 0f, W, L, W);
            ligne(c, 0f, 0f, 0f, W);
            ligne(c, L, 0f, L, W);
            ligne(c, L / 2f, 0f, L / 2f, W);
            ligne(c, 0f, CY - 201f, 165f, CY - 201f);
            ligne(c, 165f, CY - 201f, 165f, CY + 201f);
            ligne(c, 165f, CY + 201f, 0f, CY + 201f);
            ligne(c, 0f, CY - 91f, 55f, CY - 91f);
            ligne(c, 55f, CY - 91f, 55f, CY + 91f);
            ligne(c, 55f, CY + 91f, 0f, CY + 91f);
            ligne(c, L, CY - 201f, L - 165f, CY - 201f);
            ligne(c, L - 165f, CY - 201f, L - 165f, CY + 201f);
            ligne(c, L - 165f, CY + 201f, L, CY + 201f);
            ligne(c, L, CY - 91f, L - 55f, CY - 91f);
            ligne(c, L - 55f, CY - 91f, L - 55f, CY + 91f);
            ligne(c, L - 55f, CY + 91f, L, CY + 91f);
            // rond central et arcs de surface
            path.reset();
            for (int i = 0; i <= 40; i++) {
                float a = (float) (i * Math.PI * 2.0 / 40.0);
                float x = px(L / 2f + 91.5f * (float) Math.cos(a), CY + 91.5f * (float) Math.sin(a));
                float y = py(CY + 91.5f * (float) Math.sin(a));
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            c.drawPath(path, p);
            for (int cote = 0; cote < 2; cote++) {
                path.reset();
                for (int i = 0; i <= 16; i++) {
                    float a = -0.92f + i * (1.84f / 16f);
                    float wx = cote == 0 ? 110f + 91.5f * (float) Math.cos(a) : L - 110f - 91.5f * (float) Math.cos(a);
                    float wy = CY + 91.5f * (float) Math.sin(a);
                    if (i == 0) path.moveTo(px(wx, wy), py(wy));
                    else path.lineTo(px(wx, wy), py(wy));
                }
                c.drawPath(path, p);
            }
            p.setStyle(Paint.Style.FILL);
            rond(c, px(L / 2f, CY), py(CY), 2f, 0xEEFFFFFF);
            rond(c, px(110f, CY), py(CY), 1.8f, 0xEEFFFFFF);
            rond(c, px(L - 110f, CY), py(CY), 1.8f, 0xEEFFFFFF);

            dessinerBut(c, 0f, -1f);
            dessinerBut(c, L, 1f);
        }

        private void dessinerBut(Canvas c, float gx, float sortie) {
            float prof = 24f;
            float bx2 = gx + sortie * prof;
            float y1 = CY - BUT_DEMI, y2 = CY + BUT_DEMI;
            // filet
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x33FFFFFF);
            path.reset();
            path.moveTo(px(bx2, y1), pyz(y1, 0f));
            path.lineTo(px(bx2, y1), pyz(y1, BUT_H));
            path.lineTo(px(bx2, y2), pyz(y2, BUT_H));
            path.lineTo(px(bx2, y2), pyz(y2, 0f));
            path.close();
            c.drawPath(path, p);
            path.reset();
            path.moveTo(px(gx, y1), pyz(y1, BUT_H));
            path.lineTo(px(gx, y2), pyz(y2, BUT_H));
            path.lineTo(px(bx2, y2), pyz(y2, BUT_H));
            path.lineTo(px(bx2, y1), pyz(y1, BUT_H));
            path.close();
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.8f);
            p.setColor(0x66FFFFFF);
            for (int i = 1; i < 6; i++) {
                float z = BUT_H * i / 6f;
                c.drawLine(px(bx2, y1), pyz(y1, z), px(bx2, y2), pyz(y2, z), p);
                float yy = y1 + (y2 - y1) * i / 6f;
                c.drawLine(px(bx2, yy), pyz(yy, 0f), px(bx2, yy), pyz(yy, BUT_H), p);
            }
            c.drawLine(px(gx, y1), pyz(y1, 0f), px(bx2, y1), pyz(y1, 0f), p);
            c.drawLine(px(gx, y2), pyz(y2, 0f), px(bx2, y2), pyz(y2, 0f), p);
            // montants
            p.setColor(0xFFFFFFFF);
            p.setStrokeWidth(2.6f);
            c.drawLine(px(gx, y1), pyz(y1, 0f), px(gx, y1), pyz(y1, BUT_H), p);
            c.drawLine(px(gx, y2), pyz(y2, 0f), px(gx, y2), pyz(y2, BUT_H), p);
            c.drawLine(px(gx, y1), pyz(y1, BUT_H), px(gx, y2), pyz(y2, BUT_H), p);
            p.setStyle(Paint.Style.FILL);
        }

        private void dessinerEntites(Canvas c) {
            int n = js.length + 1;
            for (int i = 0; i < n; i++) ordre[i] = i;
            for (int i = 1; i < n; i++) {
                int v = ordre[i];
                float yv = v < js.length ? js[v].y : by;
                int j = i - 1;
                while (j >= 0) {
                    int o = ordre[j];
                    float yo = o < js.length ? js[o].y : by;
                    if (yo <= yv) break;
                    ordre[j + 1] = o;
                    j--;
                }
                ordre[j + 1] = v;
            }
            for (int i = 0; i < n; i++) {
                int v = ordre[i];
                if (v == js.length) dessinerBalle(c);
                else {
                    int controle = enLigne && !hote ? ctrlAdv : ctrl;
                    dessinerJoueur(c, js[v], v == controle && etat != MENU);
                }
            }
        }

        private void dessinerBalle(Canvas c) {
            float u = sc(by);
            float X = px(bx, by), Y = py(by);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x66000000);
            rf.set(X - 3.2f * u, Y - 1.1f * u, X + 3.2f * u, Y + 1.1f * u);
            c.drawOval(rf, p);
            float bY = Y - bz * u * 0.9f - 2.6f * u;
            float r = Math.max(2.4f, 2.9f * u);
            rond(c, X, bY, r, 0xFFFFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.7f);
            p.setColor(0xFF444444);
            c.drawCircle(X, bY, r, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF222222);
            c.drawCircle(X + (float) Math.cos(brot) * r * 0.5f, bY + (float) Math.sin(brot) * r * 0.5f, r * 0.32f, p);
            c.drawCircle(X - (float) Math.cos(brot) * r * 0.55f, bY - (float) Math.sin(brot) * r * 0.45f, r * 0.26f, p);
        }

        private int teinte(int couleur, float facteur) {
            int a = (couleur >>> 24) & 0xFF;
            int r = borne((int) (((couleur >>> 16) & 0xFF) * facteur), 0, 255);
            int g = borne((int) (((couleur >>> 8) & 0xFF) * facteur), 0, 255);
            int b = borne((int) ((couleur & 0xFF) * facteur), 0, 255);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        private void dessinerJoueur(Canvas c, Joueur q, boolean actif) {
            float u = sc(q.y);
            float X = px(q.x, q.y), Y = py(q.y);
            int maillot = q.eq == 0 ? tMoi : tAdv;
            int chemise = q.role == GK ? (q.eq == 0 ? 0xFFFF9800 : 0xFFAB47BC) : MAILLOT[maillot];
            int stripe = q.role == GK ? 0 : RAYURE[maillot];
            int shorts = q.role == GK ? 0xFF222222 : SHORT[maillot];
            int chaussures = q.role == GK ? 0xFF222222 : CHAUSS[maillot];
            int peau = PEAU[q.peau];
            int cheveux = q.peau > 2 ? 0xFF17120D : 0xFF3B2A1A;

            p.setStyle(Paint.Style.FILL);
            p.setColor(0x66000000);
            rf.set(X - 6.4f * u, Y - 1.5f * u, X + 6.4f * u, Y + 2.1f * u);
            c.drawOval(rf, p);
            if (actif) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.7f);
                p.setColor(0xFF00E5FF);
                rf.set(X - 8.2f * u, Y - 2.7f * u, X + 8.2f * u, Y + 2.7f * u);
                c.drawOval(rf, p);
            }

            float vitesse = (float) Math.hypot(q.vx, q.vy);
            float course = Math.min(1f, vitesse / (60f * gs));
            float balancement = (float) Math.sin(q.phase) * course;
            float taille = q.stun > 0f ? 0.9f : 1f;

            c.save();
            c.translate(X, Y);
            if (q.glisse > 0f) {
                c.rotate((float) Math.toDegrees(Math.atan2(q.dy, q.dx)));
                c.scale(u, u);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                // Pose basse de tacle, avec bras, chaussettes et chaussures visibles.
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(4.2f);
                p.setColor(teinte(chemise, 0.72f));
                c.drawLine(-7f, -4f, 2f, -5f, p);
                p.setStrokeWidth(3.5f);
                p.setColor(chemise);
                c.drawLine(-6.5f, -4.8f, 2.5f, -5.8f, p);
                p.setStrokeWidth(2.6f);
                p.setColor(peau);
                c.drawLine(-4.4f, -7f, -9f, -9f, p);
                c.drawLine(-1f, -2f, -6f, -1f, p);
                p.setStrokeWidth(3.2f);
                p.setColor(teinte(shorts, 0.9f));
                c.drawLine(-2f, -4.5f, -7f, 0.3f, p);
                p.setColor(0xFFF0F0F0);
                c.drawLine(-7f, 0.3f, -12f, 1f, p);
                p.setStrokeWidth(2.4f);
                p.setColor(chaussures);
                c.drawLine(-12f, 1f, -15f, 1.2f, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(teinte(peau, 0.72f));
                rf.set(2f, -8.5f, 8.8f, -1.8f);
                c.drawOval(rf, p);
                p.setColor(peau);
                rf.set(2.2f, -8.1f, 8f, -2.2f);
                c.drawOval(rf, p);
                p.setColor(cheveux);
                rf.set(1.8f, -8.8f, 8.3f, -4.3f);
                c.drawArc(rf, 180f, 180f, true, p);
                if (actif) {
                    p.setColor(0xFFFFEB3B);
                    path.reset();
                    path.moveTo(4f, -14f);
                    path.lineTo(10f, -14f);
                    path.lineTo(7f, -10.5f);
                    path.close();
                    c.drawPath(path, p);
                }
                c.restore();
                return;
            }

            float angle = (float) Math.toDegrees(Math.atan2(q.dy, q.dx)) + 90f;
            c.rotate(angle);
            c.scale(u, u * taille);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);

            // Jambes articulées : short, chaussettes à bandes et crampons.
            for (int s = -1; s <= 1; s += 2) {
                float lift = Math.max(0f, (float) Math.sin(q.phase + (s > 0 ? Math.PI : 0.0)))
                        * course * 2.6f;
                float swing = balancement * s * 2.5f;
                float hipX = s * 2.1f;
                float kneeX = s * 2.0f + swing;
                float kneeY = -7.0f - lift * 0.28f;
                float ankleX = s * 2.0f + swing * 1.8f;
                float ankleY = -1.7f - lift;
                if (q.kick > 0f && s > 0) {
                    ankleY -= 6f * (q.kick / 0.3f);
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(4.1f);
                p.setColor(teinte(shorts, 0.72f));
                c.drawLine(hipX + 0.45f, -13.3f, kneeX + 0.45f, kneeY, p);
                p.setStrokeWidth(3.4f);
                p.setColor(shorts);
                c.drawLine(hipX, -13.2f, kneeX, kneeY, p);
                p.setStrokeWidth(3.1f);
                p.setColor(teinte(0xFFECEFF1, 0.78f));
                c.drawLine(kneeX + 0.35f, kneeY, ankleX + 0.35f, ankleY, p);
                p.setStrokeWidth(2.7f);
                p.setColor(0xFFECEFF1);
                c.drawLine(kneeX, kneeY, ankleX, ankleY, p);
                p.setStrokeWidth(0.65f);
                p.setColor(stripe != 0 ? stripe : 0xFFB0BEC5);
                c.drawLine(kneeX - 1.0f, (kneeY + ankleY) * 0.48f,
                        ankleX - 1.0f, (kneeY + ankleY) * 0.48f, p);
                p.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(ankleX - 1.5f, ankleY + 0.7f);
                path.lineTo(ankleX + 1.5f, ankleY + 0.7f);
                path.lineTo(ankleX + 1.8f, ankleY - 1.7f);
                path.lineTo(ankleX + 0.8f, ankleY - 3.1f);
                path.lineTo(ankleX - 0.8f, ankleY - 3.1f);
                path.lineTo(ankleX - 1.8f, ankleY - 1.7f);
                path.close();
                p.setColor(teinte(chaussures, 0.62f));
                c.drawPath(path, p);
                p.setColor(chaussures);
                rf.set(ankleX - 1.35f, ankleY - 2.5f, ankleX + 1.35f, ankleY + 0.45f);
                c.drawRoundRect(rf, 0.7f, 0.7f, p);
                p.setColor(0xFFBDBDBD);
                c.drawLine(ankleX - 0.8f, ankleY - 1.2f, ankleX + 0.8f, ankleY - 1.2f, p);
            }

            // Cou et torse découpé, avec ombres latérales, col et détails de maillot.
            p.setStyle(Paint.Style.FILL);
            p.setColor(teinte(peau, 0.82f));
            rf.set(-1.8f, -26.2f, 1.8f, -22.8f);
            c.drawRoundRect(rf, 0.8f, 0.8f, p);
            path.reset();
            path.moveTo(-4.5f, -24.2f);
            path.lineTo(-6.2f, -22f);
            path.lineTo(-4.8f, -19.7f);
            path.lineTo(-4.1f, -14.1f);
            path.quadTo(0f, -12.5f, 4.1f, -14.1f);
            path.lineTo(4.8f, -19.7f);
            path.lineTo(6.2f, -22f);
            path.lineTo(4.5f, -24.2f);
            path.lineTo(2.1f, -23.3f);
            path.quadTo(0f, -22.1f, -2.1f, -23.3f);
            path.close();
            p.setColor(teinte(chemise, 0.68f));
            c.drawPath(path, p);
            path.reset();
            path.moveTo(-4.1f, -23.8f);
            path.lineTo(-5.4f, -21.8f);
            path.lineTo(-4.1f, -20f);
            path.lineTo(-3.7f, -14.3f);
            path.quadTo(0f, -13.1f, 3.7f, -14.3f);
            path.lineTo(4.1f, -20f);
            path.lineTo(5.4f, -21.8f);
            path.lineTo(4.1f, -23.8f);
            path.lineTo(1.8f, -22.8f);
            path.quadTo(0f, -21.8f, -1.8f, -22.8f);
            path.close();
            p.setColor(chemise);
            c.drawPath(path, p);
            p.setColor(teinte(chemise, 1.24f));
            path.reset();
            path.moveTo(-2.7f, -23.3f);
            path.lineTo(-1.2f, -22.4f);
            path.lineTo(-1.6f, -15.1f);
            path.lineTo(-3.0f, -14.8f);
            path.close();
            c.drawPath(path, p);
            if (stripe != 0) {
                p.setColor(stripe);
                for (int i = -2; i <= 2; i += 2) {
                    rf.set(i * 1.55f - 0.43f, -23.1f, i * 1.55f + 0.43f, -14.2f);
                    c.drawRoundRect(rf, 0.35f, 0.35f, p);
                }
            }
            p.setColor(teinte(shorts, 0.72f));
            rf.set(-4.3f, -14.8f, 4.3f, -11.7f);
            c.drawRoundRect(rf, 1.1f, 1.1f, p);
            p.setColor(shorts);
            path.reset();
            path.moveTo(-4.0f, -14.8f);
            path.lineTo(4.0f, -14.8f);
            path.lineTo(3.6f, -11.4f);
            path.lineTo(0.7f, -11.1f);
            path.lineTo(0f, -12.4f);
            path.lineTo(-0.7f, -11.1f);
            path.lineTo(-3.6f, -11.4f);
            path.close();
            c.drawPath(path, p);
            p.setColor(teinte(shorts, 1.28f));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.7f);
            c.drawLine(-3.1f, -13.8f, -1.0f, -13.7f, p);

            // Bras pliés, manches courtes et avant-bras visibles.
            for (int s = -1; s <= 1; s += 2) {
                float epauleX = s * 5.0f;
                float epauleY = -21.5f;
                float coudeX = s * (6.2f + balancement * 1.2f);
                float coudeY = -18.2f - Math.abs(balancement) * 0.45f;
                float mainX = s * (5.2f + balancement * 2.2f);
                float mainY = -15.2f + balancement * s * 1.1f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3.6f);
                p.setColor(teinte(chemise, 0.72f));
                c.drawLine(epauleX, epauleY, coudeX, coudeY, p);
                p.setStrokeWidth(2.8f);
                p.setColor(chemise);
                c.drawLine(epauleX, epauleY, coudeX, coudeY, p);
                p.setStrokeWidth(2.6f);
                p.setColor(teinte(peau, 0.75f));
                c.drawLine(coudeX, coudeY, mainX, mainY, p);
                p.setStrokeWidth(1.8f);
                p.setColor(peau);
                c.drawLine(coudeX, coudeY, mainX, mainY, p);
                p.setStyle(Paint.Style.FILL);
                rond(c, mainX, mainY, 1.15f, peau);
            }

            // Visage, oreilles et cheveux; la tête tourne avec le sens de course.
            p.setStyle(Paint.Style.FILL);
            p.setColor(teinte(peau, 0.72f));
            rf.set(-3.0f, -32.6f, 3.0f, -25.8f);
            c.drawOval(rf, p);
            p.setColor(peau);
            rf.set(-2.55f, -32.1f, 2.55f, -26.0f);
            c.drawOval(rf, p);
            rond(c, -2.7f, -28.8f, 0.75f, teinte(peau, 0.88f));
            rond(c, 2.7f, -28.8f, 0.75f, teinte(peau, 0.88f));
            p.setColor(cheveux);
            path.reset();
            path.moveTo(-2.7f, -29.8f);
            path.quadTo(-3.5f, -33.4f, 0f, -33.2f);
            path.quadTo(3.2f, -33.1f, 2.7f, -29.5f);
            path.lineTo(1.8f, -30.1f);
            path.quadTo(0f, -29.0f, -1.7f, -30.1f);
            path.close();
            c.drawPath(path, p);
            p.setColor(0xFF251A13);
            rond(c, -0.9f, -29.7f, 0.28f, p.getColor());
            rond(c, 0.9f, -29.7f, 0.28f, p.getColor());
            p.setColor(teinte(peau, 0.65f));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.55f);
            c.drawLine(0f, -29.4f, 0f, -28.5f, p);

            if (u > 0.9f) {
                int numero = (chemise == 0xFFF2F2F2 || chemise == 0xFFF5D000
                        || chemise == 0xFF7EC8F0) ? 0xFF111111 : 0xFFFFFFFF;
                texte(c, String.valueOf(q.num), 0f, -17.0f, 3.7f, numero, Paint.Align.CENTER);
            }
            if (actif) {
                float b = (float) Math.sin(temps * 6f) * 1.1f;
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFEB3B);
                path.reset();
                path.moveTo(-2.7f, -38.0f + b);
                path.lineTo(2.7f, -38.0f + b);
                path.lineTo(0f, -34.7f + b);
                path.close();
                c.drawPath(path, p);
            }
            c.restore();
        }

        private void dessinerHud(Canvas c) {
            float cx = largeur / 2f;
            rect(c, 0f, 0f, largeur, 26f, 0xCC0B1B3A);
            texte(c, pes ? "FOOT PRO KOUAMÉ" : "FOOTBALL KOUAMÉ", 8f, 17f, 9f, 0xFFB0BEC5, Paint.Align.LEFT);
            int equipeGauche = enLigne && !hote ? 1 : 0;
            int equipeDroite = 1 - equipeGauche;
            int maillotGauche = equipeGauche == 0 ? tMoi : tAdv;
            int maillotDroite = equipeDroite == 0 ? tMoi : tAdv;
            int scoreGauche = equipeGauche == 0 ? butsMoi : butsAdv;
            int scoreDroite = equipeDroite == 0 ? butsMoi : butsAdv;
            rect(c, cx - 86f, 7f, cx - 70f, 19f, MAILLOT[maillotGauche]);
            if (RAYURE[maillotGauche] != 0) rect(c, cx - 80f, 7f, cx - 76f, 19f, RAYURE[maillotGauche]);
            rect(c, cx + 70f, 7f, cx + 86f, 19f, MAILLOT[maillotDroite]);
            if (RAYURE[maillotDroite] != 0) rect(c, cx + 76f, 7f, cx + 80f, 19f, RAYURE[maillotDroite]);
            texte(c, CODE[maillotGauche], cx - 38f, 18f, 11f, 0xFFFFFFFF, Paint.Align.RIGHT);
            texte(c, CODE[maillotDroite], cx + 38f, 18f, 11f, 0xFFFFFFFF, Paint.Align.LEFT);
            rrect(c, cx - 32f, 4f, cx - 2f, 22f, 3f, 0xFF18B8A8);
            rrect(c, cx + 2f, 4f, cx + 32f, 22f, 3f, 0xFF18B8A8);
            texte(c, String.valueOf(scoreGauche), cx - 17f, 18f, 13f, 0xFFFFFFFF, Paint.Align.CENTER);
            texte(c, String.valueOf(scoreDroite), cx + 17f, 18f, 13f, 0xFFFFFFFF, Paint.Align.CENTER);
            if (enLigne) texte(c, "VS " + nomAdversaire, largeur - 46f, 17f, 8f, 0xFF80CBC4, Paint.Align.RIGHT);
            float fin = demi + extraMin * demi / 45f;
            float gsec = (mi == 0 ? 0f : 2700f) + Math.min(tMi, fin) / demi * 2700f;
            int m = (int) (gsec / 60f), s = (int) (gsec % 60f);
            rrect(c, cx + 94f, 4f, cx + 156f, 22f, 3f, 0xFF000000);
            texte(c, (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s, cx + 125f, 18f, 12f, 0xFFFF3D3D, Paint.Align.CENTER);
            if (tMi >= demi) {
                rrect(c, cx + 160f, 4f, cx + 188f, 22f, 3f, 0xFF000000);
                texte(c, "+" + extraMin, cx + 174f, 18f, 11f, 0xFFFFFFFF, Paint.Align.CENTER);
            }
            // pause
            rrect(c, largeur - 34f, 32f, largeur - 8f, 58f, 5f, 0xAA000000);
            rect(c, largeur - 26f, 38f, largeur - 22f, 52f, 0xFFFFFFFF);
            rect(c, largeur - 18f, 38f, largeur - 14f, 52f, 0xFFFFFFFF);
        }

        private void bouton(Canvas c, float x, float y, float r, String l1, String l2, boolean appuye) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(appuye ? 0xAA37474F : 0x88101820);
            c.drawCircle(x, y, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(appuye ? 0xFFFFFFFF : 0x99B0BEC5);
            c.drawCircle(x, y, r, p);
            if (l2 == null) {
                texte(c, l1, x, y + 3f, 8.5f, 0xFFFFFFFF, Paint.Align.CENTER);
            } else {
                texte(c, l1, x, y - 1f, 8.5f, 0xFFFFFFFF, Paint.Align.CENTER);
                texte(c, l2, x, y + 9f, 8.5f, 0xFFFFFFFF, Paint.Align.CENTER);
            }
        }

        private void dessinerControles(Canvas c) {
            int equipeLocale = enLigne && !hote ? 1 : 0;
            boolean att = owner != null && owner.eq == equipeLocale;
            // stick
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x55101820);
            c.drawCircle(jcx, jcy, 48f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(0x66B0BEC5);
            c.drawCircle(jcx, jcy, 48f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xCCECEFF1);
            c.drawCircle(jcx + jx * 34f, jcy + jy * 34f, 24f, p);
            bouton(c, largeur - 52f, 222f, 32f, att ? "TIR" : "GLISSADE", null, shootId >= 0);
            bouton(c, largeur - 132f, 246f, 30f, att ? "LONGUE" : "CHANGER", null, false);
            bouton(c, largeur - 160f, 312f, 32f, att ? "PASSE" : "TACLE", null, false);
            bouton(c, largeur - 60f, 305f, 38f, "SPRINT", null, sprintId >= 0);
            boolean chargeAffichee = enLigne && !hote ? distantTirCharge : charge;
            float progressionTir = enLigne && !hote ? distantChargeV : chargeV;
            if (chargeAffichee) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(4f);
                p.setColor(0xFFFFEB3B);
                rf.set(largeur - 52f - 36f, 222f - 36f, largeur - 52f + 36f, 222f + 36f);
                c.drawArc(rf, -90f, 360f * progressionTir, false, p);
            }
        }

        private void dessinerMessages(Canvas c) {
            float cx = largeur / 2f;
            if (etat == ENGAGE || etat == BUT || etat == ARRET) {
                float echelle = etat == BUT ? 1.5f : 1f;
                texte(c, msg, cx + 1.5f, 141.5f, 30f * echelle, 0xAA000000, Paint.Align.CENTER);
                texte(c, msg, cx, 140f, 30f * echelle, etat == BUT ? 0xFFFFEB3B : 0xFFFFFFFF, Paint.Align.CENTER);
                if (etat == BUT && buteur != null) {
                    texte(c, CODE[buteur.eq == 0 ? tMoi : tAdv] + "  n° " + buteur.num, cx, 175f, 14f, 0xFFFFFFFF, Paint.Align.CENTER);
                }
            } else if (etat == MITEMPS || etat == FIN || etat == PAUSE) {
                rect(c, 0f, 0f, largeur, HAUT, 0xAA000000);
                String titre = etat == PAUSE ? "PAUSE" : msg;
                texte(c, titre, cx, 120f, 30f, 0xFFFFEB3B, Paint.Align.CENTER);
                if (etat != PAUSE) {
                    int local = enLigne && !hote ? 1 : 0;
                    int butsLocaux = local == 0 ? butsMoi : butsAdv;
                    int butsAdverses = local == 0 ? butsAdv : butsMoi;
                    int equipeLocale = local == 0 ? tMoi : tAdv;
                    int equipeAdverse = local == 0 ? tAdv : tMoi;
                    texte(c, NOM[equipeLocale] + "  " + butsLocaux + " - " + butsAdverses + "  "
                            + NOM[equipeAdverse], cx, 160f, 18f, 0xFFFFFFFF, Paint.Align.CENTER);
                }
                if (etat == FIN) {
                    int local = enLigne && !hote ? 1 : 0;
                    int butsLocaux = local == 0 ? butsMoi : butsAdv;
                    int butsAdverses = local == 0 ? butsAdv : butsMoi;
                    texte(c, butsLocaux > butsAdverses ? "VICTOIRE !" : butsLocaux == butsAdverses ? "MATCH NUL" : "DÉFAITE", cx, 195f, 16f,
                            butsLocaux > butsAdverses ? 0xFF69F0AE : 0xFFFFFFFF, Paint.Align.CENTER);
                    if (enLigne) {
                        texte(c, etatReseau, cx, 222f, 13f, 0xFFB0BEC5, Paint.Align.CENTER);
                        texte(c, "Touche l’écran pour quitter le match", cx, 280f, 12f, 0xFFFFFFFF, Paint.Align.CENTER);
                    } else {
                        texte(c, "Score : " + dernierPoints + " pts   (record " + record + ")", cx, 222f, 13f, 0xFFB0BEC5, Paint.Align.CENTER);
                        texte(c, "Touche l'écran pour revenir au menu", cx, 280f, 12f, 0xFFFFFFFF, Paint.Align.CENTER);
                    }
                } else if (etat == MITEMPS) {
                    texte(c, enLigne && !hote ? "En attente de l'hôte pour reprendre" :
                            "Touche l'écran pour la 2e mi-temps", cx, 250f, 13f, 0xFFFFFFFF, Paint.Align.CENTER);
                } else {
                    texte(c, "Touche l'écran pour reprendre", cx, 190f, 14f, 0xFFFFFFFF, Paint.Align.CENTER);
                    rrect(c, 12f, 308f, 112f, 342f, 8f, 0xFFB71C1C);
                    texte(c, "QUITTER", 62f, 330f, 12f, 0xFFFFFFFF, Paint.Align.CENTER);
                }
            }
        }

        // ------------------------------------------------------------------ menu

        private void kit(Canvas c, float x, float y, int eq) {
            rect(c, x - 15f, y - 9f, x - 9f, y - 2f, MAILLOT[eq]);
            rect(c, x + 9f, y - 9f, x + 15f, y - 2f, MAILLOT[eq]);
            rrect(c, x - 11f, y - 12f, x + 11f, y + 10f, 3f, MAILLOT[eq]);
            if (RAYURE[eq] != 0) {
                for (int i = -1; i <= 1; i++) rect(c, x + i * 6f - 1.8f, y - 11f, x + i * 6f + 1.8f, y + 9f, RAYURE[eq]);
            }
        }

        private void dessinerMenu(Canvas c) {
            float cx = largeur / 2f;
            rect(c, 0f, 0f, largeur, HAUT, 0xCC06101F);
            if (enLigne && !hote) {
                texte(c, pes ? "FOOT PRO EN LIGNE" : "FOOTBALL EN LIGNE",
                        cx, 110f, 22f, 0xFFFFEB3B, Paint.Align.CENTER);
                texte(c, "Connexion au match de " + nomAdversaire + "…",
                        cx, 148f, 13f, 0xFFFFFFFF, Paint.Align.CENTER);
                texte(c, etatReseau, cx, 174f, 10f, 0xFFB0BEC5, Paint.Align.CENTER);
                return;
            }
            texte(c, pes ? "FOOT PRO KOUAMÉ" : "FOOTBALL KOUAMÉ", cx, 52f, 28f, 0xFFFFEB3B, Paint.Align.CENTER);
            texte(c, pes ? "Simulation 11 contre 11 · matchs plus longs, jeu posé"
                    : "Arcade 6 contre 6 · matchs rapides et spectaculaires", cx, 72f, 10f, 0xFFB0BEC5, Paint.Align.CENTER);
            texte(c, "‹ Retour", 12f, 22f, 12f, 0xFFFFFFFF, Paint.Align.LEFT);
            texte(c, "Record : " + record + " pts", largeur - 12f, 22f, 11f, 0xFFFFD54F, Paint.Align.RIGHT);
            String[] lab = {"TON ÉQUIPE", "ADVERSAIRE", "NIVEAU"};
            float[] ry = {122f, 182f, 242f};
            for (int i = 0; i < 3; i++) {
                texte(c, lab[i], cx, ry[i] - 20f, 10f, 0xFF90A4AE, Paint.Align.CENTER);
                rrect(c, cx - 150f, ry[i] - 14f, cx + 150f, ry[i] + 18f, 8f, 0x55FFFFFF);
                texte(c, "◀", cx - 132f, ry[i] + 7f, 18f, 0xFFFFFFFF, Paint.Align.CENTER);
                texte(c, "▶", cx + 132f, ry[i] + 7f, 18f, 0xFFFFFFFF, Paint.Align.CENTER);
                if (i == 2) {
                    texte(c, NIV_NOM[niveau], cx, ry[i] + 7f, 16f, 0xFFFFFFFF, Paint.Align.CENTER);
                } else {
                    int eq = i == 0 ? tMoi : tAdv;
                    kit(c, cx - 78f, ry[i] + 2f, eq);
                    texte(c, NOM[eq], cx - 56f, ry[i] + 7f, 15f, 0xFFFFFFFF, Paint.Align.LEFT);
                }
            }
            rrect(c, cx - 95f, 264f, cx + 95f, 298f, 9f, 0xFFFF6B35);
            texte(c, "COUP D'ENVOI · IA", cx, 287f, 15f, 0xFFFFFFFF, Paint.Align.CENTER);
            rrect(c, cx - 115f, 303f, cx + 115f, 337f, 9f, 0xFF187A64);
            texte(c, "JOUER EN LIGNE · HUMAIN", cx, 326f, 14f, 0xFFFFFFFF, Paint.Align.CENTER);
            texte(c, "Stick · TIR · PASSE · SPRINT · DÉFENSE", cx, 352f, 8f, 0xFF90A4AE, Paint.Align.CENTER);
        }

        private void toucheMenu(float x, float y) {
            float cx = largeur / 2f;
            if (x < 90f && y < 36f) {
                ((Activity) ctx).finish();
                return;
            }
            float[] ry = {122f, 182f, 242f};
            for (int i = 0; i < 3; i++) {
                if (Math.abs(y - (ry[i] + 2f)) < 22f) {
                    int sens = x < cx ? -1 : 1;
                    if (Math.abs(x - cx) < 40f) sens = 1;
                    if (i == 0) {
                        tMoi = (tMoi + sens + CODE.length) % CODE.length;
                        if (tMoi == tAdv) tMoi = (tMoi + sens + CODE.length) % CODE.length;
                    } else if (i == 1) {
                        tAdv = (tAdv + sens + CODE.length) % CODE.length;
                        if (tAdv == tMoi) tAdv = (tAdv + sens + CODE.length) % CODE.length;
                    } else {
                        niveau = (niveau + sens + 3) % 3;
                    }
                    invalidate();
                    return;
                }
            }
            if (x > cx - 100f && x < cx + 100f && y > 260f && y < 301f) {
                demarrerMatch();
                return;
            }
            if (x > cx - 120f && x < cx + 120f && y > 300f && y < 341f) {
                ((Activity) ctx).startActivity(new android.content.Intent(ctx, FootEnLigneActivity.class)
                        .putExtra("mode", pes ? "pes" : "foot"));
            }
        }

        // ------------------------------------------------------------------ toucher

        private void toucheJeu(int id, float x, float y) {
            if (x > largeur - 40f && y > 28f && y < 62f) {
                if (enLigne) {
                    quitterEnLigne();
                    ((Activity) ctx).finish();
                } else {
                    pauser();
                }
                return;
            }
            float[][] b = {{largeur - 52f, 222f, 32f}, {largeur - 132f, 246f, 30f}, {largeur - 160f, 312f, 32f}, {largeur - 60f, 305f, 38f}};
            int hit = -1;
            float meilleur = 1e9f;
            for (int i = 0; i < 4; i++) {
                float d = dist(x, y, b[i][0], b[i][1]);
                if (d <= b[i][2] * 1.25f && d < meilleur) {
                    meilleur = d;
                    hit = i;
                }
            }
            if (hit == 0) {
                shootId = id;
                if (enLigne && !hote) envoyerAction(3);
                else pressionTir();
            } else if (hit == 1) {
                if (enLigne && !hote) envoyerAction(2);
                else actionPasse(true);
            } else if (hit == 2) {
                if (enLigne && !hote) envoyerAction(1);
                else actionPasse(false);
            } else if (hit == 3) {
                sprintId = id;
                sprint = true;
                if (enLigne && !hote) entreeSprint = true;
            } else if (x < largeur * 0.45f && joyId < 0) {
                joyId = id;
                jcx = borne(x, 52f, largeur * 0.4f);
                jcy = borne(y, 110f, 312f);
                jx = 0f;
                jy = 0f;
                if (enLigne && !hote) {
                    entreeJx = 0f;
                    entreeJy = 0f;
                }
            }
        }

        private void envoyerAction(int action) {
            entreeAction = action;
            entreeSeq++;
        }

        private void deplacementStick(float x, float y) {
            float dx = x - jcx, dy = y - jcy;
            float d = (float) Math.hypot(dx, dy);
            if (d > 56f) {
                jcx += dx / d * (d - 56f);
                jcy += dy / d * (d - 56f);
                jcx = borne(jcx, 52f, largeur * 0.42f);
                jcy = borne(jcy, 110f, 312f);
                dx = x - jcx;
                dy = y - jcy;
                d = (float) Math.hypot(dx, dy);
            }
            float m = Math.min(1f, d / 44f);
            if (m < 0.12f || d < 0.01f) {
                jx = 0f;
                jy = 0f;
            } else {
                jx = dx / d * m;
                jy = dy / d * m;
            }
            if (enLigne && !hote) {
                entreeJx = jx;
                entreeJy = jy;
            }
        }

        private void leve(int id) {
            if (id == joyId) {
                joyId = -1;
                jx = 0f;
                jy = 0f;
                jcx = 82f;
                jcy = 288f;
            }
            if (id == sprintId) {
                sprintId = -1;
                sprint = false;
                if (enLigne && !hote) entreeSprint = false;
            }
            if (id == shootId) {
                shootId = -1;
                if (enLigne && !hote) envoyerAction(4);
                else relacherTir();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            int ai = e.getActionIndex();
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
                float x = e.getX(ai) / k, y = e.getY(ai) / k;
                int id = e.getPointerId(ai);
                if (etat == MENU) {
                    if (enLigne && !hote) invalidate();
                    else toucheMenu(x, y);
                } else if (etat == MITEMPS) {
                    if (SystemClock.uptimeMillis() - tEtat > 700L && (!enLigne || hote)) {
                        secondeMiTemps();
                        invalidate();
                    }
                } else if (etat == FIN) {
                    if (SystemClock.uptimeMillis() - tEtat > 700L) {
                        if (enLigne) {
                            quitterEnLigne();
                            ((Activity) ctx).finish();
                        } else {
                            etat = MENU;
                            invalidate();
                        }
                    }
                } else if (etat == PAUSE) {
                    if (x < 120f && y > 300f) {
                        etat = MENU;
                    } else {
                        etat = avant;
                        dernier = SystemClock.uptimeMillis();
                    }
                    invalidate();
                } else {
                    toucheJeu(id, x, y);
                }
            } else if (a == MotionEvent.ACTION_MOVE) {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    if (e.getPointerId(i) == joyId) deplacementStick(e.getX(i) / k, e.getY(i) / k);
                }
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP) {
                leve(e.getPointerId(ai));
            } else if (a == MotionEvent.ACTION_CANCEL) {
                charge = false;
                relacherTout();
            }
            return true;
        }
    }
}
