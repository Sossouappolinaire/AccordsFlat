package bj.radio;

import android.animation.*;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class StockUi {
    public static final int INDIGO = 0xFF1A237E, BG = 0xFFF4F6FA, DARK = 0xFF1F2937, GRAY = 0xFF6B7280,
            GREEN = 0xFF2E7D32, ORANGE = 0xFFEF6C00, BLUE = 0xFF1565C0, PURPLE = 0xFF5E35B1,
            TEAL = 0xFF00897B, PINK = 0xFFAD1457, RED = 0xFFD32F2F;

    public static int dp(Context c, int v) { return (int) (v * c.getResources().getDisplayMetrics().density); }

    public static int lighten(int color, float f) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Color.rgb((int) (r + (255 - r) * f), (int) (g + (255 - g) * f), (int) (b + (255 - b) * f));
    }

    public static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(c, radius));
        return g;
    }

    public static GradientDrawable gradient(Context c, int color, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{lighten(color, 0.25f), color});
        g.setCornerRadius(dp(c, radius));
        return g;
    }

    public static LinearLayout.LayoutParams lp(Context c, int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    public static LinearLayout header(Context c) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF3949AB, INDIGO});
        float r = dp(c, 28);
        g.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        h.setBackground(g);
        h.setPadding(dp(c, 20), dp(c, 36), dp(c, 20), dp(c, 24));
        h.setElevation(dp(c, 6));
        return h;
    }

    public static TextView tv(Context c, String text, int size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(text); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    public static Button btn(Context c, String text, int color) {
        Button b = new Button(c);
        b.setText(text); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setTypeface(null, Typeface.BOLD); b.setTextSize(16);
        b.setBackground(gradient(c, color, 14)); b.setElevation(dp(c, 4));
        b.setLayoutParams(lp(c, -1, dp(c, 54), 0, 14, 0, 2));
        return b;
    }

    public static EditText et(Context c, String hint, int type) {
        EditText e = new EditText(c);
        e.setHint(hint); e.setInputType(type); e.setSingleLine(true);
        e.setTextColor(DARK); e.setHintTextColor(0xFF9CA3AF);
        GradientDrawable g = shape(c, 0xFFF3F4F6, 12);
        g.setStroke(dp(c, 1), 0xFFE5E7EB);
        e.setBackground(g);
        e.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        e.setLayoutParams(lp(c, -1, -2, 0, 10, 0, 0));
        return e;
    }

    public static EditText num(Context c, String hint) { return et(c, hint, InputType.TYPE_CLASS_NUMBER); }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(shape(c, Color.WHITE, 18));
        l.setElevation(dp(c, 3));
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        l.setLayoutParams(lp(c, -1, -2, 0, 6, 0, 6));
        return l;
    }

    /** Titre de section avec barre de couleur (séparation entre les parties). */
    public static LinearLayout sectionTitle(Context c, String text, int color) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        View bar = new View(c);
        bar.setBackground(shape(c, color, 3));
        bar.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 5), dp(c, 22)));
        TextView t = tv(c, text, 17, DARK, true);
        t.setPadding(dp(c, 10), 0, 0, 0);
        r.addView(bar); r.addView(t);
        r.setLayoutParams(lp(c, -1, -2, 2, 20, 0, 6));
        return r;
    }

    public static TextView pill(Context c, String text, int color) {
        TextView t = tv(c, text, 11, Color.WHITE, true);
        t.setBackground(shape(c, color, 10));
        t.setPadding(dp(c, 10), dp(c, 3), dp(c, 10), dp(c, 3));
        return t;
    }

    public static LinearLayout statBox(Context c, String label, String value, int color) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setBackground(shape(c, Color.WHITE, 16));
        b.setElevation(dp(c, 3));
        b.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        b.addView(tv(c, label, 12, GRAY, false));
        b.addView(tv(c, value, 20, color, true));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        b.setLayoutParams(p);
        return b;
    }

    public static LinearLayout tile(Context c, String icon, String label, String sub, int color, View.OnClickListener l) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.BOTTOM);
        t.setBackground(gradient(c, color, 22));
        t.setElevation(dp(c, 5));
        t.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        TextView ic = tv(c, icon, 26, Color.WHITE, false);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL); g.setColor(0x33FFFFFF);
        ic.setBackground(g); ic.setGravity(Gravity.CENTER);
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 52), dp(c, 52)));
        TextView lb = tv(c, label, 17, Color.WHITE, true);
        lb.setPadding(0, dp(c, 14), 0, 0);
        TextView sb = tv(c, sub, 12, 0xCCFFFFFF, false);
        t.addView(ic); t.addView(lb); t.addView(sb);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(c, 150), 1);
        p.setMargins(dp(c, 6), dp(c, 6), dp(c, 6), dp(c, 6));
        t.setLayoutParams(p);
        t.setOnClickListener(v -> { pop(v); v.postDelayed(() -> l.onClick(v), 150); });
        return t;
    }

    public static View progress(Context c, float ratio, int color, int track) {
        final FrameLayout f = new FrameLayout(c);
        f.setBackground(shape(c, track, 5));
        f.setLayoutParams(lp(c, -1, dp(c, 9), 0, 8, 0, 0));
        final View v = new View(c);
        v.setBackground(shape(c, color, 5));
        f.addView(v, new FrameLayout.LayoutParams(0, -1));
        final float r = Math.max(0f, Math.min(1f, ratio));
        f.post(() -> { v.getLayoutParams().width = (int) (f.getWidth() * r); v.requestLayout(); });
        return f;
    }

    public static void pop(View v) {
        AnimatorSet s = new AnimatorSet();
        s.playTogether(ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.92f, 1.05f, 1f),
                ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.92f, 1.05f, 1f));
        s.setDuration(420); s.start();
    }

    public static void flash(View v) {
        v.setAlpha(0f); v.setTranslationY(-40f);
        v.animate().alpha(1f).translationY(0f).setDuration(550).start();
    }

    public static String money(int v) {
        return String.format(java.util.Locale.FRANCE, "%,d", v).replace('\u202f', ' ').replace('\u00a0', ' ') + " F";
    }
}
