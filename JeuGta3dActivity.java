package bj.radio;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * GTA Kouamé 3D : ville ouverte en 3D (three.js) affichée dans une WebView à partir du fichier assets/gta3d.html.
 * Le score est l'argent gagné ; il rejoint le classement « GTA Kouamé » (clé « gta ») quand on quitte le jeu.
 * La librairie 3D se télécharge au premier lancement (internet requis), puis le cache du téléphone la garde.
 */
public class JeuGta3dActivity extends Activity {

    private static final String CLE = "gta";

    private WebView web;
    private volatile int meilleur = 0;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersif();
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings st = web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setAllowFileAccess(true);
        st.setCacheMode(WebSettings.LOAD_DEFAULT);
        st.setMediaPlaybackRequiresUserGesture(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return true;   // le jeu ne quitte jamais sa page
            }
        });
        web.addJavascriptInterface(new Pont(), "AndroidGta");
        setContentView(web);
        web.loadUrl("file:///android_asset/gta3d.html");
    }

    /** Ce que la page du jeu peut demander à l'appli. */
    private final class Pont {
        @JavascriptInterface
        public void record(int argent) {
            if (argent > meilleur) meilleur = argent;
        }

        @JavascriptInterface
        public int recordLocal() {
            return JeuxActivity.record(getApplicationContext(), CLE);
        }
    }

    private void sauver() {
        int v = meilleur;
        if (v > 0) JeuxActivity.enregistrer(getApplicationContext(), CLE, v);
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) immersif();
    }

    @SuppressWarnings("deprecation")
    private void immersif() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        sauver();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        sauver();
        if (web != null) {
            web.removeJavascriptInterface("AndroidGta");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
