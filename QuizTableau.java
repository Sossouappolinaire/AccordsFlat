package bj.radio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * Plateau de « Questions pour un champion » : à gauche et à droite la photo, le nom et le score de deux joueurs
 * (dans un hexagone), au centre la colonne 4 – 3 – 2 – 1. Le chiffre allumé est la valeur de la question à cet instant :
 * il descend avec le temps, et c'est lui que gagne celui qui répond juste en premier.
 */
final class QuizTableau extends View {

    /** Un côté du plateau. */
    private static final class Cote {
        String nom = "", avatar = "?", hexa = "";
        Bitmap photo;
        boolean lueur;
    }

    private static final int[] COULEURS = {0xFF1E88E5, 0xFF8E24AA, 0xFF00897B, 0xFFF4511E, 0xFF6D4C41, 0xFF3949AB, 0xFFC0CA33, 0xFFD81B60};

    private final Cote g = new Cote(), d = new Cote();
    private int niveau = 0;                       // 4, 3, 2 ou 1 allumé ; 0 = aucun
    private final float dp;
    private final Paint rempli = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trait = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint texte = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path chemin = new Path();
    private final RectF rect = new RectF();

    QuizTableau(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        trait.setStyle(Paint.Style.STROKE);
        texte.setTextAlign(Paint.Align.CENTER);
        texte.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    void gauche(String nom, Bitmap photo, String avatar, String hexa) { remplir(g, nom, photo, avatar, hexa); }

    void droite(String nom, Bitmap photo, String avatar, String hexa) { remplir(d, nom, photo, avatar, hexa); }

    private void remplir(Cote c, String nom, Bitmap photo, String avatar, String hexa) {
        c.nom = nom == null ? "" : nom;
        c.photo = photo;
        c.avatar = avatar == null || avatar.isEmpty() ? "?" : avatar;
        c.hexa = hexa == null ? "" : hexa;
        invalidate();
    }

    /** Allume 4, 3, 2 ou 1 (0 éteint tout). */
    void niveau(int n) {
        if (n == niveau) return;
        niveau = n;
        invalidate();
    }

    /** Entoure d'or le joueur qui vient de gagner le point : -1 gauche, 1 droite, 0 personne. */
    void lueur(int cote) {
        boolean a = cote < 0, b = cote > 0;
        if (g.lueur == a && d.lueur == b) return;
        g.lueur = a;
        d.lueur = b;
        invalidate();
    }

    @Override
    protected void onMeasure(int largeur, int hauteur) {
        int w = MeasureSpec.getSize(largeur);
        setMeasuredDimension(w, (int) (w * 0.60f)); // même proportions que le plateau de la télévision
    }

    private static final float[] COL_X = {0.470f, 0.525f, 0.470f, 0.525f};   // colonne 4-3-2-1 en zigzag
    private static final float[] COL_Y = {0.215f, 0.425f, 0.615f, 0.800f};

    private final Rect src = new Rect();

    /** Hexagone « bouton de télévision » : dégradé, double contour métallique. */
    private void hexa(Canvas c, float cx, float cy, float w, float h, int haut, int bas, int bord, float epaisseur) {
        chemin.reset();
        chemin.moveTo(cx - w / 2, cy);
        chemin.lineTo(cx - w / 4, cy - h / 2);
        chemin.lineTo(cx + w / 4, cy - h / 2);
        chemin.lineTo(cx + w / 2, cy);
        chemin.lineTo(cx + w / 4, cy + h / 2);
        chemin.lineTo(cx - w / 4, cy + h / 2);
        chemin.close();
        rempli.setStyle(Paint.Style.FILL);
        rempli.setShader(new LinearGradient(0, cy - h / 2, 0, cy + h / 2, haut, bas, Shader.TileMode.CLAMP));
        c.drawPath(chemin, rempli);
        rempli.setShader(null);
        trait.setStrokeWidth(epaisseur * dp);
        trait.setColor(bord);
        c.drawPath(chemin, trait);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float mid = w / 2;
        float bande = Math.max(3 * dp, w * 0.012f);

        c.save();
        chemin.reset();
        rect.set(0, 0, w, h);
        chemin.addRoundRect(rect, 12 * dp, 12 * dp, Path.Direction.CW);
        c.clipPath(chemin);

        // fond de plateau
        rempli.setStyle(Paint.Style.FILL);
        rempli.setShader(new LinearGradient(0, 0, w, h, 0xFF050A1C, 0xFF10285A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, rempli);
        rempli.setShader(null);

        // les deux photos, chacune sur sa moitié
        moitie(c, g, 0, mid - bande / 2, h);
        moitie(c, d, mid + bande / 2, w, h);

        // barre centrale métallique
        rempli.setShader(new LinearGradient(mid - bande, 0, mid + bande, 0, 0xFF6C7A8C, 0xFFD5DEE8, Shader.TileMode.CLAMP));
        c.drawRect(mid - bande / 2, 0, mid + bande / 2, h, rempli);
        rempli.setShader(null);

        // colonne 4 – 3 – 2 – 1 ; le chiffre allumé est la valeur de la question à cet instant
        float hw = Math.min(w * 0.155f, 62 * dp), hh = hw * 0.72f;
        for (int i = 0; i < 4; i++) {
            int valeur = 4 - i;
            float cx = w * COL_X[i], cy = h * COL_Y[i];
            boolean allume = valeur == niveau;
            boolean passe = niveau != 0 && valeur > niveau;
            float k = allume ? 1.1f : 1f;
            if (allume) {
                hexa(c, cx, cy, hw * k, hh * k, 0xFF123E7C, 0xFFEF6C00, 0xFFFFE082, 3.5f);
            } else if (passe) {
                hexa(c, cx, cy, hw, hh, 0xFF6D4C41, 0xFF3E2723, 0xFF8D8D8D, 2.5f);
            } else {
                hexa(c, cx, cy, hw, hh, 0xFFFFA726, 0xFFE65100, 0xFF9CC3E6, 3f);
            }
            texte.setColor(allume ? 0xFFBFE3FF : passe ? 0xFFBCAAA4 : 0xFFFFF176);
            texte.setTextSize(hh * (allume ? 0.78f : 0.7f));
            c.drawText(String.valueOf(valeur), cx, cy + hh * 0.26f, texte);
        }
        c.restore();

        score(c, g, w * 0.23f, h * 0.835f);
        score(c, d, w * 0.77f, h * 0.835f);
    }

    /** Photo du joueur qui remplit toute sa moitié (recadrée au centre), ou grande initiale sur fond de couleur. */
    private void moitie(Canvas c, Cote s, float x0, float x1, float h) {
        rect.set(x0, 0, x1, h);
        if (s.photo != null) {
            float rw = x1 - x0, ratio = rw / h;
            int bw = s.photo.getWidth(), bh = s.photo.getHeight();
            int sw = bw, sh = bh;
            if ((float) bw / bh > ratio) sw = Math.round(bh * ratio); else sh = Math.round(bw / ratio);
            int sx = (bw - sw) / 2, sy = Math.max(0, (int) ((bh - sh) * 0.25f)); // on garde plutôt le haut (le visage)
            src.set(sx, sy, sx + sw, sy + sh);
            c.drawBitmap(s.photo, src, rect, rempli);
        } else {
            int base = COULEURS[Math.abs(s.nom.hashCode()) % COULEURS.length];
            rempli.setStyle(Paint.Style.FILL);
            rempli.setShader(new LinearGradient(0, 0, 0, h, base, 0xFF0B1220, Shader.TileMode.CLAMP));
            c.drawRect(rect, rempli);
            rempli.setShader(null);
            texte.setColor(0x55FFFFFF);
            texte.setTextSize(h * 0.5f);
            c.drawText(s.avatar, (x0 + x1) / 2, h * 0.58f, texte);
        }
        // nom, sur un bandeau translucide en haut de la photo
        if (!s.nom.isEmpty()) {
            texte.setTextSize(12 * dp);
            String nom = s.nom;
            float max = (x1 - x0) * 0.8f;
            while (nom.length() > 1 && texte.measureText(nom) > max) nom = nom.substring(0, nom.length() - 1);
            if (!nom.equals(s.nom)) nom = nom.trim() + "…";
            float tw = texte.measureText(nom);
            float cx = (x0 + x1) / 2;
            rempli.setStyle(Paint.Style.FILL);
            rempli.setColor(0x990B1B3A);
            rect.set(cx - tw / 2 - 10 * dp, 6 * dp, cx + tw / 2 + 10 * dp, 6 * dp + 22 * dp);
            c.drawRoundRect(rect, 11 * dp, 11 * dp, rempli);
            texte.setColor(0xFFFFFFFF);
            c.drawText(nom, cx, 6 * dp + 16 * dp, texte);
        }
        if (s.lueur) { // le joueur qui vient de marquer : cadre doré
            trait.setStrokeWidth(5 * dp);
            trait.setColor(0xFFFFD54F);
            c.drawRect(x0 + 2.5f * dp, 2.5f * dp, x1 - 2.5f * dp, h - 2.5f * dp, trait);
        }
    }

    /** Score dans un hexagone bleu nuit à contour doré, en bas de la photo. */
    private void score(Canvas c, Cote s, float cx, float cy) {
        float hw = Math.min(getWidth() * 0.19f, 74 * dp), hh = hw * 0.46f;
        hexa(c, cx, cy, hw, hh, 0xFF1A4F8F, 0xFF0A1B3A, s.lueur ? 0xFFFFD54F : 0xFFFFB300, 3f);
        texte.setColor(0xFFFFFFFF);
        texte.setTextSize(hh * 0.62f);
        c.drawText(s.hexa, cx, cy + hh * 0.22f, texte);
    }
}
