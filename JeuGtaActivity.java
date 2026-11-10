package bj.radio;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Random;

/**
 * GTA Kouamé : jeu de ville en monde ouvert, vu de dessus (style GTA 1 / 2).
 * - Grande ville de 7 x 7 blocs : routes, trottoirs, parcs, bâtiments bloquants, jour et nuit.
 * - Circulation qui roule dans ses voies, piétons qui se promènent.
 * - On peut voler n'importe quelle voiture (bouton ENTRER), écraser, tirer au pistolet (visée assistée).
 * - Recherché de 1 à 5 étoiles : la police arrive, fonce sur toi et tire à partir de 3 étoiles.
 * - Missions de livraison avec chronomètre, sacs d'argent, munitions et soins ramassés dans la rue.
 * - Mini-carte, joystick à gauche, boutons à droite. Le score est l'argent gagné (record gardé, clé « gta »).
 * Les images (gta_*.png) sont des dessins originaux, à remplacer par les vôtres sous les mêmes noms.
 */
public class JeuGtaActivity extends Activity {

    private VueGta vue;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersif();
        vue = new VueGta(this);
        setContentView(vue);
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
        if (vue != null) vue.pauser();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (vue != null) vue.reprendre();
    }

    private static final class VueGta extends View {

        // ---------------------------------------------------------------- carte
        // 0 = parc (herbe), 1 = route, 2/3/4 = bâtiments (bloquants), 5 = trottoir
        private static final int TS = 64;
        private static final int PER = 8;                 // 2 routes + 6 tuiles de bloc
        private static final int BLOCS = 7;
        private static final int NT = PER * BLOCS + 2;    // 58 tuiles : routes sur les 4 bords
        private static final float PAS = 1000f / 60f;     // un pas de jeu = 1/60 s
        private static final float VIT_PIETON = 3.4f;

        private final byte[][] carte = new byte[NT][NT];

        // ---------------------------------------------------------------- objets du monde
        private static final class Voiture {
            float x, y, ang, vit, vie = 100f, maxV = 8.5f;
            int dir, inter = -1, bloqueT, recul, tir, idle, feu, couleur;
            float voie;
            boolean ia = true, police, epave, joueur;
        }

        private static final class Pieton {
            float x, y, ang, t, vit = 1.1f;
            int etat, col;        // etat : 0 marche, 1 fuite, 2 à terre
        }

        private static final class Objet {
            float x, y;
            int type, val;        // type : 0 argent, 1 munitions, 2 soin
            Objet(float x, float y, int type, int val) { this.x = x; this.y = y; this.type = type; this.val = val; }
        }

        private static final class Balle {
            float x, y, dx, dy;
            int vie;
            boolean joueur;
            Balle(float x, float y, float dx, float dy, int vie, boolean joueur) {
                this.x = x; this.y = y; this.dx = dx; this.dy = dy; this.vie = vie; this.joueur = joueur;
            }
        }

        private static final class Effet {
            float x, y;
            int t;
            Effet(float x, float y) { this.x = x; this.y = y; }
        }

        private final ArrayList<Voiture> voitures = new ArrayList<>();
        private final ArrayList<Pieton> pietons = new ArrayList<>();
        private final ArrayList<Objet> objets = new ArrayList<>();
        private final ArrayList<Balle> balles = new ArrayList<>();
        private final ArrayList<Effet> effets = new ArrayList<>();

        // ---------------------------------------------------------------- outils de dessin
        private final Context ctx;
        private final float d;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pPixel = new Paint();
        private final Paint[] teintes = new Paint[8];
        private final Paint[] teintesPieton = new Paint[6];
        private final Paint pEpave = new Paint();
        private final RectF r = new RectF();
        private final Path chemin = new Path();
        private final Random rnd = new Random();
        private final Bitmap sol, route, b1, b2, b3, sprJoueur, sprVoiture;

        private static final int[] COULEURS_VOITURES = {
                0xFFFFFFFF, 0xFFE53935, 0xFF1E88E5, 0xFFFDD835, 0xFF43A047, 0xFF8E24AA, 0xFFFB8C00, 0xFF5A5F66};
        private static final int[] COULEURS_PIETONS = {
                0xFFFFFFFF, 0xFFE57373, 0xFF64B5F6, 0xFFFFD54F, 0xFF81C784, 0xFFBA68C8};

        // ---------------------------------------------------------------- joueur
        private float jx = TS * 4.5f, jy = TS * 4.5f, jdx = 0, jdy = -1;
        private float jvie = 100f;
        private Voiture voitJ;
        private int argent = 200, gagne, munitions = 30, record;
        private int delaiTir, cdDegat, mortT;
        private float ptsRech;
        private boolean nouveauRecord;

        // ---------------------------------------------------------------- mission
        private int mEtat, mDelai = 150, mTemps;
        private float mAx, mAy, mBx, mBy;

        // ---------------------------------------------------------------- commandes
        private int joyId = -1, tirId = -1, freinId = -1;
        private float joyOx, joyOy, joyX, joyY;
        private float aX, aY, aR, tX, tY, tR;

        // ---------------------------------------------------------------- caméra / temps
        private float echelle = 1f, camX, camY;
        private boolean actif = true;
        private long dernier;
        private float cumul;
        private int temps;
        private String message = "";
        private long messageFin;

        VueGta(Context c) {
            super(c);
            ctx = c;
            d = c.getResources().getDisplayMetrics().density;
            pPixel.setFilterBitmap(false);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            sol = BitmapFactory.decodeResource(getResources(), R.drawable.gta_tile_ground, o);
            route = BitmapFactory.decodeResource(getResources(), R.drawable.gta_tile_road, o);
            b1 = BitmapFactory.decodeResource(getResources(), R.drawable.gta_tile_bldg1, o);
            b2 = BitmapFactory.decodeResource(getResources(), R.drawable.gta_tile_bldg2, o);
            b3 = BitmapFactory.decodeResource(getResources(), R.drawable.gta_tile_bldg3, o);
            sprJoueur = BitmapFactory.decodeResource(getResources(), R.drawable.gta_player, o);
            sprVoiture = BitmapFactory.decodeResource(getResources(), R.drawable.gta_car_white, o);
            for (int i = 0; i < teintes.length; i++) {
                teintes[i] = new Paint();
                teintes[i].setFilterBitmap(false);
                teintes[i].setColorFilter(new LightingColorFilter(COULEURS_VOITURES[i], 0));
            }
            for (int i = 0; i < teintesPieton.length; i++) {
                teintesPieton[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
                teintesPieton[i].setColorFilter(new LightingColorFilter(COULEURS_PIETONS[i], 0));
            }
            pEpave.setColorFilter(new LightingColorFilter(0xFF303030, 0));

            record = JeuxActivity.record(c, "gta");
            genererCarte();

            // ma voiture, garée sur la route à côté du parc de départ
            Voiture v = new Voiture();
            v.x = TS * 5.5f;
            v.y = TS * 1.5f;
            v.ia = false;
            v.couleur = 1;
            voitures.add(v);

            for (int i = 0; i < 16; i++) spawnCivile(260f, 1700f);
            for (int i = 0; i < 28; i++) spawnPieton(220f, 1500f);
            for (int i = 0; i < 22; i++) spawnObjet(200f, 1800f);
            dernier = SystemClock.uptimeMillis();
        }

        void pauser() {
            actif = false;
            if (gagne > 0) JeuxActivity.enregistrer(ctx, "gta", gagne);
        }

        void reprendre() {
            actif = true;
            dernier = SystemClock.uptimeMillis();
            cumul = 0;
            joyId = -1;
            tirId = -1;
            freinId = -1;
            postInvalidateOnAnimation();
        }

        // ================================================================ carte

        private void genererCarte() {
            Random g = new Random(20261110L);
            byte[][] bloc = new byte[BLOCS][BLOCS];
            for (int i = 0; i < BLOCS; i++)
                for (int j = 0; j < BLOCS; j++)
                    bloc[i][j] = (byte) (g.nextInt(6) == 0 ? 0 : 2 + g.nextInt(3));
            bloc[0][0] = 0;     // parc de départ
            bloc[3][3] = 0;     // grand parc central
            for (int row = 0; row < NT; row++) {
                for (int col = 0; col < NT; col++) {
                    int mr = row % PER, mc = col % PER;
                    if (mr < 2 || mc < 2) carte[row][col] = 1;
                    else if (mr == 2 || mr == 7 || mc == 2 || mc == 7) carte[row][col] = 5;
                    else carte[row][col] = bloc[row / PER][col / PER];
                }
            }
        }

        private int typeEn(float x, float y) {
            int col = (int) Math.floor(x / TS), row = (int) Math.floor(y / TS);
            if (row < 0 || row >= NT || col < 0 || col >= NT) return -1;
            return carte[row][col];
        }

        /** Terrain où l'on peut marcher ou rouler : parc, route, trottoir. */
        private boolean marchable(float x, float y) {
            int t = typeEn(x, y);
            return t == 0 || t == 1 || t == 5;
        }

        /** Les piétons restent sur les trottoirs et dans les parcs. */
        private boolean trottoir(float x, float y) {
            int t = typeEn(x, y);
            return t == 0 || t == 5;
        }

        private boolean tirPasse(float x, float y) {
            int t = typeEn(x, y);
            return t == 0 || t == 1 || t == 5;
        }

        private boolean pietonLibre(float x, float y) {
            float m = 11f;
            return marchable(x - m, y - m) && marchable(x + m, y - m) && marchable(x - m, y + m) && marchable(x + m, y + m);
        }

        /** Rectangle de 46 x 24 tourné de « angle » degrés autour de (x, y). */
        private boolean voitureLibre(float x, float y, float angle) {
            double a = Math.toRadians(angle);
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            float[] lx = {23, 23, -23, -23, 23, -23, 0, 0};
            float[] ly = {12, -12, 12, -12, 0, 0, 12, -12};
            for (int i = 0; i < lx.length; i++) {
                if (!marchable(x + lx[i] * cs - ly[i] * sn, y + lx[i] * sn + ly[i] * cs)) return false;
            }
            return true;
        }

        // ================================================================ outils

        private static float dist(float ax, float ay, float bx, float by) {
            return (float) Math.hypot(ax - bx, ay - by);
        }

        private static float ecartAngle(float a, float b) {
            float diff = b - a;
            while (diff > 180f) diff -= 360f;
            while (diff < -180f) diff += 360f;
            return diff;
        }

        private static float tourner(float a, float cible, float max) {
            float diff = ecartAngle(a, cible);
            float res = Math.abs(diff) <= max ? cible : a + (diff > 0 ? max : -max);
            while (res > 180f) res -= 360f;
            while (res < -180f) res += 360f;
            return res;
        }

        private void dire(String t) {
            message = t;
            messageFin = SystemClock.uptimeMillis() + 2600;
        }

        private void gagner(int n) {
            argent += n;
            gagne += n;
            if (gagne > record) {
                record = gagne;
                nouveauRecord = true;
            }
        }

        private int etoiles() {
            float q = ptsRech;
            return q < 30 ? 0 : q < 90 ? 1 : q < 170 ? 2 : q < 270 ? 3 : q < 400 ? 4 : 5;
        }

        private void pointsRecherche(float n) {
            ptsRech = Math.min(500f, ptsRech + n);
        }

        private boolean tropProche(float x, float y, float rayon) {
            for (Voiture v : voitures) if (dist(v.x, v.y, x, y) < rayon) return true;
            return false;
        }

        /** Point de trottoir (ou de parc) au hasard dans un anneau autour de (cx, cy). */
        private float[] ptTrottoir(float cx, float cy, float rmin, float rmax, boolean parc) {
            for (int essai = 0; essai < 300; essai++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                float rr = rmin + rnd.nextFloat() * (rmax - rmin);
                float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
                int t = typeEn(x, y);
                if (t == 5 || (parc && t == 0)) return new float[]{x, y};
            }
            return new float[]{TS * 4.5f, TS * 4.5f};
        }

        // ================================================================ apparitions

        private void spawnCivile(float rmin, float rmax) {
            for (int essai = 0; essai < 14; essai++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                float rr = rmin + rnd.nextFloat() * (rmax - rmin);
                float x = jx + (float) Math.cos(a) * rr, y = jy + (float) Math.sin(a) * rr;
                int col = (int) Math.floor(x / TS), row = (int) Math.floor(y / TS);
                if (row < 0 || col < 0 || row >= NT || col >= NT) continue;
                int mr = row % PER, mc = col % PER;
                boolean hor = mr < 2 && mc >= 2, ver = mc < 2 && mr >= 2;
                if (!hor && !ver) continue;
                Voiture v = new Voiture();
                int ib = row / PER, jb = col / PER;
                if (hor) {
                    v.dir = mr == 0 ? 2 : 0;
                    v.voie = (ib * PER + (mr == 0 ? 0.5f : 1.5f)) * TS;
                    v.y = v.voie;
                    v.x = col * TS + TS / 2f;
                } else {
                    v.dir = mc == 0 ? 1 : 3;
                    v.voie = (jb * PER + (mc == 0 ? 0.5f : 1.5f)) * TS;
                    v.x = v.voie;
                    v.y = row * TS + TS / 2f;
                }
                if (tropProche(v.x, v.y, 90f)) continue;
                v.ang = v.dir == 3 ? -90f : v.dir * 90f;
                v.couleur = 1 + rnd.nextInt(7);
                v.maxV = 7.5f + rnd.nextFloat() * 2f;
                v.vit = 3f;
                voitures.add(v);
                return;
            }
        }

        private void spawnPieton(float rmin, float rmax) {
            float[] pt = ptTrottoir(jx, jy, rmin, rmax, true);
            Pieton q = new Pieton();
            q.x = pt[0];
            q.y = pt[1];
            q.ang = (float) (rnd.nextDouble() * Math.PI * 2);
            q.t = 40 + rnd.nextInt(160);
            q.col = rnd.nextInt(COULEURS_PIETONS.length);
            q.vit = 0.9f + rnd.nextFloat() * 0.6f;
            pietons.add(q);
        }

        private void spawnObjet(float rmin, float rmax) {
            float[] pt = ptTrottoir(jx, jy, rmin, rmax, true);
            int k = rnd.nextInt(100);
            if (k < 58) objets.add(new Objet(pt[0], pt[1], 0, 40 + rnd.nextInt(7) * 20));
            else if (k < 80) objets.add(new Objet(pt[0], pt[1], 1, 15));
            else objets.add(new Objet(pt[0], pt[1], 2, 35));
        }

        private void spawnPolice() {
            for (int essai = 0; essai < 20; essai++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                float rr = 650f + rnd.nextFloat() * 350f;
                float x = jx + (float) Math.cos(a) * rr, y = jy + (float) Math.sin(a) * rr;
                if (typeEn(x, y) != 1 || tropProche(x, y, 90f)) continue;
                Voiture v = new Voiture();
                v.x = x;
                v.y = y;
                v.police = true;
                v.ia = false;
                v.couleur = 0;
                v.ang = (float) Math.toDegrees(Math.atan2(jy - y, jx - x));
                v.maxV = 7.8f;
                v.tir = 60;
                voitures.add(v);
                return;
            }
        }

        // ================================================================ logique

        private void pas() {
            temps++;
            if (delaiTir > 0) delaiTir--;
            if (cdDegat > 0) cdDegat--;

            // joystick
            float sx = 0, sy = 0, sm = 0;
            if (joyId != -1) {
                float dx = joyX - joyOx, dy = joyY - joyOy;
                float len = (float) Math.hypot(dx, dy);
                float rayon = 55f * d;
                if (len > 8f * d) {
                    sx = dx / len;
                    sy = dy / len;
                    sm = Math.min(1f, len / rayon);
                }
            }

            if (mortT > 0) {
                mortT--;
                if (mortT == 0) renaitre();
            } else {
                joueur(sx, sy, sm);
                if (jvie <= 0f) mourir();
            }

            majVoitures();
            majPietons();
            majBalles();
            majObjets();
            majPolice();
            majMission();
            for (int i = effets.size() - 1; i >= 0; i--) {
                Effet e = effets.get(i);
                if (++e.t > 45) effets.remove(i);
            }
            // peuple la ville autour du joueur
            if (temps % 24 == 0 && countCiviles() < 22) spawnCivile(650f, 1250f);
            if (temps % 12 == 0 && pietons.size() < 38) spawnPieton(450f, 1100f);
            if (temps % 300 == 0 && objets.size() < 26) spawnObjet(300f, 1600f);
        }

        private int countCiviles() {
            int n = 0;
            for (Voiture v : voitures) if (v.ia && !v.police && !v.epave) n++;
            return n;
        }

        // ---------------------------------------------------------------- joueur

        private void joueur(float sx, float sy, float sm) {
            if (voitJ != null) {
                pilotage(voitJ, sx, sy, sm, freinId != -1);
                jx = voitJ.x;
                jy = voitJ.y;
                return;
            }
            if (sm > 0f) {
                jdx = sx;
                jdy = sy;
                float v = VIT_PIETON * (0.45f + 0.55f * sm);
                float nx = jx + sx * v, ny = jy + sy * v;
                if (pietonLibre(nx, jy)) jx = nx;
                if (pietonLibre(jx, ny)) jy = ny;
            }
            if (tirId != -1 && munitions > 0 && delaiTir <= 0) tirer();

            // voitures qui foncent sur moi
            for (Voiture v : voitures) {
                if (v.epave || v.joueur) continue;
                float dd = dist(v.x, v.y, jx, jy);
                if (dd > 32f) continue;
                if (Math.abs(v.vit) > 2.5f) {
                    if (cdDegat <= 0) {
                        jvie -= Math.abs(v.vit) * 3.5f;
                        cdDegat = 25;
                    }
                    float nx = (jx - v.x) / Math.max(1f, dd), ny = (jy - v.y) / Math.max(1f, dd);
                    if (pietonLibre(jx + nx * 8f, jy + ny * 8f)) {
                        jx += nx * 8f;
                        jy += ny * 8f;
                    }
                } else if (v.police) {
                    jvie -= 0.25f;
                }
            }
        }

        private void pilotage(Voiture v, float sx, float sy, float sm, boolean frein) {
            if (frein) {
                v.vit *= 0.88f;
                if (Math.abs(v.vit) < 0.2f) v.vit = 0;
            } else if (sm > 0.2f) {
                float cible = (float) Math.toDegrees(Math.atan2(sy, sx));
                float diff = ecartAngle(v.ang, cible);
                boolean arriere = Math.abs(diff) > 125f && v.vit < 2.5f;
                float visee = arriere ? cible + 180f : cible;
                float tour = 3.4f * Math.min(1f, Math.abs(v.vit) / 2.2f) + 0.5f;
                float nouv = tourner(v.ang, visee, tour);
                if (voitureLibre(v.x, v.y, nouv)) v.ang = nouv;
                if (arriere) {
                    v.vit = Math.max(v.vit - 0.2f, -3.2f);
                } else if (Math.abs(diff) > 125f) {
                    v.vit = Math.max(0f, v.vit - 0.45f);
                } else {
                    float cibleV = v.maxV * (0.4f + 0.6f * sm);
                    if (v.vit < cibleV) v.vit = Math.min(cibleV, v.vit + 0.22f);
                    else v.vit -= 0.08f;
                }
            } else {
                v.vit *= 0.985f;
                if (Math.abs(v.vit) < 0.05f) v.vit = 0;
            }
            double a = Math.toRadians(v.ang);
            float mx = (float) Math.cos(a) * v.vit, my = (float) Math.sin(a) * v.vit;
            boolean bx = !voitureLibre(v.x + mx, v.y, v.ang);
            if (!bx) v.x += mx;
            boolean by = !voitureLibre(v.x, v.y + my, v.ang);
            if (!by) v.y += my;
            if (bx || by) {
                float imp = Math.abs(v.vit);
                if (imp > 4.5f) v.vie -= (imp - 3f) * 3f;
                v.vit *= -0.25f;
            }
        }

        /** Avance dans la direction de la voiture ; renvoie vrai si un mur bloque. */
        private boolean avancer(Voiture v) {
            double a = Math.toRadians(v.ang);
            float mx = (float) Math.cos(a) * v.vit, my = (float) Math.sin(a) * v.vit;
            boolean bloque = false;
            if (voitureLibre(v.x + mx, v.y, v.ang)) v.x += mx; else bloque = true;
            if (voitureLibre(v.x, v.y + my, v.ang)) v.y += my; else bloque = true;
            return bloque;
        }

        private float[] viseeAssistee() {
            float fx = jdx, fy = jdy;
            float meilleur = 320f;
            float cx = fx, cy = fy;
            float faceAngle = (float) Math.toDegrees(Math.atan2(fy, fx));
            for (Pieton q : pietons) {
                if (q.etat == 2) continue;
                float dd = dist(q.x, q.y, jx, jy);
                if (dd > meilleur || dd < 1f) continue;
                float ang = (float) Math.toDegrees(Math.atan2(q.y - jy, q.x - jx));
                if (Math.abs(ecartAngle(faceAngle, ang)) < 40f) {
                    meilleur = dd;
                    cx = (q.x - jx) / dd;
                    cy = (q.y - jy) / dd;
                }
            }
            for (Voiture v : voitures) {
                if (v.epave || v.joueur) continue;
                float dd = dist(v.x, v.y, jx, jy);
                if (dd > meilleur || dd < 1f) continue;
                float ang = (float) Math.toDegrees(Math.atan2(v.y - jy, v.x - jx));
                if (Math.abs(ecartAngle(faceAngle, ang)) < 35f) {
                    meilleur = dd;
                    cx = (v.x - jx) / dd;
                    cy = (v.y - jy) / dd;
                }
            }
            return new float[]{cx, cy};
        }

        private void tirer() {
            float[] dir = viseeAssistee();
            jdx = dir[0];
            jdy = dir[1];
            balles.add(new Balle(jx + jdx * 18f, jy + jdy * 18f, jdx * 15f, jdy * 15f, 28, true));
            munitions--;
            delaiTir = 13;
            pointsRecherche(1.5f);
        }

        private void actionVoiture() {
            if (mortT > 0) return;
            if (voitJ == null) {
                Voiture meilleur = null;
                float bd = 100f;
                for (Voiture v : voitures) {
                    if (v.epave) continue;
                    float dd = dist(v.x, v.y, jx, jy);
                    if (dd < bd) {
                        bd = dd;
                        meilleur = v;
                    }
                }
                if (meilleur == null) {
                    dire("Aucune voiture à proximité");
                    return;
                }
                if (meilleur.police) {
                    pointsRecherche(70f);
                    meilleur.police = false;
                    meilleur.couleur = 0;
                } else if (meilleur.ia) {
                    // le conducteur sort et s'enfuit
                    Pieton q = new Pieton();
                    double a = Math.toRadians(meilleur.ang + 90f);
                    q.x = meilleur.x + (float) Math.cos(a) * 34f;
                    q.y = meilleur.y + (float) Math.sin(a) * 34f;
                    if (!trottoir(q.x, q.y) && typeEn(q.x, q.y) != 1) {
                        q.x = meilleur.x;
                        q.y = meilleur.y;
                    }
                    q.etat = 1;
                    q.t = 240;
                    q.ang = (float) Math.atan2(q.y - jy, q.x - jx);
                    q.col = rnd.nextInt(COULEURS_PIETONS.length);
                    pietons.add(q);
                    pointsRecherche(40f);
                }
                meilleur.ia = false;
                meilleur.joueur = true;
                meilleur.recul = 0;
                meilleur.inter = -1;
                voitJ = meilleur;
                jx = meilleur.x;
                jy = meilleur.y;
            } else {
                Voiture v = voitJ;
                v.joueur = false;
                v.ia = false;
                v.police = false;
                voitJ = null;
                double[] angles = {90, -90, 0, 180, 45, -45, 135, -135};
                boolean place = false;
                for (double deg : angles) {
                    double a = Math.toRadians(v.ang + deg);
                    float nx = v.x + (float) Math.cos(a) * 44f, ny = v.y + (float) Math.sin(a) * 44f;
                    if (pietonLibre(nx, ny)) {
                        jx = nx;
                        jy = ny;
                        place = true;
                        break;
                    }
                }
                if (!place) {
                    jx = v.x;
                    jy = v.y;
                }
            }
        }

        private void mourir() {
            mortT = 170;
            if (voitJ != null) {
                voitJ.joueur = false;
                voitJ.ia = false;
                voitJ.police = false;
                voitJ = null;
            }
            argent -= Math.min(argent, 100 + argent / 5);
            jvie = 0f;
            tirId = -1;
            if (gagne > 0) JeuxActivity.enregistrer(ctx, "gta", gagne);
        }

        private void renaitre() {
            jx = TS * 4.5f;
            jy = TS * 4.5f;
            jdx = 0;
            jdy = -1;
            jvie = 100f;
            ptsRech = 0f;
            munitions = Math.max(munitions, 20);
            dire("Tu es sorti de l'hôpital");
        }

        // ---------------------------------------------------------------- voitures

        private void majVoitures() {
            int et = etoiles();
            for (int i = voitures.size() - 1; i >= 0; i--) {
                Voiture v = voitures.get(i);
                if (v.epave) {
                    if (++v.feu > 420) voitures.remove(i);
                    continue;
                }
                float dj = dist(v.x, v.y, jx, jy);
                if (v.joueur) {
                    // piloté dans joueur()
                } else if (v.police) {
                    iaPolice(v, et);
                    if ((et == 0 && v.idle > 1200) || dj > 1900f) {
                        voitures.remove(i);
                        continue;
                    }
                } else if (v.ia) {
                    iaCivile(v);
                    if (v.bloqueT > 500 || dj > 1900f) {
                        voitures.remove(i);
                        continue;
                    }
                } else {
                    v.vit *= 0.9f;
                    if (Math.abs(v.vit) < 0.05f) v.vit = 0; else avancer(v);
                }
            }

            // collisions voiture contre voiture
            int n = voitures.size();
            for (int i = 0; i < n; i++) {
                Voiture a = voitures.get(i);
                for (int j = i + 1; j < n; j++) {
                    Voiture b = voitures.get(j);
                    if (a.epave && b.epave) continue;
                    float dx = b.x - a.x, dy = b.y - a.y;
                    float d2 = dx * dx + dy * dy;
                    if (d2 >= 2116f) continue;
                    float dd = (float) Math.sqrt(d2);
                    if (dd < 0.01f) {
                        dx = 1f;
                        dy = 0f;
                        dd = 1f;
                    }
                    float nx = dx / dd, ny = dy / dd, rec = 46f - dd;
                    float pa = a.epave ? 0f : (b.epave ? 1f : 0.5f);
                    float pb = b.epave ? 0f : (a.epave ? 1f : 0.5f);
                    pousser(a, -nx * rec * pa, -ny * rec * pa);
                    pousser(b, nx * rec * pb, ny * rec * pb);
                    float rel = Math.abs(a.vit) + Math.abs(b.vit);
                    if (rel > 3f) {
                        float dg = rel * 1.4f;
                        if (!a.epave) a.vie -= dg;
                        if (!b.epave) b.vie -= dg;
                        if ((a.joueur && !b.police && !b.epave) || (b.joueur && !a.police && !a.epave)) pointsRecherche(10f);
                    }
                    if ((a.police && b.joueur && !a.epave) || (b.police && a.joueur && !b.epave)) {
                        if (a.joueur) a.vie -= 0.5f; else b.vie -= 0.5f;
                    }
                    a.vit *= 0.7f;
                    b.vit *= 0.7f;
                }
            }
            for (int i = 0; i < voitures.size(); i++) {
                Voiture v = voitures.get(i);
                if (!v.epave && v.vie <= 0f) exploser(v);
            }
        }

        private void pousser(Voiture v, float dx, float dy) {
            if (voitureLibre(v.x + dx, v.y + dy, v.ang)) {
                v.x += dx;
                v.y += dy;
            }
        }

        private void exploser(Voiture v) {
            v.epave = true;
            v.vit = 0f;
            v.feu = 0;
            v.vie = 0f;
            v.ia = false;
            effets.add(new Effet(v.x, v.y));
            if (v.joueur) {
                v.joueur = false;
                voitJ = null;
                jx = v.x + 32f;
                jy = v.y;
                if (!pietonLibre(jx, jy)) {
                    jx = v.x;
                    jy = v.y;
                }
                jvie -= 35f;
            } else if (dist(jx, jy, v.x, v.y) < 100f) {
                jvie -= 30f;
            }
            for (Pieton q : pietons) if (dist(q.x, q.y, v.x, v.y) < 100f) frapper(q, true);
            for (Voiture o : voitures) {
                if (o != v && !o.epave && dist(o.x, o.y, v.x, v.y) < 90f) o.vie -= 35f;
            }
            pointsRecherche(v.police ? 70f : 35f);
            dire("Boum !");
        }

        private void iaCivile(Voiture v) {
            int tx = (int) Math.floor(v.x / TS), ty = (int) Math.floor(v.y / TS);
            boolean enInter = tx >= 0 && ty >= 0 && tx < NT && ty < NT && tx % PER < 2 && ty % PER < 2;
            if (enInter) {
                int id = (ty / PER) * 100 + tx / PER;
                if (v.inter != id) {
                    v.inter = id;
                    choisirDir(v, tx / PER, ty / PER);
                }
            } else {
                v.inter = -1;
            }
            boolean bloque = obstacleDevant(v);
            if (bloque) v.bloqueT++; else if (v.bloqueT > 0) v.bloqueT--;
            float croisiere = Math.min(v.maxV, 5.2f);
            if (bloque) v.vit = Math.max(0f, v.vit - 0.5f);
            else if (v.vit < croisiere) v.vit += 0.12f;
            else v.vit -= 0.05f;
            float cible = v.dir == 3 ? -90f : v.dir * 90f;
            v.ang = tourner(v.ang, cible, 4f);
            // se recale sur le centre de sa voie
            if (v.dir == 0 || v.dir == 2) {
                float dy = v.voie - v.y;
                v.y += Math.max(-2f, Math.min(2f, dy));
            } else {
                float dx = v.voie - v.x;
                v.x += Math.max(-2f, Math.min(2f, dx));
            }
            double a = Math.toRadians(v.ang);
            v.x += (float) Math.cos(a) * v.vit;
            v.y += (float) Math.sin(a) * v.vit;
        }

        private void choisirDir(Voiture v, int jb, int ib) {
            boolean[] ok = new boolean[4];
            ok[0] = jb < BLOCS;
            ok[1] = ib < BLOCS;
            ok[2] = jb > 0;
            ok[3] = ib > 0;
            int inverse = (v.dir + 2) % 4;
            int[] cand = new int[4];
            int nc = 0;
            for (int k = 0; k < 4; k++) if (ok[k] && k != inverse) cand[nc++] = k;
            if (nc == 0) cand[nc++] = inverse;
            int choix;
            if (ok[v.dir] && rnd.nextInt(100) < 50) choix = v.dir;
            else choix = cand[rnd.nextInt(nc)];
            v.dir = choix;
            int colBase = jb * PER, rowBase = ib * PER;
            if (choix == 0) v.voie = (rowBase + 1.5f) * TS;
            else if (choix == 2) v.voie = (rowBase + 0.5f) * TS;
            else if (choix == 1) v.voie = (colBase + 0.5f) * TS;
            else v.voie = (colBase + 1.5f) * TS;
        }

        private boolean obstacleDevant(Voiture v) {
            double a = Math.toRadians(v.ang);
            float ax = v.x + (float) Math.cos(a) * 58f, ay = v.y + (float) Math.sin(a) * 58f;
            for (Voiture o : voitures) {
                if (o == v) continue;
                if (dist(o.x, o.y, ax, ay) < 34f) return true;
            }
            if (voitJ == null && mortT == 0 && dist(jx, jy, ax, ay) < 34f) return true;
            return false;
        }

        private void iaPolice(Voiture v, int et) {
            float dx = jx - v.x, dy = jy - v.y;
            float dd = (float) Math.hypot(dx, dy);
            if (et == 0) {
                v.vit *= 0.95f;
                v.idle++;
                if (Math.abs(v.vit) > 0.05f) avancer(v);
                return;
            }
            v.idle = 0;
            float cible = (float) Math.toDegrees(Math.atan2(dy, dx));
            float maxv = 7.6f + 0.35f * et;
            if (v.recul > 0) {
                v.recul--;
                v.vit = Math.max(v.vit - 0.4f, -3f);
                v.ang = tourner(v.ang, cible + 120f, 2.5f);
            } else {
                v.vit = Math.min(v.vit + 0.2f, maxv);
                v.ang = tourner(v.ang, cible, 3.2f);
            }
            if (avancer(v) && v.recul == 0 && v.vit > 0f) {
                v.recul = 30;
                v.vit = 0f;
            }
            // tirs à partir de 3 étoiles
            if (v.tir > 0) v.tir--;
            if (et >= 3 && dd < 340f && v.tir <= 0 && mortT == 0) {
                float ex = dx / Math.max(1f, dd), ey = dy / Math.max(1f, dd);
                ex += (rnd.nextFloat() - 0.5f) * 0.12f;
                ey += (rnd.nextFloat() - 0.5f) * 0.12f;
                balles.add(new Balle(v.x + ex * 26f, v.y + ey * 26f, ex * 11f, ey * 11f, 38, false));
                v.tir = 50 - et * 4;
            }
        }

        // ---------------------------------------------------------------- piétons

        private void majPietons() {
            for (int i = pietons.size() - 1; i >= 0; i--) {
                Pieton q = pietons.get(i);
                if (q.etat == 2) {
                    if (--q.t <= 0) pietons.remove(i);
                    continue;
                }
                if (dist(q.x, q.y, jx, jy) > 1400f) {
                    pietons.remove(i);
                    continue;
                }
                // écrasé ?
                boolean touche = false;
                for (Voiture v : voitures) {
                    if (v.epave || Math.abs(v.vit) < 2.2f) continue;
                    if (dist(v.x, v.y, q.x, q.y) < 28f) {
                        frapper(q, v.joueur);
                        touche = true;
                        break;
                    }
                }
                if (touche) continue;
                float vit = q.etat == 1 ? 2.4f : q.vit;
                if (q.etat == 1) {
                    if (--q.t <= 0) q.etat = 0;
                } else {
                    if (q.t < 0) {          // petite pause sur place
                        q.t++;
                        continue;
                    }
                    if (--q.t <= 0) {
                        q.ang = (float) (rnd.nextDouble() * Math.PI * 2);
                        q.t = 50 + rnd.nextInt(200);
                        if (rnd.nextInt(5) == 0) q.t = -(30 + rnd.nextInt(90));
                    }
                }
                float nx = q.x + (float) Math.cos(q.ang) * vit, ny = q.y + (float) Math.sin(q.ang) * vit;
                if (trottoir(nx, ny)) {
                    q.x = nx;
                    q.y = ny;
                } else {
                    q.ang += 2.0f + rnd.nextFloat();
                    if (q.etat == 0) q.t = 25 + rnd.nextInt(60);
                }
            }
        }

        private void frapper(Pieton q, boolean parJoueur) {
            if (q.etat == 2) return;
            q.etat = 2;
            q.t = 420;
            if (parJoueur) pointsRecherche(35f);
            if (rnd.nextInt(10) < 7) objets.add(new Objet(q.x, q.y, 0, 10 + rnd.nextInt(41)));
            for (Pieton o : pietons) {
                if (o.etat == 0 && dist(o.x, o.y, q.x, q.y) < 220f) {
                    o.etat = 1;
                    o.t = 240;
                    o.ang = (float) Math.atan2(o.y - q.y, o.x - q.x);
                }
            }
        }

        // ---------------------------------------------------------------- balles, objets

        private void majBalles() {
            for (int i = balles.size() - 1; i >= 0; i--) {
                Balle b = balles.get(i);
                b.x += b.dx;
                b.y += b.dy;
                if (--b.vie <= 0 || !tirPasse(b.x, b.y)) {
                    balles.remove(i);
                    continue;
                }
                boolean fin = false;
                if (b.joueur) {
                    for (Pieton q : pietons) {
                        if (q.etat != 2 && dist(q.x, q.y, b.x, b.y) < 16f) {
                            frapper(q, true);
                            fin = true;
                            break;
                        }
                    }
                    if (!fin) {
                        for (Voiture v : voitures) {
                            if (v.epave || v.joueur) continue;
                            if (dist(v.x, v.y, b.x, b.y) < 28f) {
                                v.vie -= 18f;
                                pointsRecherche(v.police ? 25f : 8f);
                                fin = true;
                                break;
                            }
                        }
                    }
                } else if (mortT == 0) {
                    if (voitJ == null) {
                        if (dist(jx, jy, b.x, b.y) < 16f) {
                            jvie -= 6f;
                            fin = true;
                        }
                    } else if (dist(voitJ.x, voitJ.y, b.x, b.y) < 28f) {
                        voitJ.vie -= 4f;
                        fin = true;
                    }
                }
                if (fin) balles.remove(i);
            }
        }

        private void majObjets() {
            if (mortT > 0) return;
            for (int i = objets.size() - 1; i >= 0; i--) {
                Objet o = objets.get(i);
                if (dist(o.x, o.y, jx, jy) < (voitJ != null ? 40f : 30f)) {
                    if (o.type == 0) {
                        gagner(o.val);
                    } else if (o.type == 1) {
                        munitions = Math.min(99, munitions + o.val);
                    } else {
                        jvie = Math.min(100f, jvie + o.val);
                        if (voitJ != null) voitJ.vie = Math.min(100f, voitJ.vie + 20f);
                    }
                    objets.remove(i);
                }
            }
        }

        // ---------------------------------------------------------------- police et mission

        private void majPolice() {
            int et = etoiles();
            int cible = et == 0 ? 0 : Math.min(8, et * 2);
            int n = 0;
            boolean proche = false;
            for (Voiture v : voitures) {
                if (v.police && !v.epave) {
                    n++;
                    if (dist(v.x, v.y, jx, jy) < 350f) proche = true;
                }
            }
            if (n < cible && temps % 40 == 0) spawnPolice();
            if (!proche && mortT == 0) {
                float avant = ptsRech;
                ptsRech = Math.max(0f, ptsRech - 0.1f);
                if (etoiles() == 0 && avant >= 30f && ptsRech < 30f) dire("Plus recherché !");
            }
        }

        private void nouvelleMission() {
            float[] a = ptTrottoir(jx, jy, 500f, 1700f, false);
            float[] b = ptTrottoir(a[0], a[1], 700f, 1900f, false);
            mAx = a[0];
            mAy = a[1];
            mBx = b[0];
            mBy = b[1];
            mEtat = 1;
            dire("Nouvelle mission : récupère le colis jaune");
        }

        private void majMission() {
            if (mEtat == 0) {
                if (--mDelai <= 0) nouvelleMission();
                return;
            }
            if (mortT > 0) return;
            if (mEtat == 1) {
                if (dist(jx, jy, mAx, mAy) < 46f) {
                    mEtat = 2;
                    mTemps = (int) (60f * (25f + dist(mAx, mAy, mBx, mBy) / 200f));
                    dire("Colis récupéré ! Livre-le au point vert");
                }
            } else {
                mTemps--;
                if (dist(jx, jy, mBx, mBy) < 46f) {
                    int gain = 250 + Math.max(0, mTemps / 60) * 3;
                    gagner(gain);
                    dire("Livraison réussie ! +" + gain + " $");
                    mEtat = 0;
                    mDelai = 240;
                } else if (mTemps <= 0) {
                    dire("Temps écoulé, mission ratée");
                    mEtat = 0;
                    mDelai = 240;
                }
            }
        }

        // ================================================================ toucher

        private static boolean dansCercle(float x, float y, float cx, float cy, float rr) {
            return Math.hypot(x - cx, y - cy) <= rr;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int act = e.getActionMasked();
            int idx = e.getActionIndex();
            switch (act) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int id = e.getPointerId(idx);
                    float x = e.getX(idx), y = e.getY(idx);
                    if (dansCercle(x, y, aX, aY, aR * 1.15f)) {
                        actionVoiture();
                    } else if (dansCercle(x, y, tX, tY, tR * 1.15f)) {
                        if (voitJ != null) freinId = id; else tirId = id;
                    } else if (x < getWidth() * 0.5f && joyId == -1) {
                        joyId = id;
                        joyOx = x;
                        joyOy = y;
                        joyX = x;
                        joyY = y;
                    }
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < e.getPointerCount(); i++) {
                        if (e.getPointerId(i) == joyId) {
                            joyX = e.getX(i);
                            joyY = e.getY(i);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    joyId = -1;
                    tirId = -1;
                    freinId = -1;
                    return true;
                case MotionEvent.ACTION_POINTER_UP: {
                    int id = e.getPointerId(idx);
                    if (id == joyId) joyId = -1;
                    if (id == tirId) tirId = -1;
                    if (id == freinId) freinId = -1;
                    return true;
                }
            }
            return true;
        }

        // ================================================================ dessin

        private Bitmap tuile(int t) {
            switch (t) {
                case 1: return route;
                case 2: return b1;
                case 3: return b2;
                case 4: return b3;
                default: return sol;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;

            long now = SystemClock.uptimeMillis();
            if (actif) {
                cumul += Math.min(100, now - dernier);
                int n = 0;
                while (cumul >= PAS && n++ < 5) {
                    pas();
                    cumul -= PAS;
                }
                if (n >= 5) cumul = 0;
            }
            dernier = now;

            // zoom : on voit plus loin quand on roule vite
            float frac = voitJ != null ? Math.min(1f, Math.abs(voitJ.vit) / 9f) : 0f;
            float cibleEch = h / (TS * (5.6f + 2.2f * frac));
            if (temps < 2) echelle = cibleEch;
            echelle += (cibleEch - echelle) * 0.06f;
            float lw = w / echelle, lh = h / echelle;
            float avanceX = 0, avanceY = 0;
            if (voitJ != null) {
                double a = Math.toRadians(voitJ.ang);
                avanceX = (float) Math.cos(a) * voitJ.vit * 12f;
                avanceY = (float) Math.sin(a) * voitJ.vit * 12f;
            }
            camX = Math.max(0, Math.min(jx + avanceX - lw / 2f, NT * TS - lw));
            camY = Math.max(0, Math.min(jy + avanceY - lh / 2f, NT * TS - lh));

            c.drawColor(0xFF11151C);
            c.save();
            c.scale(echelle, echelle);
            c.translate(-camX, -camY);

            dessinerVille(c, lw, lh);
            dessinerMarqueurs(c);
            for (Objet o : objets) if (visible(o.x, o.y, 30f, lw, lh)) dessinerObjet(c, o);
            for (Pieton q : pietons) if (q.etat == 2 && visible(q.x, q.y, 30f, lw, lh)) dessinerPieton(c, q);
            for (Voiture v : voitures) if (v.epave && visible(v.x, v.y, 60f, lw, lh)) dessinerVoiture(c, v);
            for (Voiture v : voitures) if (!v.epave && visible(v.x, v.y, 60f, lw, lh)) dessinerVoiture(c, v);
            for (Pieton q : pietons) if (q.etat != 2 && visible(q.x, q.y, 30f, lw, lh)) dessinerPieton(c, q);
            if (voitJ == null && mortT == 0) dessinerJoueur(c);
            dessinerBalles(c);
            for (Effet e : effets) dessinerExplosion(c, e);
            c.restore();

            // nuit
            float nuit = (float) Math.max(0.0, Math.sin(temps / 7200.0 * Math.PI * 2.0));
            if (nuit > 0.02f) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(((int) (nuit * 120f) << 24) | 0x0A1030);
                c.drawRect(0, 0, w, h, p);
            }

            dessinerInterface(c, w, h);
            if (mortT > 0) dessinerMort(c, w, h);

            if (actif) postInvalidateOnAnimation();
        }

        private boolean visible(float x, float y, float marge, float lw, float lh) {
            return x > camX - marge && x < camX + lw + marge && y > camY - marge && y < camY + lh + marge;
        }

        private void dessinerVille(Canvas c, float lw, float lh) {
            int c0 = Math.max(0, (int) (camX / TS)), c1 = Math.min(NT - 1, (int) ((camX + lw) / TS));
            int l0 = Math.max(0, (int) (camY / TS)), l1 = Math.min(NT - 1, (int) ((camY + lh) / TS));
            p.setStyle(Paint.Style.FILL);
            for (int row = l0; row <= l1; row++) {
                for (int col = c0; col <= c1; col++) {
                    int t = carte[row][col];
                    float x = col * TS, y = row * TS;
                    if (t == 5) {
                        p.setColor(0xFFB4B9BE);
                        c.drawRect(x, y, x + TS + 0.5f, y + TS + 0.5f, p);
                        p.setColor(0xFF9EA4AA);
                        c.drawRect(x + TS - 2, y, x + TS + 0.5f, y + TS + 0.5f, p);
                        c.drawRect(x, y + TS - 2, x + TS + 0.5f, y + TS + 0.5f, p);
                        continue;
                    }
                    r.set(x, y, x + TS + 0.5f, y + TS + 0.5f);
                    c.drawBitmap(tuile(t), null, r, pPixel);
                    if (t == 1) {
                        int mr = row % PER, mc = col % PER;
                        p.setColor(0xFFE2B007);
                        if (mr == 0 && mc >= 2) {          // ligne jaune d'une route horizontale
                            c.drawRect(x + 6, y + TS - 2, x + 26, y + TS + 2, p);
                            c.drawRect(x + 38, y + TS - 2, x + 58, y + TS + 2, p);
                        } else if (mc == 0 && mr >= 2) {   // ligne jaune d'une route verticale
                            c.drawRect(x + TS - 2, y + 6, x + TS + 2, y + 26, p);
                            c.drawRect(x + TS - 2, y + 38, x + TS + 2, y + 58, p);
                        }
                    }
                }
            }
        }

        private void dessinerMarqueurs(Canvas c) {
            if (mEtat == 0) return;
            float pulse = 1f + 0.12f * (float) Math.sin(temps / 8.0);
            float mx = mEtat == 1 ? mAx : mBx, my = mEtat == 1 ? mAy : mBy;
            int col = mEtat == 1 ? 0xFFFFD600 : 0xFF00E676;
            p.setStyle(Paint.Style.FILL);
            p.setColor((col & 0x00FFFFFF) | 0x55000000);
            c.drawCircle(mx, my, 40f * pulse, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4f);
            p.setColor(col);
            c.drawCircle(mx, my, 40f * pulse, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(34f);
            c.drawText(mEtat == 1 ? "C" : "L", mx, my + 12f, p);
            // flèche vers l'objectif, autour du joueur
            float ang = (float) Math.atan2(my - jy, mx - jx);
            float dd = dist(jx, jy, mx, my);
            if (dd > 150f) {
                float ax = jx + (float) Math.cos(ang) * 70f, ay = jy + (float) Math.sin(ang) * 70f;
                chemin.reset();
                chemin.moveTo(ax + (float) Math.cos(ang) * 16f, ay + (float) Math.sin(ang) * 16f);
                chemin.lineTo(ax + (float) Math.cos(ang + 2.5f) * 12f, ay + (float) Math.sin(ang + 2.5f) * 12f);
                chemin.lineTo(ax + (float) Math.cos(ang - 2.5f) * 12f, ay + (float) Math.sin(ang - 2.5f) * 12f);
                chemin.close();
                p.setColor(col);
                c.drawPath(chemin, p);
            }
        }

        private void dessinerObjet(Canvas c, Objet o) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x55000000);
            c.drawOval(o.x - 12, o.y + 6, o.x + 12, o.y + 15, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            if (o.type == 0) {
                p.setColor(0xFF2E7D32);
                r.set(o.x - 13, o.y - 8, o.x + 13, o.y + 8);
                c.drawRoundRect(r, 3, 3, p);
                p.setColor(0xFF9CCC65);
                c.drawCircle(o.x, o.y, 5f, p);
            } else if (o.type == 1) {
                p.setColor(0xFFFB8C00);
                r.set(o.x - 12, o.y - 10, o.x + 12, o.y + 10);
                c.drawRoundRect(r, 3, 3, p);
                p.setColor(0xFF4E2A00);
                p.setTextSize(15f);
                c.drawText("||", o.x, o.y + 5f, p);
            } else {
                p.setColor(0xFFFFFFFF);
                r.set(o.x - 12, o.y - 12, o.x + 12, o.y + 12);
                c.drawRoundRect(r, 4, 4, p);
                p.setColor(0xFFE53935);
                c.drawRect(o.x - 3, o.y - 9, o.x + 3, o.y + 9, p);
                c.drawRect(o.x - 9, o.y - 3, o.x + 9, o.y + 3, p);
            }
        }

        private void dessinerPieton(Canvas c, Pieton q) {
            c.save();
            c.translate(q.x, q.y);
            if (q.etat == 2) {
                c.rotate(90f + q.col * 40f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x33000000);
                c.drawCircle(0, 0, 14f, p);
                c.scale(0.75f, 0.75f);
                c.drawBitmap(sprJoueur, -sprJoueur.getWidth() / 2f, -sprJoueur.getHeight() / 2f, pEpave);
            } else {
                c.rotate((float) Math.toDegrees(q.ang) + 90f);
                c.scale(0.75f, 0.75f);
                c.drawBitmap(sprJoueur, -sprJoueur.getWidth() / 2f, -sprJoueur.getHeight() / 2f, teintesPieton[q.col]);
            }
            c.restore();
        }

        private void dessinerJoueur(Canvas c) {
            c.save();
            c.translate(jx, jy);
            c.rotate((float) Math.toDegrees(Math.atan2(jdy, jdx)) + 90f);
            c.scale(0.85f, 0.85f);
            c.drawBitmap(sprJoueur, -sprJoueur.getWidth() / 2f, -sprJoueur.getHeight() / 2f, pPixel);
            c.restore();
        }

        private void dessinerVoiture(Canvas c, Voiture v) {
            c.save();
            c.translate(v.x, v.y);
            c.rotate(v.ang);
            c.scale(0.8f, 0.8f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x44000000);
            r.set(-30, -15, 34, 19);
            c.drawRoundRect(r, 8, 8, p);
            c.drawBitmap(sprVoiture, -sprVoiture.getWidth() / 2f, -sprVoiture.getHeight() / 2f,
                    v.epave ? pEpave : teintes[v.couleur]);
            if (v.police && !v.epave) {
                p.setColor(0xFF101820);
                c.drawRect(-18, -10, 20, 10, p);
                boolean a = (temps / 8) % 2 == 0;
                p.setColor(a ? 0xFFFF1744 : 0xFF2979FF);
                c.drawCircle(-2, -6, 4.5f, p);
                p.setColor(a ? 0xFF2979FF : 0xFFFF1744);
                c.drawCircle(-2, 6, 4.5f, p);
                p.setColor(0x33FFFFFF);
                c.drawCircle(-2, a ? -6 : 6, 11f, p);
            }
            if (!v.epave && v.vie < 40f) {
                p.setColor(0x66404040);
                c.drawCircle(-24 - (temps % 12), (temps % 5) - 2, 6f, p);
            }
            c.restore();
            if (v.epave) {
                p.setStyle(Paint.Style.FILL);
                int k = temps % 6;
                p.setColor(0xCCFF6D00);
                c.drawCircle(v.x - 4 + k, v.y - 2, 14f + k, p);
                p.setColor(0xCCFFD600);
                c.drawCircle(v.x + 4 - k, v.y + 3, 8f + k / 2f, p);
            }
        }

        private void dessinerBalles(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            for (Balle b : balles) {
                p.setColor(b.joueur ? 0xFFFFF59D : 0xFFFF8A80);
                c.drawCircle(b.x, b.y, 3.5f, p);
                p.setColor(b.joueur ? 0x55FFF59D : 0x55FF8A80);
                c.drawCircle(b.x - b.dx * 0.6f, b.y - b.dy * 0.6f, 2.5f, p);
            }
        }

        private void dessinerExplosion(Canvas c, Effet e) {
            float k = e.t / 45f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(((int) ((1f - k) * 200f) << 24) | 0xFF6D00);
            c.drawCircle(e.x, e.y, 20f + k * 90f, p);
            p.setColor(((int) ((1f - k) * 220f) << 24) | 0xFFD600);
            c.drawCircle(e.x, e.y, 10f + k * 50f, p);
        }

        // ---------------------------------------------------------------- interface

        private void texte(Canvas c, String t, float x, float y, float taille, int couleur, Paint.Align al) {
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(al);
            p.setTextSize(taille);
            p.setColor(couleur);
            c.drawText(t, x, y, p);
        }

        private void dessinerInterface(Canvas c, int w, int h) {
            float m = 10f * d;

            // ---- mini-carte
            float taille = 118f * d;
            float rayonMonde = 640f;
            RectF carteR = new RectF(m, m, m + taille, m + taille);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF0B0E12);
            c.drawRoundRect(carteR, 10 * d, 10 * d, p);
            c.save();
            c.clipRect(carteR);
            float px = taille / (rayonMonde * 2f);
            int c0 = Math.max(0, (int) ((jx - rayonMonde) / TS)), c1 = Math.min(NT - 1, (int) ((jx + rayonMonde) / TS));
            int l0 = Math.max(0, (int) ((jy - rayonMonde) / TS)), l1 = Math.min(NT - 1, (int) ((jy + rayonMonde) / TS));
            for (int row = l0; row <= l1; row++) {
                for (int col = c0; col <= c1; col++) {
                    int t = carte[row][col];
                    p.setColor(t == 1 ? 0xFF4A505A : t == 5 ? 0xFF8A9096 : t == 0 ? 0xFF2E7D32 : 0xFF222831);
                    float x0 = carteR.left + (col * TS - (jx - rayonMonde)) * px;
                    float y0 = carteR.top + (row * TS - (jy - rayonMonde)) * px;
                    c.drawRect(x0, y0, x0 + TS * px + 0.7f, y0 + TS * px + 0.7f, p);
                }
            }
            for (Voiture v : voitures) {
                if (!v.police || v.epave) continue;
                float x0 = carteR.centerX() + (v.x - jx) * px, y0 = carteR.centerY() + (v.y - jy) * px;
                p.setColor((temps / 8) % 2 == 0 ? 0xFFFF1744 : 0xFF2979FF);
                c.drawCircle(x0, y0, 3.2f * d, p);
            }
            if (mEtat != 0) {
                float mx = mEtat == 1 ? mAx : mBx, my = mEtat == 1 ? mAy : mBy;
                float x0 = carteR.centerX() + (mx - jx) * px, y0 = carteR.centerY() + (my - jy) * px;
                x0 = Math.max(carteR.left + 5 * d, Math.min(carteR.right - 5 * d, x0));
                y0 = Math.max(carteR.top + 5 * d, Math.min(carteR.bottom - 5 * d, y0));
                p.setColor(mEtat == 1 ? 0xFFFFD600 : 0xFF00E676);
                c.drawCircle(x0, y0, 4.5f * d, p);
            }
            // moi
            float ang = voitJ != null ? (float) Math.toRadians(voitJ.ang) : (float) Math.atan2(jdy, jdx);
            float cx = carteR.centerX(), cy = carteR.centerY(), s = 5.5f * d;
            chemin.reset();
            chemin.moveTo(cx + (float) Math.cos(ang) * s * 1.4f, cy + (float) Math.sin(ang) * s * 1.4f);
            chemin.lineTo(cx + (float) Math.cos(ang + 2.5f) * s, cy + (float) Math.sin(ang + 2.5f) * s);
            chemin.lineTo(cx + (float) Math.cos(ang - 2.5f) * s, cy + (float) Math.sin(ang - 2.5f) * s);
            chemin.close();
            p.setColor(0xFFFFFFFF);
            c.drawPath(chemin, p);
            c.restore();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * d);
            p.setColor(0xFFFFFFFF);
            c.drawRoundRect(carteR, 10 * d, 10 * d, p);

            // ---- tableau de bord
            float bx = carteR.right + m, by = m, bw = 200f * d, bh = 78f * d;
            r.set(bx, by, bx + bw, by + bh);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x99000000);
            c.drawRoundRect(r, 10 * d, 10 * d, p);
            texte(c, "$ " + argent, bx + 12 * d, by + 26 * d, 21 * d, 0xFF9CCC65, Paint.Align.LEFT);
            // santé
            barre(c, bx + 12 * d, by + 36 * d, bw - 24 * d, 8 * d, jvie / 100f, 0xFFE53935);
            if (voitJ != null) barre(c, bx + 12 * d, by + 48 * d, bw - 24 * d, 6 * d, voitJ.vie / 100f, 0xFF29B6F6);
            texte(c, "Munitions : " + munitions, bx + 12 * d, by + 70 * d, 12 * d, 0xFFFFE082, Paint.Align.LEFT);
            texte(c, (nouveauRecord ? "Record !" : "Record " + record), bx + bw - 12 * d, by + 70 * d, 11 * d, 0xCCFFFFFF, Paint.Align.RIGHT);

            // ---- étoiles
            int et = etoiles();
            for (int i = 0; i < 5; i++) {
                boolean allume = i < et && !(i == et - 1 && (temps / 10) % 2 == 0 && etoilesProche());
                texte(c, "★", bx + (14 + i * 22) * d, by + bh + 22 * d, 22 * d, allume ? 0xFFFFD600 : 0x55FFFFFF, Paint.Align.LEFT);
            }

            // ---- mission
            String mt;
            if (mEtat == 1) mt = "Mission : récupère le colis jaune (" + (int) (dist(jx, jy, mAx, mAy) / 10f) + " m)";
            else if (mEtat == 2) mt = "Livre le colis au point vert (" + (int) (dist(jx, jy, mBx, mBy) / 10f) + " m) · "
                    + Math.max(0, mTemps / 60) + " s";
            else mt = "Balade-toi dans la ville…";
            texte(c, mt, w / 2f + 60 * d, h - 14 * d, 13 * d, 0xDDFFFFFF, Paint.Align.CENTER);

            // ---- commandes
            aR = 34f * d;
            aX = w - 188f * d;
            aY = h - 56f * d;
            tR = 46f * d;
            tX = w - 72f * d;
            tY = h - 82f * d;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x881E78DC);
            c.drawCircle(aX, aY, aR, p);
            texte(c, voitJ != null ? "SORTIR" : "ENTRER", aX, aY + 5 * d, 12 * d, 0xFFFFFFFF, Paint.Align.CENTER);
            boolean enVoiture = voitJ != null;
            boolean appuye = enVoiture ? freinId != -1 : tirId != -1;
            p.setStyle(Paint.Style.FILL);
            p.setColor(appuye ? 0xCCFF7043 : (enVoiture ? 0x88FB8C00 : 0x88D32F2F));
            c.drawCircle(tX, tY, tR, p);
            texte(c, enVoiture ? "FREIN" : "TIRER", tX, tY + 5 * d, 15 * d, 0xFFFFFFFF, Paint.Align.CENTER);

            // joystick
            float jxc = joyId != -1 ? joyOx : 92f * d, jyc = joyId != -1 ? joyOy : h - 92f * d;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x22FFFFFF);
            c.drawCircle(jxc, jyc, 55f * d, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * d);
            p.setColor(0x66FFFFFF);
            c.drawCircle(jxc, jyc, 55f * d, p);
            float tx = jxc, ty = jyc;
            if (joyId != -1) {
                float dx = joyX - joyOx, dy = joyY - joyOy;
                float len = (float) Math.hypot(dx, dy);
                float lim = 55f * d;
                if (len > lim) {
                    dx = dx / len * lim;
                    dy = dy / len * lim;
                }
                tx += dx;
                ty += dy;
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(joyId != -1 ? 0xAAFFFFFF : 0x55FFFFFF);
            c.drawCircle(tx, ty, 22f * d, p);

            // ---- message
            if (SystemClock.uptimeMillis() < messageFin) {
                texte(c, message, w / 2f, 40 * d, 17 * d, 0xFFFFFFFF, Paint.Align.CENTER);
            }
        }

        private boolean etoilesProche() {
            for (Voiture v : voitures) if (v.police && !v.epave && dist(v.x, v.y, jx, jy) < 350f) return false;
            return true;     // clignote quand la police ne te voit plus
        }

        private void barre(Canvas c, float x, float y, float w, float hh, float frac, int couleur) {
            frac = Math.max(0f, Math.min(1f, frac));
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x66FFFFFF);
            r.set(x, y, x + w, y + hh);
            c.drawRoundRect(r, hh / 2, hh / 2, p);
            p.setColor(couleur);
            r.set(x, y, x + w * frac, y + hh);
            c.drawRoundRect(r, hh / 2, hh / 2, p);
        }

        private void dessinerMort(Canvas c, int w, int h) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x99000000);
            c.drawRect(0, 0, w, h, p);
            texte(c, "WASTED", w / 2f, h / 2f + 14 * d, 56 * d, 0xFFD32F2F, Paint.Align.CENTER);
            texte(c, "Tu perds une partie de ton argent", w / 2f, h / 2f + 46 * d, 14 * d, 0xDDFFFFFF, Paint.Align.CENTER);
        }
    }
}
