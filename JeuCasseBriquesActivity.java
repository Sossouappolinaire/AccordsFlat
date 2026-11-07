package bj.radio;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Random;

/**
 * Cœur Casse-Briques : un casse-briques néon dont les briques forment des cœurs.
 * On glisse le doigt pour déplacer la raquette, on touche pour lancer la boule.
 * Briques normales, briques en métal (3 coups), astéroïdes (2 coups) ; à partir du niveau 2 des astéroïdes tombent du ciel.
 * Bonus qui tombent : raquette large, trois boules, ralenti, vie en plus. Combos qui multiplient les points.
 */
public class JeuCasseBriquesActivity extends Activity {

    private VueCasse vue;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Ui.fenetre(this);
        getWindow().setStatusBarColor(0xFF050B1A);
        getWindow().setNavigationBarColor(0xFF050B1A);
        vue = new VueCasse(this);
        setContentView(vue);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (vue != null) vue.pauser();
    }

    @Override
    public void onBackPressed() {
        if (vue == null || !vue.retour()) super.onBackPressed();
    }

    // ------------------------------------------------------------------------------------------------

    private final class VueCasse extends View {

        private static final float W = 380f, H = 620f, HAUT = 62f, BR = 7f;
        private static final float PH = 14f, PY = 552f, PW0 = 84f, PW_LARGE = 128f;
        private static final int MENU = 0, PRET = 1, JEU = 2, PAUSE = 3, FIN = 4;
        private static final float STEP = 1f / 120f;

        /** # brique, L/R triangle, M métal (3 coups), A astéroïde (2 coups). */
        private final String[][] NIVEAUX = {
                {"..####..####..", ".###A########.", "#########A####", "##############", "######A#######", ".L##########R.",
                        "..LA#######R..", "...L######R...", "....L####R....", ".....L##R.....", "......LR......"},
                {"..####..####..", ".####L..R####.", "###........###", "###..AAAA..###", "###........###", ".L##......##R.",
                        "..L##....##R..", "...L##..##R...", "....LMMMMR....", ".....LMMR.....", "......LR......"},
                {".##.##..##.##.", "#A##########A#", "##############", ".L###R..L###R.", "..L#R....L#R..", "...M......M...",
                        "..............", "MMMM......MMMM", "..............", "..AA......AA.."},
                {"..MMMM..MMMM..", ".M##########M.", "M############M", "M##A######A##M", "M############M", ".LM########MR.",
                        "..LM######MR..", "...L######R...", "....LAAAAR....", ".....L##R.....", "......LR......"},
                {"......##......", ".....####.....", "....######....", "...##A##A##...", "..##########..", ".############.",
                        "..##MMMMMM##..", "...##A##A##...", "....######....", ".....####.....", "......##......"},
                {".##.##..##.##.", "####LR..LR####", "#A##########A#", ".############.", "..L########R..", "...L######R...",
                        "....L####R....", ".....L##R.....", "......LR......"}
        };

        private final int[][] PAL = {
                {0xFFC03FD0, 0xFFEC8CF8, 0xFF7D1C90}, {0xFF6F4FD8, 0xFFA995F7, 0xFF3F2A9C}, {0xFFE9EAEE, 0xFFFFFFFF, 0xFFA8ABB8},
                {0xFF8FD01F, 0xFFC8F566, 0xFF5A9407}, {0xFF1FB5A8, 0xFF78EADF, 0xFF0B756C}, {0xFFE63946, 0xFFFF9099, 0xFF97202B}};

        private final class Brique {
            boolean rond;
            int type, hp, maxhp, pal;      // type : 0 normale, 1 métal, 2 astéroïde
            float flash;
            float[] px, py;
            float x0, y0, x1, y1, cx, cy, r, cxm, cym;
        }

        private final class Boule {
            float x, y, vx, vy;
            final float[] tx = new float[12], ty = new float[12];
            int nt;
        }

        private final class Caillou { float x, y, vy, r; }

        private final class Capsule { float x, y; char t; }

        private final class Part { float x, y, vx, vy, vie; int col; }

        private final class Flot { float x, y, vie; String t; int col; }

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path chemin = new Path();
        private final RectF r = new RectF();
        private final Random rnd = new Random();
        private final float d;

        private final ArrayList<Brique> briques = new ArrayList<>();
        private final ArrayList<Boule> boules = new ArrayList<>();
        private final ArrayList<Caillou> cailloux = new ArrayList<>();
        private final ArrayList<Capsule> capsules = new ArrayList<>();
        private final ArrayList<Part> parts = new ArrayList<>();
        private final ArrayList<Flot> flots = new ArrayList<>();

        private final float[] sx = new float[60], sy = new float[60], sr = new float[60], sa = new float[60], sp = new float[60];
        private final float[] hx = new float[9], hy = new float[9], hs = new float[9], hv = new float[9];

        private int etat = MENU, niveau = 1, vies = 3, score, record, combo;
        private int totalB, totalA, faitB, faitA;
        private float temps, tremble, flashRouge, rochTimer, padX, padW = PW0, largeT, lentT, acc, msgT;
        private String msg = "";
        private boolean nouveauRecord;
        private long dernier;
        private float echelle = 1f, offX, offY, lastLx, downX, downY;
        private boolean glisse, appuye;

        private final RectF bJouer = new RectF(), bQuitter = new RectF(), bPause = new RectF(),
                bReprendre = new RectF(), bQuitter2 = new RectF(), bRejouer = new RectF(), bMenu = new RectF();

        private Shader fond, asteroide;

        VueCasse(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            record = JeuxActivity.record(c, "casse");
            for (int i = 0; i < sx.length; i++) {
                sx[i] = rnd.nextFloat() * W;
                sy[i] = rnd.nextFloat() * H;
                sr[i] = 0.5f + rnd.nextFloat() * 1.2f;
                sa[i] = 0.3f + rnd.nextFloat() * 0.7f;
                sp[i] = rnd.nextFloat() * 6f;
            }
            for (int i = 0; i < hx.length; i++) recyclerCoeur(i, true);
            bJouer.set(70, 380, 310, 448);
            bQuitter.set(110, 466, 270, 514);
            bReprendre.set(70, 300, 310, 362);
            bQuitter2.set(110, 380, 270, 426);
            bRejouer.set(70, 340, 310, 402);
            bMenu.set(110, 420, 270, 466);
            bPause.set(W - 50, 8, W - 8, 50);
            padX = (W - PW0) / 2f;
        }

        private void recyclerCoeur(int i, boolean partout) {
            hx[i] = rnd.nextFloat() * W;
            hy[i] = partout ? rnd.nextFloat() * H : -30f;
            hs[i] = 6f + rnd.nextFloat() * 12f;
            hv[i] = 18f + rnd.nextFloat() * 30f;
        }

        // ------------------------------------------------------------------ vie du jeu

        void pauser() {
            if (etat == JEU) etat = PAUSE;
            invalidate();
        }

        /** Bouton retour : vrai si on a géré (pause / reprise), faux pour quitter. */
        boolean retour() {
            if (etat == JEU) { etat = PAUSE; return true; }
            if (etat == PAUSE) { reprendre(); return true; }
            return false;
        }

        private void reprendre() {
            etat = JEU;
            dernier = SystemClock.uptimeMillis();
            acc = 0;
        }

        private float vitesse() {
            float v = Math.min(430f + 28f * (niveau - 1), 760f);
            return lentT > 0 ? v * 0.72f : v;
        }

        private void nouvellePartie() {
            niveau = 1; vies = 3; score = 0; combo = 0;
            parts.clear(); flots.clear(); capsules.clear();
            padW = PW0; largeT = 0; lentT = 0;
            record = JeuxActivity.record(JeuCasseBriquesActivity.this, "casse");
            nouveauRecord = false;
            construire(niveau);
            remettreBoule();
            msg = "Niveau 1";
            msgT = 2.5f;
        }

        private void construire(int n) {
            briques.clear();
            cailloux.clear();
            capsules.clear();
            totalB = totalA = faitB = faitA = 0;
            String[] lignes = NIVEAUX[(n - 1) % NIVEAUX.length];
            float cw = 24f, ch = 16f, x0 = (W - 14 * cw) / 2f, y0 = HAUT + 22f;
            for (int ro = 0; ro < lignes.length; ro++) {
                for (int c = 0; c < 14; c++) {
                    char k = c < lignes[ro].length() ? lignes[ro].charAt(c) : '.';
                    if (k == '.') continue;
                    float x = x0 + c * cw, y = y0 + ro * ch;
                    Brique b = new Brique();
                    b.pal = (c + ro) % 6;
                    b.hp = 1;
                    if (k == 'A') {
                        b.rond = true; b.type = 2; b.hp = 2;
                        b.cx = x + cw / 2f; b.cy = y + ch / 2f; b.r = 10.5f;
                        b.x0 = b.cx - b.r; b.x1 = b.cx + b.r; b.y0 = b.cy - b.r; b.y1 = b.cy + b.r;
                        totalA++;
                    } else {
                        if (k == 'L') { b.px = new float[]{x, x + cw, x + cw}; b.py = new float[]{y, y, y + ch}; }
                        else if (k == 'R') { b.px = new float[]{x, x + cw, x}; b.py = new float[]{y, y, y + ch}; }
                        else { b.px = new float[]{x, x + cw, x + cw, x}; b.py = new float[]{y, y, y + ch, y + ch}; }
                        if (k == 'M') { b.type = 1; b.hp = 3; }
                        b.x0 = x; b.x1 = x + cw; b.y0 = y; b.y1 = y + ch;
                        float mx = 0, my = 0;
                        for (int i = 0; i < b.px.length; i++) { mx += b.px[i]; my += b.py[i]; }
                        b.cxm = mx / b.px.length; b.cym = my / b.px.length;
                        totalB++;
                    }
                    b.maxhp = b.hp;
                    briques.add(b);
                }
            }
            rochTimer = 4f + rnd.nextFloat() * 2f;
        }

        private void remettreBoule() {
            etat = PRET;
            boules.clear();
            Boule b = new Boule();
            b.x = padX + padW / 2f;
            b.y = PY - BR - 1f;
            boules.add(b);
            combo = 0;
        }

        private void lancer() {
            etat = JEU;
            dernier = SystemClock.uptimeMillis();
            acc = 0;
            msg = "";
            Boule b = boules.get(0);
            float a = (rnd.nextBoolean() ? 1 : -1) * 0.35f;
            float v = vitesse();
            b.vx = v * (float) Math.sin(a);
            b.vy = -v * (float) Math.cos(a);
            b.nt = 0;
        }

        private void perdreVie() {
            vies--;
            flashRouge = 0.35f;
            tremble = 9f;
            combo = 0;
            capsules.clear();
            if (vies <= 0) {
                Sons.jouer(getContext(), Sons.PERDU);
                etat = FIN;
                nouveauRecord = score > record;
                JeuxActivity.enregistrer(getContext(), "casse", score);
                record = Math.max(record, score);
            } else {
                Sons.jouer(getContext(), Sons.PERDRE_VIE);
                remettreBoule();
                msg = vies == 1 ? "Dernière vie !" : "Il reste " + vies + " vies";
                msgT = 2f;
            }
        }

        // ------------------------------------------------------------------ collisions

        private float[] segProche(float px, float py, float ax, float ay, float bx, float by) {
            float dx = bx - ax, dy = by - ay;
            float t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
            t = Math.max(0f, Math.min(1f, t));
            return new float[]{ax + dx * t, ay + dy * t};
        }

        /** Renvoie {nx, ny, pénétration} ou null. */
        private float[] toucheBrique(Brique b, float x, float y) {
            if (b.rond) {
                float dx = x - b.cx, dy = y - b.cy, dist = (float) Math.hypot(dx, dy), R = b.r + BR;
                if (dist >= R) return null;
                float L = dist == 0 ? 1f : dist;
                return new float[]{dx / L, dy / L, R - dist};
            }
            int n = b.px.length;
            float best = 1e9f, bcx = 0, bcy = 0;
            int bi = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float[] q = segProche(x, y, b.px[i], b.py[i], b.px[j], b.py[j]);
                float dd = (float) Math.hypot(x - q[0], y - q[1]);
                if (dd < best) { best = dd; bcx = q[0]; bcy = q[1]; bi = i; }
            }
            boolean dedans = true;
            int signe = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float cr = (b.px[j] - b.px[i]) * (y - b.py[i]) - (b.py[j] - b.py[i]) * (x - b.px[i]);
                if (cr != 0) {
                    int s = cr > 0 ? 1 : -1;
                    if (signe == 0) signe = s; else if (s != signe) { dedans = false; break; }
                }
            }
            if (dedans) {
                int j = (bi + 1) % n;
                float nx = b.py[j] - b.py[bi], ny = -(b.px[j] - b.px[bi]);
                float L = (float) Math.hypot(nx, ny);
                if (L == 0) L = 1f;
                nx /= L; ny /= L;
                if (nx * (b.cxm - b.px[bi]) + ny * (b.cym - b.py[bi]) > 0) { nx = -nx; ny = -ny; }
                return new float[]{nx, ny, BR + best};
            }
            if (best >= BR) return null;
            float nx = x - bcx, ny = y - bcy;
            float L = (float) Math.hypot(nx, ny);
            if (L == 0) L = 1f;
            return new float[]{nx / L, ny / L, BR - best};
        }

        private void eclat(float x, float y, int col, int n) {
            for (int i = 0; i < n && parts.size() < 220; i++) {
                Part q = new Part();
                float a = rnd.nextFloat() * 6.283f, s = 40f + rnd.nextFloat() * 170f;
                q.x = x; q.y = y; q.vx = (float) Math.cos(a) * s; q.vy = (float) Math.sin(a) * s;
                q.vie = 0.4f + rnd.nextFloat() * 0.5f; q.col = col;
                parts.add(q);
            }
        }

        private void gain(float x, float y, int base, int col) {
            int mult = 1 + Math.min(combo / 3, 4);
            int pts = base * mult;
            score += pts;
            Flot f = new Flot();
            f.x = x; f.y = y; f.vie = 0.8f; f.col = col;
            f.t = mult > 1 ? "+" + pts + " x" + mult : "+" + pts;
            if (flots.size() < 30) flots.add(f);
        }

        private void abimer(Brique b) {
            b.hp--;
            b.flash = 0.12f;
            combo++;
            float mx = b.rond ? b.cx : (b.x0 + b.x1) / 2f, my = b.rond ? b.cy : (b.y0 + b.y1) / 2f;
            if (b.hp <= 0) {
                briques.remove(b);
                if (b.type == 2) { faitA++; gain(mx, my, 30, 0xFFE8D8C4); eclat(mx, my, 0xFF9A8878, 14); Sons.jouer(getContext(), Sons.EXPLOSION); }
                else if (b.type == 1) { faitB++; gain(mx, my, 50, 0xFFC9D2DC); eclat(mx, my, 0xFF9AA4B0, 14); Sons.jouer(getContext(), Sons.TOUCHE); }
                else { faitB++; gain(mx, my, 10, PAL[b.pal][1]); eclat(mx, my, PAL[b.pal][0], 9); Sons.jouer(getContext(), Sons.TOUCHE); }
                if (rnd.nextFloat() < (b.type == 0 ? 0.13f : 0.30f)) {
                    Capsule c = new Capsule();
                    c.x = mx; c.y = my;
                    float t = rnd.nextFloat();
                    c.t = t < 0.34f ? 'L' : t < 0.62f ? 'M' : t < 0.86f ? 'S' : 'V';
                    capsules.add(c);
                }
                if (score > record) record = score;
            } else {
                Sons.jouer(getContext(), Sons.PAS);
            }
        }

        private void corrigerAngle(Boule b) {
            float sp = vitesse();
            float m = (float) Math.hypot(b.vx, b.vy);
            if (m == 0) m = 1f;
            b.vx *= sp / m; b.vy *= sp / m;
            float minY = 0.22f * sp;
            if (Math.abs(b.vy) < minY) {
                b.vy = (b.vy < 0 ? -1 : 1) * minY;
                b.vx = (b.vx < 0 ? -1 : 1) * (float) Math.sqrt(sp * sp - minY * minY);
            }
        }

        /** Avance une boule d'un pas ; renvoie faux si elle est tombée. */
        private boolean pasBoule(Boule b, float dt) {
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            if (b.x < BR) { b.x = BR; b.vx = Math.abs(b.vx); }
            if (b.x > W - BR) { b.x = W - BR; b.vx = -Math.abs(b.vx); }
            if (b.y < HAUT + BR) { b.y = HAUT + BR; b.vy = Math.abs(b.vy); }
            // raquette
            if (b.vy > 0 && b.y + BR >= PY && b.y - BR <= PY + PH && b.x >= padX - BR && b.x <= padX + padW + BR) {
                float rel = Math.max(-1f, Math.min(1f, (b.x - (padX + padW / 2f)) / (padW / 2f)));
                float a = rel * 1.05f, v = vitesse();
                b.vx = v * (float) Math.sin(a);
                b.vy = -v * (float) Math.cos(a);
                b.y = PY - BR;
                combo = 0;
                Sons.jouer(getContext(), Sons.SAUT);
            }
            // briques
            float[] meilleur = null;
            Brique cible = null;
            for (int i = 0; i < briques.size(); i++) {
                Brique k = briques.get(i);
                if (b.x < k.x0 - BR || b.x > k.x1 + BR || b.y < k.y0 - BR || b.y > k.y1 + BR) continue;
                float[] h = toucheBrique(k, b.x, b.y);
                if (h != null && (meilleur == null || h[2] > meilleur[2])) { meilleur = h; cible = k; }
            }
            if (meilleur != null) {
                b.x += meilleur[0] * meilleur[2];
                b.y += meilleur[1] * meilleur[2];
                float dot = b.vx * meilleur[0] + b.vy * meilleur[1];
                if (dot < 0) { b.vx -= 2 * dot * meilleur[0]; b.vy -= 2 * dot * meilleur[1]; }
                abimer(cible);
                corrigerAngle(b);
                if (briques.isEmpty()) niveauSuivant();
            }
            return b.y <= H + BR;
        }

        private void niveauSuivant() {
            niveau++;
            score += 100;
            if (score > record) record = score;
            construire(niveau);
            remettreBoule();
            msg = "Bravo ! Niveau " + niveau;
            msgT = 2.5f;
            Sons.jouer(getContext(), Sons.GAGNE);
        }

        private void majCailloux(float dt) {
            if (niveau >= 2) {
                rochTimer -= dt;
                if (rochTimer <= 0) {
                    Caillou k = new Caillou();
                    k.x = 20f + rnd.nextFloat() * (W - 40f);
                    k.y = HAUT + 6f;
                    k.vy = 80f + Math.min(niveau, 8) * 14f + rnd.nextFloat() * 40f;
                    k.r = 8f + rnd.nextFloat() * 3f;
                    cailloux.add(k);
                    rochTimer = Math.max(2.2f, 6.5f - niveau * 0.5f) + rnd.nextFloat() * 2f;
                }
            }
            for (int i = cailloux.size() - 1; i >= 0; i--) {
                Caillou k = cailloux.get(i);
                k.y += k.vy * dt;
                if (k.y > H + 20f) { cailloux.remove(i); continue; }
                boolean pris = false;
                for (Boule b : boules) {
                    if (Math.hypot(b.x - k.x, b.y - k.y) < k.r + BR) {
                        gain(k.x, k.y, 20, 0xFFE8D8C4);
                        eclat(k.x, k.y, 0xFF9A8878, 10);
                        Sons.jouer(getContext(), Sons.EXPLOSION);
                        pris = true;
                        break;
                    }
                }
                if (pris) { cailloux.remove(i); continue; }
                float cx = Math.max(padX, Math.min(k.x, padX + padW)), cy = Math.max(PY, Math.min(k.y, PY + PH));
                if (Math.hypot(k.x - cx, k.y - cy) < k.r) {
                    cailloux.remove(i);
                    eclat(k.x, k.y, 0xFFFF6B6B, 16);
                    perdreVie();
                    if (etat != JEU) return;
                }
            }
        }

        private void majCapsules(float dt) {
            for (int i = capsules.size() - 1; i >= 0; i--) {
                Capsule c = capsules.get(i);
                c.y += 120f * dt;
                if (c.y > H + 20f) { capsules.remove(i); continue; }
                if (c.y + 8f >= PY && c.y - 8f <= PY + PH && c.x >= padX - 14f && c.x <= padX + padW + 14f) {
                    capsules.remove(i);
                    appliquer(c.t);
                }
            }
        }

        private void appliquer(char t) {
            Sons.jouer(getContext(), Sons.PIECE);
            String m;
            if (t == 'L') { largeT = 14f; m = "Raquette large !"; }
            else if (t == 'S') { lentT = 10f; m = "Ralenti !"; }
            else if (t == 'V') { vies = Math.min(5, vies + 1); m = "Vie en plus !"; }
            else {
                m = "Trois boules !";
                ArrayList<Boule> neuves = new ArrayList<>();
                Boule src = boules.isEmpty() ? null : boules.get(0);
                if (src != null) {
                    for (int s = -1; s <= 1; s += 2) {
                        Boule n = new Boule();
                        n.x = src.x; n.y = src.y;
                        float a = s * 0.45f, ca = (float) Math.cos(a), sa2 = (float) Math.sin(a);
                        n.vx = src.vx * ca - src.vy * sa2;
                        n.vy = src.vx * sa2 + src.vy * ca;
                        if (Math.abs(n.vy) < 40f) n.vy = -vitesse() * 0.5f;
                        neuves.add(n);
                    }
                    boules.addAll(neuves);
                }
            }
            Flot f = new Flot();
            f.x = padX + padW / 2f; f.y = PY - 22f; f.vie = 1.2f; f.t = m; f.col = 0xFFFFFFFF;
            flots.add(f);
        }

        private void maj(float dt) {
            temps += dt;
            if (flashRouge > 0) flashRouge -= dt;
            if (tremble > 0) tremble = Math.max(0f, tremble - 30f * dt);
            if (msgT > 0) msgT -= dt;
            for (int i = 0; i < hx.length; i++) {
                hy[i] += hv[i] * dt;
                if (hy[i] > H + 30f) recyclerCoeur(i, false);
            }
            for (Brique b : briques) if (b.flash > 0) b.flash -= dt;
            for (int i = parts.size() - 1; i >= 0; i--) {
                Part q = parts.get(i);
                q.vie -= dt; q.x += q.vx * dt; q.y += q.vy * dt; q.vy += 260f * dt;
                if (q.vie <= 0) parts.remove(i);
            }
            for (int i = flots.size() - 1; i >= 0; i--) {
                Flot f = flots.get(i);
                f.vie -= dt; f.y -= 34f * dt;
                if (f.vie <= 0) flots.remove(i);
            }
            if (etat == JEU) {
                if (largeT > 0) largeT -= dt;
                if (lentT > 0) lentT -= dt;
                float cible = largeT > 0 ? PW_LARGE : PW0;
                float avant = padW;
                padW += (cible - padW) * Math.min(1f, 10f * dt);
                padX = Math.max(0f, Math.min(W - padW, padX - (padW - avant) / 2f));
                acc += dt;
                while (acc >= STEP && etat == JEU) {
                    for (int i = boules.size() - 1; i >= 0; i--) {
                        Boule b = boules.get(i);
                        if (!pasBoule(b, STEP)) boules.remove(i);
                        if (etat != JEU) return;
                    }
                    if (boules.isEmpty()) { perdreVie(); return; }
                    majCailloux(STEP);
                    if (etat != JEU) return;
                    acc -= STEP;
                }
                majCapsules(dt);
                for (Boule b : boules) {
                    for (int i = Math.min(b.nt, 11); i > 0; i--) { b.tx[i] = b.tx[i - 1]; b.ty[i] = b.ty[i - 1]; }
                    b.tx[0] = b.x; b.ty[0] = b.y;
                    b.nt = Math.min(12, b.nt + 1);
                }
            } else if (etat == PRET && !boules.isEmpty()) {
                Boule b = boules.get(0);
                b.x = padX + padW / 2f; b.y = PY - BR - 1f;
            }
        }

        // ------------------------------------------------------------------ dessin

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            echelle = Math.min(w / W, h / H);
            offX = (w - W * echelle) / 2f;
            offY = (h - H * echelle) / 2f;
            fond = new LinearGradient(0, 0, 0, H, new int[]{0xFF0A1636, 0xFF0B2A52, 0xFF0F3D66}, null, Shader.TileMode.CLAMP);
            asteroide = new RadialGradient(-0.3f, -0.3f, 1.3f, 0xFFA29080, 0xFF2E2620, Shader.TileMode.CLAMP);
        }

        private void coeur(Path c, float cx, float cy, float s) {
            c.reset();
            c.moveTo(cx, cy + s * 0.9f);
            c.cubicTo(cx - s * 1.6f, cy - s * 0.1f, cx - s * 0.9f, cy - s * 1.1f, cx, cy - s * 0.35f);
            c.cubicTo(cx + s * 0.9f, cy - s * 1.1f, cx + s * 1.6f, cy - s * 0.1f, cx, cy + s * 0.9f);
            c.close();
        }

        private void texte(String t, float x, float y, float taille, int col, boolean gras, Paint.Align a) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(gras ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            p.setTextAlign(a);
            p.setTextSize(taille);
            p.setColor(col);
            Canvas c = cnv;
            c.drawText(t, x, y, p);
        }

        private Canvas cnv;

        private void bouton(RectF b, String t, int c1, int c2, float taille) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, b.top, 0, b.bottom, c1, c2, Shader.TileMode.CLAMP));
            cnv.drawRoundRect(b, 16, 16, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(0x88FFFFFF);
            cnv.drawRoundRect(b, 16, 16, p);
            texte(t, b.centerX(), b.centerY() + taille * 0.35f, taille, 0xFFFFFFFF, true, Paint.Align.CENTER);
        }

        private void panneau(float x0, float y0, float x1, float y1) {
            r.set(x0, y0, x1, y1);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xE0050B1A);
            cnv.drawRoundRect(r, 20, 20, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.5f);
            p.setColor(0xFF3EC6FF);
            cnv.drawRoundRect(r, 20, 20, p);
        }

        private void dessinBrique(Brique b) {
            Canvas c = cnv;
            p.setShader(null);
            if (b.rond) {
                c.save();
                c.translate(b.cx, b.cy);
                c.scale(b.r, b.r);
                p.setStyle(Paint.Style.FILL);
                p.setShader(asteroide);
                c.drawCircle(0, 0, 1f, p);
                p.setShader(null);
                c.restore();
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.2f);
                p.setColor(0xFF1A1410);
                c.drawCircle(b.cx, b.cy, b.r, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF3A2F27);
                c.drawCircle(b.cx - b.r * 0.35f, b.cy - b.r * 0.2f, b.r * 0.28f, p);
                c.drawCircle(b.cx + b.r * 0.3f, b.cy + b.r * 0.3f, b.r * 0.22f, p);
                if (b.hp < b.maxhp) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(0xFFE8D8C4);
                    p.setStrokeWidth(1.4f);
                    c.drawLine(b.cx - b.r * 0.6f, b.cy - b.r * 0.5f, b.cx, b.cy, p);
                    c.drawLine(b.cx, b.cy, b.cx + b.r * 0.5f, b.cy + b.r * 0.6f, p);
                    c.drawLine(b.cx, b.cy, b.cx + b.r * 0.6f, b.cy - b.r * 0.4f, p);
                }
                if (b.flash > 0) { p.setStyle(Paint.Style.FILL); p.setColor(0x8CFFFFFF); c.drawCircle(b.cx, b.cy, b.r, p); }
                return;
            }
            chemin.reset();
            chemin.moveTo(b.px[0], b.py[0]);
            for (int i = 1; i < b.px.length; i++) chemin.lineTo(b.px[i], b.py[i]);
            chemin.close();
            float w = b.x1 - b.x0, h = b.y1 - b.y0;
            p.setStyle(Paint.Style.FILL);
            if (b.type == 1) {
                p.setColor(b.hp < b.maxhp ? 0xFF4A525C : 0xFF6B7480);
                c.drawPath(chemin, p);
                p.setColor(0xFF9AA4B0);
                c.drawRect(b.x0 + 1, b.y0 + 1, b.x1 - 1, b.y0 + 4, p);
                p.setColor(0xFF5FD9E8);
                c.drawCircle(b.x0 + 7, b.y0 + h / 2 + 1, 2.4f, p);
                c.drawCircle(b.x1 - 7, b.y0 + h / 2 + 1, 2.4f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1f);
                p.setColor(0xFF1C232B);
                c.drawPath(chemin, p);
                if (b.hp < b.maxhp) {
                    p.setColor(0xFFC9D2DC);
                    c.drawLine(b.x0 + 4, b.y0 + 2, b.x0 + 10, b.y0 + 8, p);
                    c.drawLine(b.x0 + 10, b.y0 + 8, b.x0 + 8, b.y1 - 2, p);
                    if (b.hp == 1) {
                        c.drawLine(b.x0 + 15, b.y0 + 2, b.x0 + 19, b.y0 + 9, p);
                        c.drawLine(b.x0 + 19, b.y0 + 9, b.x0 + 16, b.y1 - 2, p);
                    }
                }
            } else {
                int[] pa = PAL[b.pal];
                p.setShader(new LinearGradient(0, b.y0, 0, b.y1, pa[1], pa[0], Shader.TileMode.CLAMP));
                c.drawPath(chemin, p);
                p.setShader(null);
                if (b.px.length == 4) {
                    p.setColor(pa[2]);
                    c.drawRect(b.x0, b.y1 - 3, b.x1, b.y1, p);
                    p.setColor(0x66FFFFFF);
                    c.drawRect(b.x0 + 3, b.y0 + 2, b.x1 - 3, b.y0 + 5, p);
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(0.9f);
                p.setColor(0xFF0A1428);
                c.drawPath(chemin, p);
            }
            if (b.flash > 0) { p.setStyle(Paint.Style.FILL); p.setColor(0x8CFFFFFF); c.drawPath(chemin, p); }
        }

        private void dessinFond() {
            Canvas c = cnv;
            p.setStyle(Paint.Style.FILL);
            p.setShader(fond);
            c.drawRect(0, 0, W, H, p);
            p.setShader(null);
            // gros cœur lumineux en arrière-plan
            coeur(chemin, W / 2f, H * 0.42f, 150f + 6f * (float) Math.sin(temps * 1.6f));
            p.setColor(0x14FF4D6D);
            c.drawPath(chemin, p);
            for (int i = 0; i < sx.length; i++) {
                p.setColor(0xFFF4F1DE);
                p.setAlpha((int) (255 * sa[i] * (0.65f + 0.35f * Math.sin(temps * 2f + sp[i]))));
                c.drawCircle(sx[i], sy[i], sr[i], p);
            }
            for (int i = 0; i < hx.length; i++) {
                coeur(chemin, hx[i], hy[i], hs[i]);
                p.setColor(0x26FF6B8B);
                c.drawPath(chemin, p);
            }
        }

        private void dessinHud() {
            Canvas c = cnv;
            r.set(4, 4, W - 4, HAUT - 6);
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(0xCC0D2547);
            c.drawRoundRect(r, 12, 12, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f);
            p.setColor(0xFF3A6EA5);
            c.drawRoundRect(r, 12, 12, p);
            for (int i = 0; i < vies; i++) {
                coeur(chemin, 22f + i * 22f, 24f, 8f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFF4D6D);
                c.drawPath(chemin, p);
            }
            texte("Cible " + (faitB + faitA) + "/" + (totalB + totalA), 14f, 46f, 11f, 0xFF9FD8FF, false, Paint.Align.LEFT);
            texte("NIVEAU " + niveau, W / 2f, 24f, 14f, 0xFF3EC6FF, true, Paint.Align.CENTER);
            texte(String.valueOf(score), W / 2f, 45f, 18f, 0xFFFFFFFF, true, Paint.Align.CENTER);
            texte("Record " + Math.max(record, score), W - 62f, 46f, 11f, 0xFF9FD8FF, false, Paint.Align.RIGHT);
            // bouton pause
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x33FFFFFF);
            c.drawCircle(W - 28f, 24f, 14f, p);
            p.setColor(0xFFFFFFFF);
            c.drawRect(W - 33f, 17f, W - 29.5f, 31f, p);
            c.drawRect(W - 26.5f, 17f, W - 23f, 31f, p);
            if (combo >= 3) {
                int mult = 1 + Math.min(combo / 3, 4);
                texte("COMBO x" + mult, W / 2f, HAUT + 14f, 13f, 0xFFFFD166, true, Paint.Align.CENTER);
            }
        }

        private void dessinJeu() {
            Canvas c = cnv;
            for (Brique b : briques) dessinBrique(b);
            for (Caillou k : cailloux) {
                c.save();
                c.translate(k.x, k.y);
                c.scale(k.r, k.r);
                p.setStyle(Paint.Style.FILL);
                p.setShader(asteroide);
                c.drawCircle(0, 0, 1f, p);
                p.setShader(null);
                c.restore();
            }
            for (Capsule k : capsules) {
                int col = k.t == 'L' ? 0xFF2EBD6B : k.t == 'M' ? 0xFFB04FE0 : k.t == 'S' ? 0xFF2E8BFF : 0xFFFF4D6D;
                r.set(k.x - 15f, k.y - 8f, k.x + 15f, k.y + 8f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(col);
                c.drawRoundRect(r, 8, 8, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.5f);
                p.setColor(0xFFFFFFFF);
                c.drawRoundRect(r, 8, 8, p);
                texte(String.valueOf(k.t), k.x, k.y + 4.5f, 12f, 0xFFFFFFFF, true, Paint.Align.CENTER);
            }
            for (Part q : parts) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(q.col);
                p.setAlpha((int) (255 * Math.max(0f, Math.min(1f, q.vie * 2.5f))));
                c.drawRect(q.x - 2, q.y - 2, q.x + 2, q.y + 2, p);
            }
            p.setAlpha(255);
            // boules
            for (Boule b : boules) {
                p.setStyle(Paint.Style.FILL);
                for (int i = b.nt - 1; i >= 1; i--) {
                    float f = 1f - i / (float) b.nt;
                    p.setColor(0xFFBFE3FF);
                    p.setAlpha((int) (90 * f));
                    c.drawCircle(b.tx[i], b.ty[i], BR * (0.4f + 0.5f * f), p);
                }
                p.setAlpha(255);
                p.setColor(0x336EC6FF);
                c.drawCircle(b.x, b.y, BR * 2.1f, p);
                p.setShader(new RadialGradient(b.x - 2.5f, b.y - 2.5f, BR + 1f, new int[]{0xFFFFFFFF, 0xFF8FB4FF, 0xFF2B3F8F}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(b.x, b.y, BR, p);
                p.setShader(null);
            }
            // raquette
            float px = padX;
            boolean large = largeT > 0;
            if (large) {
                r.set(px - 3, PY - 3, px + padW + 3, PY + PH + 5);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x4455E08A);
                c.drawRoundRect(r, 10, 10, p);
            }
            r.set(px + 12, PY + 5, px + padW - 12, PY + 12);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFBFE8EE);
            c.drawRoundRect(r, 3, 3, p);
            p.setColor(0xFF3EC6FF);
            c.drawRect(px + padW / 2f - 14f, PY + 7.5f, px + padW / 2f + 14f, PY + 9.5f, p);
            for (int k = 0; k < 2; k++) {
                float x = k == 0 ? px : px + padW - 14f;
                r.set(x, PY, x + 14f, PY + PH + 2f);
                p.setColor(0xFFD62839);
                c.drawRoundRect(r, 5, 5, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1f);
                p.setColor(0xFF7A1420);
                c.drawRoundRect(r, 5, 5, p);
                p.setStyle(Paint.Style.FILL);
            }
            for (Flot f : flots) {
                p.setAlpha(255);
                texte(f.t, f.x, f.y, 12f, f.col, true, Paint.Align.CENTER);
            }
            // bonus actifs
            float yb = H - 14f;
            if (largeT > 0) { texte("↔ " + (int) Math.ceil(largeT) + "s", 14f, yb, 11f, 0xFF55E08A, true, Paint.Align.LEFT); }
            if (lentT > 0) { texte("🐢 " + (int) Math.ceil(lentT) + "s", W - 14f, yb, 11f, 0xFF6EB6FF, true, Paint.Align.RIGHT); }
            if (flashRouge > 0) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFF3C3C);
                p.setAlpha((int) (255 * Math.min(1f, flashRouge * 0.9f)));
                c.drawRect(0, 0, W, H, p);
                p.setAlpha(255);
            }
        }

        private void dessinMenu() {
            Canvas c = cnv;
            float pulse = 1f + 0.05f * (float) Math.sin(temps * 3f);
            coeur(chemin, W / 2f, 200f, 78f * pulse);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 120, 0, 290, 0xFFFF6B8B, 0xFFB0123A, Shader.TileMode.CLAMP));
            c.drawPath(chemin, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4f);
            p.setColor(0xFFFFD0DA);
            c.drawPath(chemin, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x55FFFFFF);
            r.set(W / 2f - 52f * pulse, 150f, W / 2f - 18f * pulse, 168f);
            c.drawOval(r, p);
            texte("CŒUR", W / 2f, 86f, 44f, 0xFFFFD166, true, Paint.Align.CENTER);
            texte("CASSE-BRIQUES", W / 2f, 330f, 30f, 0xFFFFFFFF, true, Paint.Align.CENTER);
            texte("Brise tous les cœurs !", W / 2f, 356f, 13f, 0xFF9FD8FF, false, Paint.Align.CENTER);
            bouton(bJouer, "▶  JOUER", 0xFFFF6B8B, 0xFFC1124A, 24f);
            bouton(bQuitter, "Quitter", 0xFF2A4A78, 0xFF14284A, 16f);
            texte("🏆 Record : " + record, W / 2f, 548f, 16f, 0xFFFFD166, true, Paint.Align.CENTER);
            texte("Glisse le doigt pour bouger la raquette", W / 2f, 576f, 12f, 0xFF9FD8FF, false, Paint.Align.CENTER);
            texte("Attrape les bonus : L large · M 3 boules · S ralenti · V vie", W / 2f, 596f, 10.5f, 0xFF7FA6C9, false, Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            long now = SystemClock.uptimeMillis();
            if (dernier == 0) dernier = now;
            float dt = Math.min(0.05f, Math.max(0f, (now - dernier) / 1000f));
            dernier = now;
            if (etat != PAUSE) maj(dt);

            cnv = canvas;
            canvas.drawColor(0xFF050B1A);
            canvas.save();
            float tx = tremble > 0 ? (rnd.nextFloat() - 0.5f) * tremble : 0f;
            float ty = tremble > 0 ? (rnd.nextFloat() - 0.5f) * tremble : 0f;
            canvas.translate(offX + tx * echelle, offY + ty * echelle);
            canvas.scale(echelle, echelle);
            canvas.clipRect(0, 0, W, H);
            dessinFond();
            if (etat == MENU) {
                dessinMenu();
            } else {
                dessinJeu();
                dessinHud();
                if (etat == PRET) {
                    panneau(40, 300, W - 40, 384);
                    texte(msg.isEmpty() ? "Niveau " + niveau : msg, W / 2f, 336f, 20f, 0xFFFFFFFF, true, Paint.Align.CENTER);
                    texte("Touche l'écran pour lancer la boule", W / 2f, 362f, 13f, 0xFF9FD8FF, false, Paint.Align.CENTER);
                } else if (etat == JEU && msgT > 0 && !msg.isEmpty()) {
                    texte(msg, W / 2f, 420f, 18f, 0xFFFFD166, true, Paint.Align.CENTER);
                }
                if (etat == PAUSE) {
                    panneau(40, 240, W - 40, 450);
                    texte("PAUSE", W / 2f, 284f, 26f, 0xFFFFD166, true, Paint.Align.CENTER);
                    bouton(bReprendre, "▶  Reprendre", 0xFF2EBD6B, 0xFF157A42, 20f);
                    bouton(bQuitter2, "Quitter", 0xFF2A4A78, 0xFF14284A, 16f);
                } else if (etat == FIN) {
                    panneau(30, 200, W - 30, 490);
                    texte("PARTIE TERMINÉE", W / 2f, 246f, 24f, 0xFFFF6B8B, true, Paint.Align.CENTER);
                    texte(String.valueOf(score), W / 2f, 292f, 40f, 0xFFFFFFFF, true, Paint.Align.CENTER);
                    texte(nouveauRecord ? "🏆 Nouveau record !" : "Record : " + record, W / 2f, 322f, 15f, 0xFFFFD166, true, Paint.Align.CENTER);
                    bouton(bRejouer, "↻  Rejouer", 0xFFFF6B8B, 0xFFC1124A, 20f);
                    bouton(bMenu, "Menu", 0xFF2A4A78, 0xFF14284A, 16f);
                }
            }
            canvas.restore();
            postInvalidateOnAnimation();
        }

        // ------------------------------------------------------------------ doigt

        private boolean dans(RectF b, float x, float y) { return b.contains(x, y); }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float lx = (e.getX() - offX) / echelle, ly = (e.getY() - offY) / echelle;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = lx; downY = ly; lastLx = lx; glisse = false; appuye = true;
                    if (etat == JEU && dans(bPause, lx, ly)) { etat = PAUSE; appuye = false; }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!appuye) return true;
                    if (Math.abs(lx - downX) > 8f || Math.abs(ly - downY) > 8f) glisse = true;
                    if (etat == JEU || etat == PRET) {
                        padX = Math.max(0f, Math.min(W - padW, padX + (lx - lastLx) * 1.25f));
                    }
                    lastLx = lx;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!appuye) return true;
                    appuye = false;
                    if (!glisse) tape(lx, ly);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    appuye = false;
                    return true;
                default:
                    return true;
            }
        }

        private void tape(float x, float y) {
            if (etat == MENU) {
                if (dans(bJouer, x, y)) nouvellePartie();
                else if (dans(bQuitter, x, y)) finish();
            } else if (etat == PRET) {
                lancer();
            } else if (etat == PAUSE) {
                if (dans(bReprendre, x, y)) reprendre();
                else if (dans(bQuitter2, x, y)) finish();
            } else if (etat == FIN) {
                if (dans(bRejouer, x, y)) nouvellePartie();
                else if (dans(bMenu, x, y)) { etat = MENU; record = JeuxActivity.record(getContext(), "casse"); }
            }
        }
    }
}
