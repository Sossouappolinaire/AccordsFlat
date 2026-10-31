package bj.radio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Écran d'accueil : trois boutons. */
public class AccordsHomeActivity extends Activity {

    private int dp(int v) { return AccordsUi.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(AccordsUi.BG);
        root.setPadding(dp(24), dp(48), dp(24), dp(24));

        // Logo
        FrameLayout logo = new FrameLayout(this);
        logo.setBackground(AccordsUi.gradient(AccordsUi.VIOLET, AccordsUi.MAGENTA, dp(24)));
        logo.setClipToOutline(true);
        ImageView logoImg = new ImageView(this);
        logoImg.setImageResource(R.drawable.accords_logo_fg);
        logoImg.setScaleX(1.7f);
        logoImg.setScaleY(1.7f);
        logo.addView(logoImg, new FrameLayout.LayoutParams(-1, -1));
        root.addView(logo, new LinearLayout.LayoutParams(dp(84), dp(84)));

        TextView title = new TextView(this);
        title.setText("Accords");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(AccordsUi.TEXT);
        title.setGravity(Gravity.CENTER);
        root.addView(title, AccordsUi.lp(this, -2, -2, 16));

        TextView sub = new TextView(this);
        sub.setText("Que veux-tu faire ?");
        sub.setTextSize(14);
        sub.setTextColor(AccordsUi.MUTED);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, AccordsUi.lp(this, -2, -2, 4));

        // Bouton 1 : fonction d'origine (analyse d'un fichier audio)
        Button analyse = AccordsUi.button(this, "🎼  Analyser un morceau\nAccords · notes · paroles", AccordsUi.VIOLET, AccordsUi.MAGENTA, Color.WHITE);
        analyse.setTextSize(17);
        analyse.setPadding(dp(20), dp(28), dp(20), dp(28));
        analyse.setOnClickListener(v -> startActivity(new Intent(this, AccordsAnalyseActivity.class)));
        root.addView(analyse, AccordsUi.lp(this, -1, -2, 40));

        // Bouton 2 : création de chansons à partir d'un texte
        Button creer = AccordsUi.button(this, "✍  Créer une chanson\nTexte → musique, voix et chœur", AccordsUi.TEAL, AccordsUi.BLUE, Color.WHITE);
        creer.setTextSize(17);
        creer.setPadding(dp(20), dp(28), dp(20), dp(28));
        creer.setOnClickListener(v -> startActivity(new Intent(this, SongActivity.class)));
        root.addView(creer, AccordsUi.lp(this, -1, -2, 18));

        // Bouton 3 : séparation des voix et des instruments (export WAV)
        Button separer = AccordsUi.button(this, "✂  Séparateur de voix\nVoix · piano · batterie · basse → WAV", AccordsUi.ORANGE, AccordsUi.MAGENTA, Color.WHITE);
        separer.setTextSize(17);
        separer.setPadding(dp(20), dp(28), dp(20), dp(28));
        separer.setOnClickListener(v -> startActivity(new Intent(this, StemActivity.class)));
        root.addView(separer, AccordsUi.lp(this, -1, -2, 18));

        setContentView(root);

        AccordsUi.enter(logo, 0);
        AccordsUi.enter(title, 80);
        AccordsUi.enter(analyse, 160);
        AccordsUi.enter(creer, 240);
        AccordsUi.enter(separer, 320);
    }
}
