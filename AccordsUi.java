package bj.radio;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;

/** Thème et petits utilitaires de style (couleurs, dégradés, effets d'appui et d'entrée). */
public class AccordsUi {

    public static final int BG = 0xFF0D1024;
    public static final int CARD = 0xFF171B38;
    public static final int STROKE = 0xFF2A3060;
    public static final int TEXT = 0xFFEDEFFF;
    public static final int MUTED = 0xFF9AA3C7;
    public static final int DARK = 0xFF14172E;
    public static final int AMBER = 0xFFFFC857;
    public static final int ORANGE = 0xFFFF9F43;
    public static final int VIOLET = 0xFF6D4AFF;
    public static final int MAGENTA = 0xFFC13FD6;
    public static final int TEAL = 0xFF14B8A6;
    public static final int BLUE = 0xFF3B82F6;
    public static final int INDIGO = 0xFF6366F1;

    public static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static GradientDrawable gradient(int c1, int c2, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        g.setCornerRadius(radius);
        return g;
    }

    public static GradientDrawable solid(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private static Drawable ripple(Drawable content, float radius, boolean oval) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        if (oval) mask.setShape(GradientDrawable.OVAL); else mask.setCornerRadius(radius);
        return new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), content, mask);
    }

    /** Léger effet de rétrécissement quand on appuie. */
    public static void press(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private static void base(Button b, String text, int textColor, float sp) {
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        b.setTextSize(sp);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        press(b);
    }

    /** Gros bouton à dégradé, coins arrondis, relief et ondulation. */
    public static Button button(Context c, String text, int c1, int c2, int textColor) {
        Button b = new Button(c);
        base(b, text, textColor, 15);
        float r = dp(c, 16);
        b.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        b.setBackground(ripple(gradient(c1, c2, r), r, false));
        b.setElevation(dp(c, 4));
        return b;
    }

    /** Bouton sans fond (action secondaire). */
    public static Button ghost(Context c, String text) {
        Button b = new Button(c);
        base(b, text, MUTED, 13);
        float r = dp(c, 14);
        b.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        GradientDrawable g = solid(0x00000000, r);
        g.setStroke(dp(c, 1), STROKE);
        b.setBackground(ripple(g, r, false));
        return b;
    }

    /** Bouton rond à dégradé (lecture). */
    public static Button circle(Context c, String text, int c1, int c2, int textColor) {
        Button b = new Button(c);
        base(b, text, textColor, 24);
        b.setPadding(0, 0, 0, 0);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setShape(GradientDrawable.OVAL);
        b.setBackground(ripple(g, 0, true));
        b.setElevation(dp(c, 6));
        return b;
    }

    /** Petit bouton rond transparent (− et +). */
    public static Button iconButton(Context c, String text) {
        Button b = new Button(c);
        base(b, text, Color.WHITE, 22);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(ripple(solid(0x00000000, 0), 0, true));
        return b;
    }

    /** Pastille translucide (PDF). */
    public static Button chip(Context c, String text) {
        Button b = new Button(c);
        base(b, text, Color.WHITE, 13);
        float r = dp(c, 20);
        b.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        b.setBackground(ripple(solid(0x26FFFFFF, r), r, false));
        return b;
    }

    /** Fond de carte arrondi avec fin contour. */
    public static void card(View v, Context c) {
        GradientDrawable g = solid(CARD, dp(c, 20));
        g.setStroke(dp(c, 1), STROKE);
        v.setBackground(g);
        v.setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14));
    }

    /** Apparition en fondu avec léger glissement vers le haut. */
    public static void enter(View v, int delayMs) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 18));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(380)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    public static LinearLayout.LayoutParams lp(Context c, int w, int h, int topDp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, h);
        l.topMargin = dp(c, topDp);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }
}
