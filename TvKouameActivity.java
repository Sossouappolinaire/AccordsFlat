package bj.radio;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TV Kouamé : une chaîne « en direct » faite des vidéos Facebook de la base (titre, canal, durée).
 * Chaque jour à 00h00 (heure du Bénin), le SERVEUR fabrique tout seul le programme du jour (section 8 de
 * supabase_setup.sql). Le programme est CONTINU, 24 h sur 24 : un film qui finit à 00h34 est suivi du film suivant
 * à 00h34 (le film commencé la veille a un début négatif). Aucun film répété tant qu'il en reste d'inédits.
 * En arrivant à 01h07, on tombe au milieu du programme en cours, à la bonne minute, et le suivant démarre tout seul.
 * TV Kouamé s'ouvre directement en plein écran (un appui sur l'image ou « Retour » revient à la page).
 * L'écran montre le logo « TV K » et, en bas, le bandeau d'annonces défilant.
 * L'heure vient du serveur (pas du téléphone).
 * PUBLICITÉ : après chaque film et chaque série, le serveur met un spot (section17_tv_publicite.sql) qui enchaîne la
 * bande d'annonce (10 s) de TOUS les films et séries programmés ce jour-là, en commençant par le prochain film.
 * Le message « TV Kouamé est votre télévision Facebook. En Dieu nous comptons. » est écrit à l'écran et dit à voix
 * haute. Les spots figurent dans la liste « Programme du jour » (ligne « 📣 Publicité TV Kouamé »).
 */
@OptIn(markerClass = UnstableApi.class)
public class TvKouameActivity extends Activity {

    private static final long JOUR_MS = 86_400_000L;

    private static final class Prog {
        String titre, lien, canal, serie;
        String cat = "";                      // film, serie ou chanson
        long dureeMs, debutMs, extraitMs;     // extraitMs : où commence la bande d'annonce dans la vidéo
        int episode;
        boolean pub;                          // spot publicitaire (extrait d'un film/série) et non un vrai programme
    }

    private static final String TEXTE_SECOURS_PUB = "📣 TV Kouamé est votre télévision Facebook\n\nEn Dieu nous comptons";
    private static final String PHRASE_PUB = "TV Kouamé est votre télévision Facebook. En Dieu nous comptons.";

    private final List<Prog> progs = new ArrayList<>();
    private long totalMs = 0;
    private long refServeurMs = 0, refAppareil = 0;     // heure du serveur (ms depuis minuit) à l'instant refAppareil
    private long derniereSync = 0, dernierMaintenant = -1;
    private String jourServeur = "", dernierBandeau = "";

    private ExoPlayer player;
    private PlayerView vue;
    private TextView voile, etat, titreV, heure, suivant, programmeTxt, titreProgramme, logo, bandeau, pubVue;
    private TextToSpeech tts;
    private boolean ttsPret = false;
    private long pubDit = Long.MIN_VALUE;   // début du spot déjà annoncé à voix haute
    private long pubPrefetch = Long.MIN_VALUE;  // début du spot dont on a préparé les bandes d'annonce
    private Prog lu = null;                  // vidéo réellement lue : le programme, ou la bande d'annonce en cours pendant un spot
    private int bande = -1;                  // numéro (absolu) de la bande d'annonce en cours dans le spot ; elles tournent si le spot est long
    // Publicité regardée à la demande (touche sur une ligne « Publicité » du programme)
    private static final long BANDE_MANUELLE_MS = 10_000L;
    private int pubManuelle = -1;            // index du spot regardé à la demande (-1 : aucun)
    private List<Prog> pubManuelleBandes = new ArrayList<>();
    private int pubManuelleNum = 0;          // bande en cours dans la publicité à la demande
    private long pubManuelleLecture = 0;     // instant où la bande en cours a vraiment commencé (0 : pas encore)
    private ProgressBar barre;
    private Button revoir, plein;
    private FrameLayout zone;
    private ScrollView infos;
    private LinearLayout page, entete;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tickRun = this::tick;
    private boolean actif = false, differe = false, pleinEcran = false, syncEnCours = false;
    private long debutCourant = -1;
    private int courant = -1, generation = 0, essaisProg = 0;
    private FbLien.Direct directCourant = null;   // façons de lire le programme en cours
    private int variante = 0;                      // laquelle on essaie (0 = la plus simple)
    private String prefetch = "";

    // ------------------------------------------------------------------ cycle de vie

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.fenetre(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackground(Ui.fond());

        int p = Ui.dp(this, 14);
        entete = new LinearLayout(this);
        entete.setPadding(p, p, p, Ui.dp(this, 6));
        entete.addView(Ui.texte(this, "📺 TV Kouamé", 22, Ui.TXT, true));
        page.addView(entete);

        vue = new PlayerView(this);
        vue.setUseController(false);
        vue.setBackgroundColor(Color.BLACK);
        zone = new FrameLayout(this);
        zone.setBackgroundColor(Color.BLACK);
        zone.addView(vue, new FrameLayout.LayoutParams(-1, -1));
        voile = Ui.texte(this, "Chargement du programme…", 15, Ui.TXT, true);
        voile.setGravity(Gravity.CENTER);
        voile.setBackgroundColor(0x99000000);
        voile.setPadding(p, p, p, p);
        zone.addView(voile, new FrameLayout.LayoutParams(-1, -1));

        // Logo de la chaîne (coin haut-droit, toujours visible)
        logo = Ui.texte(this, "TV K", 15, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ui.rond(0xE6E91E63, 8, this));
        logo.setPadding(Ui.dp(this, 10), Ui.dp(this, 3), Ui.dp(this, 10), Ui.dp(this, 3));
        FrameLayout.LayoutParams lpLogo = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lpLogo.setMargins(0, Ui.dp(this, 8), Ui.dp(this, 8), 0);
        zone.addView(logo, lpLogo);

        // Bandeau d'annonces défilant (bas de l'écran)
        bandeau = Ui.texte(this, "", 13, Color.WHITE, true);
        bandeau.setSingleLine(true);
        bandeau.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        bandeau.setMarqueeRepeatLimit(-1);
        bandeau.setHorizontallyScrolling(true);
        bandeau.setSelected(true);
        bandeau.setBackgroundColor(0xCC8E0F3A);
        bandeau.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        zone.addView(bandeau, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        // Message du spot publicitaire (affiché seulement pendant les spots, au-dessus du bandeau)
        pubVue = Ui.texte(this, "📣 TV Kouamé est votre télévision Facebook\nEn Dieu nous comptons", 17, Color.WHITE, true);
        pubVue.setGravity(Gravity.CENTER);
        pubVue.setBackground(Ui.rond(0xCC8E0F3A, 14, this));
        pubVue.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        pubVue.setVisibility(View.GONE);
        FrameLayout.LayoutParams lpPub = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        lpPub.setMargins(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 40));
        zone.addView(pubVue, lpPub);
        page.addView(zone, new LinearLayout.LayoutParams(-1, Ui.dp(this, 230)));

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(p, p, p, p);

        etat = Ui.texte(this, "", 14, Ui.JAUNE, true);
        titreV = Ui.texte(this, "", 18, Ui.TXT, true);
        heure = Ui.texte(this, "", 13, Ui.TXT2, false);
        barre = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barre.setMax(1000);
        suivant = Ui.texte(this, "", 14, Ui.TXT, false);
        Ui.ajouter(c, etat, 0);
        Ui.ajouter(c, titreV, 4);
        Ui.ajouter(c, heure, 4);
        Ui.ajouter(c, barre, 8);
        Ui.ajouter(c, suivant, 8);

        revoir = Ui.bouton(this, "⏮ Revoir depuis le début", Ui.VIOLET);
        revoir.setOnClickListener(v -> basculerDifferePlusDirect());
        Ui.ajouter(c, revoir, 12);
        plein = Ui.boutonContour(this, "⛶ Plein écran", Ui.TXT);
        plein.setOnClickListener(v -> basculerPlein());
        Ui.ajouter(c, plein, 8);

        titreProgramme = Ui.texte(this, "Programme du jour", 18, Ui.TXT, true);
        Ui.ajouter(c, titreProgramme, 20);
        programmeTxt = Ui.texte(this, "", 14, Ui.TXT2, false);
        Ui.ajouter(c, programmeTxt, 8);
        programmeTxt.setMovementMethod(LinkMovementMethod.getInstance());   // les lignes « Publicité » sont cliquables
        programmeTxt.setHighlightColor(0x33FFC857);

        infos = new ScrollView(this);
        infos.addView(c);
        page.addView(infos, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);

        tts = new TextToSpeech(this, status -> {          // voix du spot : « TV Kouamé est votre télévision Facebook… »
            if (status != TextToSpeech.SUCCESS || tts == null) return;
            int r = tts.setLanguage(Locale.FRENCH);
            ttsPret = !(r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED);
            if (ttsPret) tts.setSpeechRate(0.95f);
        });

        zone.setOnClickListener(v -> basculerPlein());   // un appui sur l'image : plein écran / retour à la page
        basculerPlein();                                  // TV Kouamé s'ouvre directement en plein écran
    }

    @Override
    protected void onResume() {
        super.onResume();
        actif = true;
        if (player == null) creerLecteur();
        pubManuelle = -1;
        differe = false;
        debutCourant = -1;                 // on revient au direct, à la bonne minute
        synchroniser(true);
    }

    @Override
    protected void onPause() {
        actif = false;
        pubManuelle = -1;
        h.removeCallbacks(tickRun);
        generation++;
        if (tts != null) tts.stop();
        if (player != null) player.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (pleinEcran) basculerPlein();
        else super.onBackPressed();
    }

    private void creerLecteur() {
        player = new ExoPlayer.Builder(this).build();
        vue.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException e) {
                if (pubManuelle >= 0) {         // bande d'annonce illisible pendant une publicité à la demande : on passe à la suivante
                    pubManuelleNum++;
                    jouerBandeManuelle();
                    return;
                }
                if (courant < 0 || courant >= progs.size()) return;
                if (directCourant != null && variante + 1 < directCourant.variantes.size()) {   // autre façon de lire la même vidéo
                    variante++;
                    demarrer(directCourant, generation);
                    return;
                }
                if (progs.get(courant).pub) {   // un spot qui ne se lit pas : on garde le message, sans afficher d'erreur
                    voile(TEXTE_SECOURS_PUB);
                    return;
                }
                if (essaisProg == 0) {          // le lien Facebook a peut-être expiré : on le cherche à nouveau
                    FbLien.oublier(lu != null ? lu.lien : progs.get(courant).lien);
                    relancer(true);
                } else {
                    String detail = e.getErrorCodeName();
                    Throwable cause = e.getCause();
                    if (cause instanceof HttpDataSource.InvalidResponseCodeException) {
                        detail += ", http " + ((HttpDataSource.InvalidResponseCodeException) cause).responseCode;
                    }
                    voile("⚠ Ce programme ne peut pas être lu (" + detail + "). Le suivant démarre à l'heure prévue.");
                }
            }

            @Override
            public void onRenderedFirstFrame() {
                voile(null);                    // l'image est là : on retire le message
            }

            @Override
            public void onPlaybackStateChanged(int etat) {
                if (etat == Player.STATE_BUFFERING) voile("⏳ Chargement de la vidéo…");
                else if (etat == Player.STATE_READY) voile(null);
            }
        });
    }

    // ------------------------------------------------------------------ planning et heure

    /** Récupère le programme du jour (fabriqué par le serveur à 00h00) et l'heure du serveur. */
    private void synchroniser(final boolean premiere) {
        if (syncEnCours) return;
        syncEnCours = true;
        if (premiere) voile("Chargement du programme…");
        new Thread(() -> {
            final JSONObject j = Supabase.tvPlanning();
            final long t0 = SystemClock.elapsedRealtime();
            runOnUiThread(() -> {
                syncEnCours = false;
                if (!actif) return;
                if (j == null) {
                    if (progs.isEmpty()) voile("⚠ Programme indisponible. Vérifiez votre connexion internet. "
                            + "(L'administrateur doit avoir exécuté la section 8 de supabase_setup.sql.)");
                    else h.postDelayed(tickRun, 1000);
                    return;
                }
                List<Prog> nouv = new ArrayList<>();
                long total = 0;
                JSONArray a = j.optJSONArray("programmes");
                for (int i = 0; a != null && i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o == null || o.optLong("duree_ms", 0) <= 0) continue;
                    Prog g = new Prog();
                    g.titre = texte(o, "titre");
                    g.lien = texte(o, "lien");
                    g.canal = texte(o, "canal");
                    g.serie = texte(o, "serie");
                    g.dureeMs = o.optLong("duree_ms", 0);
                    g.debutMs = o.has("debut_ms") ? o.optLong("debut_ms", total) : total;
                    g.episode = o.optInt("episode", 0);
                    g.pub = o.optBoolean("pub", false);
                    g.extraitMs = o.optLong("extrait_ms", 0);
                    g.cat = texte(o, "categorie");
                    if (g.lien.isEmpty()) continue;
                    nouv.add(g);
                    total = g.debutMs + g.dureeMs;
                }
                progs.clear();
                progs.addAll(nouv);
                totalMs = total;
                jourServeur = j.optString("jour", "");
                refServeurMs = j.optLong("maintenant_ms", 0);
                refAppareil = t0;
                derniereSync = t0;
                dernierMaintenant = -1;
                tick();
            });
        }).start();
    }

    private static String texte(JSONObject o, String cle) {
        String s = o.optString(cle, "");
        return "null".equals(s) ? "" : s;
    }

    /** Heure du serveur, en ms depuis minuit (heure du Bénin). */
    private long maintenant() {
        return ((refServeurMs + (SystemClock.elapsedRealtime() - refAppareil)) % JOUR_MS + JOUR_MS) % JOUR_MS;
    }

    /** Programme en cours : {index, avancée dans ce programme (ms), heure de son début (ms depuis minuit)} ; null si rien ne passe. */
    private long[] position(long now) {
        for (int i = 0; i < progs.size(); i++) {
            Prog g = progs.get(i);
            if (now >= g.debutMs && now < g.debutMs + g.dureeMs) return new long[]{i, now - g.debutMs, g.debutMs};
        }
        return null;
    }

    // ------------------------------------------------------------------ boucle d'une seconde

    private void tick() {
        h.removeCallbacks(tickRun);
        if (!actif) return;
        long now = maintenant();

        // minuit est passé : le serveur a fabriqué le programme du nouveau jour, on le récupère
        if (dernierMaintenant >= 0 && now < dernierMaintenant - 60_000L) {
            dernierMaintenant = -1;
            synchroniser(false);
            return;
        }
        dernierMaintenant = now;

        majTitreProgramme();
        if (progs.isEmpty() || totalMs <= 0) {
            ecranAttente("📭 Pas encore de programme. Il se fabrique tout seul chaque jour à 00h00 "
                    + "avec les films de « Films partagés ».");
            majBandeau("📺 TV K · Nouveau programme chaque jour à 00h00 · Ajoutez vos films dans « Films partagés »");
            h.postDelayed(() -> synchroniser(false), 15000);
            return;
        }

        long[] p = position(now);
        if (p == null) {                                     // ne devrait pas arriver : le programme couvre toute la journée
            ecranAttente("📺 TV K\n\nLe programme se met à jour…");
            etat.setText("⏳ Mise à jour du programme");
            titreV.setText("TV K");
            heure.setText("");
            barre.setProgress(0);
            suivant.setText("");
            majBandeau("📺 TV K · Programme continu, 24 h sur 24 · Nouveau programme chaque jour à 00h00 (heure du Bénin)");
            listeProgramme(-1);
            h.postDelayed(() -> synchroniser(false), 5000);
            return;
        }

        int idx = (int) p[0];
        long off = p[1], debut = p[2];
        courant = idx;

        if (pubManuelle >= 0) {                             // publicité regardée à la demande : le direct reprend à la fin
            if (pubManuelle >= progs.size()) { finPubManuelle(); return; }
            majAffichagePubManuelle();
            h.postDelayed(tickRun, 1000);
            return;
        }

        boolean enLecture = player != null && (player.getPlaybackState() == Player.STATE_READY
                || player.getPlaybackState() == Player.STATE_BUFFERING);
        boolean memeProgramme = debutCourant != -1 && jourMod(debut) == jourMod(debutCourant);
        if (memeProgramme) {
            debutCourant = debut;                           // à minuit, même film : seul son début est recalculé
            if (progs.get(idx).pub && !differe && bande != bandeVoulue(idx, off)) lancer(idx, debut, false);   // bande d'annonce suivante
        } else if (!(differe && enLecture)) lancer(idx, debut, false);

        majAffichage(idx, off, debut, now);

        if (SystemClock.elapsedRealtime() - derniereSync > 10 * 60_000L) synchroniser(false);
        h.postDelayed(tickRun, 1000);
    }

    /** Écran « TV K » sans lecture (pas de programme, ou fin de journée). */
    private void ecranAttente(String t) {
        if (player != null && player.getPlaybackState() != Player.STATE_IDLE) {
            player.stop();
            player.clearMediaItems();
        }
        debutCourant = -1;
        voile(t);
    }

    private void lancer(int idx, long debut, boolean reessai) {
        courant = idx;
        debutCourant = debut;
        differe = false;
        variante = 0;
        directCourant = null;
        essaisProg = reessai ? 1 : 0;
        final int gen = ++generation;
        final Prog g = progs.get(idx);
        final Prog lue;                                      // vidéo à lire
        bande = -1;
        if (g.pub) {                                         // spot : la bande d'annonce du moment
            List<Prog> bs = bandesDe(idx);
            int b = bandeVoulue(idx, Math.max(0, maintenant() - debut));
            if (b < 0 || bs.isEmpty()) {                     // plus aucun film à annoncer : message seul (on ne lit surtout pas le film d'avant)
                lu = g;
                listeProgramme(idx);
                if (player != null) {
                    player.stop();
                    player.clearMediaItems();
                }
                voile(TEXTE_SECOURS_PUB);
                return;
            }
            bande = b;
            lue = bs.get(b % bs.size());
        } else {
            lue = g;
        }
        lu = lue;
        listeProgramme(idx);
        voile(g.pub ? TEXTE_SECOURS_PUB : "📺 TV K · Chargement : " + titreDe(g) + "…");
        FbLien.Direct d = FbLien.deja(lue.lien);
        if (d != null) {
            demarrer(d, gen);
            return;
        }
        new Thread(() -> {
            final FbLien.Direct r = FbLien.resoudre(lue.lien);
            runOnUiThread(() -> {
                if (gen != generation || !actif) return;
                if (r == null) voile(g.pub ? TEXTE_SECOURS_PUB : "⚠ Programme indisponible pour l'instant. Le suivant démarre à l'heure prévue.");
                else demarrer(r, gen);
            });
        }).start();
    }

    private void relancer(boolean reessai) {
        if (courant < 0 || courant >= progs.size()) return;
        lancer(courant, debutCourant, reessai);
    }

    /** Lance la lecture à la minute exacte du direct (le temps de chargement est rattrapé). */
    private void demarrer(FbLien.Direct d, int gen) {
        if (gen != generation || !actif || player == null || courant < 0 || courant >= progs.size()) return;
        Prog g = progs.get(courant);
        Prog lue = lu != null ? lu : g;
        long off = Math.max(0, maintenant() - debutCourant);
        long depart;                                         // où commencer dans la vidéo lue
        if (g.pub && lue != g) {                             // bande d'annonce : elle commence à extraitMs dans le film
            long teff = bandeDuree(courant);
            long dansBande = off % teff;                     // les bandes tournent : on est toujours dans l'une d'elles
            if (dansBande >= teff - 1500) {                  // cette bande est finie : la suivante prend le relais au prochain tick
                voile(TEXTE_SECOURS_PUB);
                return;
            }
            depart = lue.extraitMs + (dansBande < 3000 ? 0 : dansBande);
        } else {
            if (off >= g.dureeMs - 2000) {
                voile("⏳ Fin du programme…");
                return;
            }
            depart = off < 3000 ? 0 : off;
        }
        Map<String, String> entetes = new HashMap<>();
        entetes.put("Referer", "https://www.facebook.com/");
        DefaultHttpDataSource.Factory f = new DefaultHttpDataSource.Factory()
                .setUserAgent(d.ua)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setDefaultRequestProperties(entetes);
        directCourant = d;
        String[] v = d.variantes.get(Math.min(variante, d.variantes.size() - 1));
        ProgressiveMediaSource.Factory usine = new ProgressiveMediaSource.Factory(f);
        MediaSource src = usine.createMediaSource(MediaItem.fromUri(v[0]));
        if (v[1] != null) src = new MergingMediaSource(src, usine.createMediaSource(MediaItem.fromUri(v[1])));   // image et son séparés
        player.setVolume(g.pub && off < 7000 ? 0.35f : 1f);   // au début d'un spot, la voix « TV Kouamé… » passe par-dessus
        player.setMediaSource(src, depart);
        player.prepare();
        player.play();
        voile(g.pub ? TEXTE_SECOURS_PUB : "⏳ Chargement de la vidéo…");
        final int g2 = gen;
        vue.postDelayed(() -> {             // si rien ne démarre en 45 s : autre façon de lire, nouvelle tentative, puis message clair
            if (g2 != generation || !actif || player == null || courant < 0 || courant >= progs.size()) return;
            if (player.getPlaybackState() == Player.STATE_READY) return;
            if (directCourant != null && variante + 1 < directCourant.variantes.size()) {
                variante++;
                demarrer(directCourant, g2);
                return;
            }
            if (progs.get(courant).pub) {
                voile(TEXTE_SECOURS_PUB);
            } else if (essaisProg == 0) {
                FbLien.oublier(lu != null ? lu.lien : progs.get(courant).lien);
                relancer(true);
            } else {
                voile("⚠ La vidéo ne démarre pas. Vérifiez votre connexion internet.");
            }
        }, 45000);
    }

    // ------------------------------------------------------------------ affichage

    private void voile(String t) {
        if (t == null) {
            voile.setVisibility(View.GONE);
        } else {
            voile.setText(t);
            voile.setVisibility(View.VISIBLE);
        }
    }

    private String titreDe(Prog g) {
        if (g.pub) return "📣 Publicité TV Kouamé";
        String t = g.titre.isEmpty() ? "Vidéo" : LienPlateforme.titreCourt(g.titre, 5);
        if (!g.serie.isEmpty() || g.episode > 0) {
            t = (g.serie.isEmpty() ? "" : g.serie + " · ") + (g.episode > 0 ? "Épisode " + g.episode + " · " : "") + t;
        }
        return t;
    }

    /** Heure du jour (0 à 24 h) d'un instant exprimé en ms depuis minuit, même négatif (film commencé la veille). */
    private static long jourMod(long ms) {
        return ((ms % JOUR_MS) + JOUR_MS) % JOUR_MS;
    }

    private static String hm(long ms) {
        long m = (((ms % JOUR_MS) + JOUR_MS) % JOUR_MS) / 60000;
        return String.format(Locale.FRANCE, "%02dh%02d", m / 60, m % 60);
    }

    /** « Programme du jour · mercredi 28 octobre » */
    private void majTitreProgramme() {
        String t = "Programme du jour";
        try {
            java.util.Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).parse(jourServeur);
            if (d != null) t += " · " + new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(d);
        } catch (Exception ignored) {}
        if (!t.contentEquals(titreProgramme.getText())) titreProgramme.setText(t);
    }

    /** Prochain vrai programme après l'index donné (les spots publicitaires sont ignorés) ; null s'il n'y en a plus. */
    private Prog prochainReel(int apres) {
        for (int i = apres + 1; i < progs.size(); i++) if (!progs.get(i).pub) return progs.get(i);
        return null;
    }

    /**
     * Bandes d'annonce d'un spot : les films et séries qui passent APRÈS ce spot et qui n'ont pas encore commencé.
     * Un film ou une série déjà joué (ou en cours) est retiré de la liste, y compris pour les spots plus anciens.
     */
    private List<Prog> bandesDe(int idxPub) {
        List<Prog> res = new ArrayList<>();
        long now = maintenant();
        for (int i = idxPub + 1; i < progs.size(); i++) {
            Prog p = progs.get(i);
            if (!p.pub && ("film".equals(p.cat) || "serie".equals(p.cat)) && p.debutMs > now) res.add(p);
        }
        return res;
    }

    /** Durée de chaque bande d'annonce d'un spot : durée du spot divisée par le nombre de films restants, entre 10 et 30 s. */
    private long bandeDuree(int idxPub) {
        int m = Math.max(1, bandesDe(idxPub).size());
        long d = progs.get(idxPub).dureeMs / m;
        return Math.max(1000, Math.min(d, 30_000L));
    }

    /** Numéro (absolu) de la bande d'annonce à montrer maintenant dans le spot (-1 s'il ne reste aucun film à annoncer). */
    private int bandeVoulue(int idxPub, long off) {
        if (bandesDe(idxPub).isEmpty()) return -1;
        return (int) (Math.max(0, off) / bandeDuree(idxPub));   // numéro absolu : après la dernière bande, on repart de la première
    }

    /** Spot publicitaire : message à l'écran + voix (une seule fois par spot, seulement si on le prend au début). */
    private void majPub(int idx, Prog g, long off, long debut) {
        boolean pubEnCours = g.pub && !differe;
        pubVue.setVisibility(pubEnCours ? View.VISIBLE : View.GONE);
        if (player != null) player.setVolume(g.pub && off < 7000 ? 0.35f : 1f);
        if (!pubEnCours) return;
        if (pubPrefetch != debut) {                          // on prépare à l'avance les liens de toutes les bandes d'annonce
            pubPrefetch = debut;
            final List<Prog> bs = bandesDe(idx);
            new Thread(() -> {
                for (Prog x : bs) {
                    if (!actif) return;
                    if (FbLien.deja(x.lien) == null) FbLien.resoudre(x.lien);
                }
            }).start();
        }
        if (pubDit != debut && off < 8000) {
            pubDit = debut;
            if (tts != null && ttsPret) tts.speak(PHRASE_PUB, TextToSpeech.QUEUE_FLUSH, null, "pub");
        }
    }

    /** Le texte du bandeau n'est changé que s'il est différent (sinon le défilement repartirait à zéro). */
    private void majBandeau(String t) {
        if (t.equals(dernierBandeau)) return;
        dernierBandeau = t;
        bandeau.setText(t);
        bandeau.setSelected(true);
    }

    private void majAffichage(int idx, long off, long debut, long now) {
        Prog g = progs.get(idx);
        long fin = debut + g.dureeMs;
        Prog nx = idx + 1 < progs.size() ? progs.get(idx + 1) : null;   // ligne suivante (peut être un spot)
        Prog n = prochainReel(idx);                                     // prochain vrai programme

        etat.setText(g.pub ? "📣 PUBLICITÉ · TV Kouamé"
                : differe ? "⏪ Vous regardez en différé" : "🔴 EN DIRECT · " + (g.canal.isEmpty() ? "Kouamé TV" : g.canal));
        titreV.setText(g.pub && lu != null && lu != g ? "🎬 Bande d'annonce : " + titreDe(lu) : titreDe(g));
        heure.setText(hm(debut) + " → " + hm(fin) + " · " + Supabase.duree(g.dureeMs)
                + " · reste " + Supabase.duree(Math.max(0, fin - now)));
        long avance = off;
        if (differe && player != null) avance = Math.max(0, player.getCurrentPosition());
        barre.setProgress((int) Math.min(1000, avance * 1000 / g.dureeMs));
        suivant.setText(n != null ? "À suivre à " + hm(n.debutMs) + " : " + titreDe(n)
                : "Ce programme se termine à " + hm(fin) + " : le nouveau programme du jour enchaîne ensuite.");
        revoir.setText(differe ? "🔴 Revenir en direct" : "⏮ Revoir depuis le début");

        // bandeau d'annonces
        StringBuilder b = new StringBuilder("📺 TV K · EN CE MOMENT : ").append(titreDe(g));
        if (n != null) b.append("   •   À SUIVRE ").append(hm(n.debutMs)).append(" : ").append(titreDe(n));
        Prog n2 = n == null ? null : prochainReel(progs.indexOf(n));
        if (n2 != null) b.append("   •   ENSUITE ").append(hm(n2.debutMs)).append(" : ").append(titreDe(n2));
        b.append("   •   Nouveau programme chaque jour à 00h00 (heure du Bénin)   ");
        majBandeau(b.toString());

        if (!differe && player != null && player.getPlaybackState() == Player.STATE_ENDED) {
            voile(g.pub ? TEXTE_SECOURS_PUB : "⏳ " + (n != null ? "Prochain programme à " + hm(n.debutMs) + " : " + titreDe(n)
                    : "Prochain programme à " + hm(fin)));
        }
        majPub(idx, g, off, debut);
        // on prépare le lien de la ligne suivante (programme ou spot) avant la fin
        Prog cible = nx;
        if (nx != null && nx.pub) {
            List<Prog> bs = bandesDe(idx + 1);
            if (!bs.isEmpty()) cible = bs.get(0);
        }
        if (cible != null && fin - now < 90_000 && FbLien.deja(cible.lien) == null && !cible.lien.equals(prefetch)) {
            prefetch = cible.lien;
            final String lien = cible.lien;
            new Thread(() -> FbLien.resoudre(lien)).start();
        }
    }

    /** Programme complet de la journée : ✓ déjà passé, ▶ en cours, • à venir. Les lignes « Publicité » se touchent pour les lire. */
    private void listeProgramme(int idxCourant) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < progs.size(); i++) {
            Prog g = progs.get(i);
            String marque = idxCourant < 0 ? "•" : (i < idxCourant ? "✓" : (i == idxCourant ? "▶" : "•"));
            if (g.pub) {                                      // spot publicitaire : visible dans la liste, avec ses bandes d'annonce
                final int idxPub = i;
                List<Prog> bs = bandesDe(i);
                int deb = sb.length();
                sb.append(marque).append(' ').append(hm(g.debutMs)).append("  📣 Publicité TV Kouamé");
                if (!bs.isEmpty()) sb.append(" · ").append(String.valueOf(bs.size())).append(bs.size() > 1 ? " bandes d'annonce, dès « " : " bande d'annonce : « ")
                        .append(titreDe(bs.get(0))).append(" »");
                sb.append(" (").append(Supabase.duree(g.dureeMs)).append(")");
                if (!bs.isEmpty()) sb.append("  ▶ Lire");
                sb.setSpan(new ClickableSpan() {
                    @Override public void onClick(View v) { lirePubALaDemande(idxPub); }
                    @Override public void updateDrawState(TextPaint ds) {
                        ds.setColor(Ui.JAUNE);
                        ds.setUnderlineText(false);
                    }
                }, deb, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append('\n');
                continue;
            }
            sb.append(marque).append(' ').append(g.debutMs < 0 ? "00h00" : hm(g.debutMs)).append("  ")
                    .append(titreDe(g)).append(g.debutMs < 0 ? " (suite)" : "")
                    .append(" (").append(Supabase.duree(g.dureeMs)).append(")\n");
        }
        programmeTxt.setText(sb.length() == 0 ? (CharSequence) "—" : sb);
    }

    // ------------------------------------------------------------------ publicité à la demande

    /**
     * Touche sur une ligne « Publicité » du programme : on lit ses bandes d'annonce (10 s chacune), une après l'autre.
     * Seuls les films et séries qui n'ont pas encore commencé y figurent. À la fin, le direct reprend tout seul.
     */
    private void lirePubALaDemande(int idxPub) {
        if (player == null || !actif || idxPub < 0 || idxPub >= progs.size() || !progs.get(idxPub).pub) return;
        List<Prog> bs = bandesDe(idxPub);
        if (bs.isEmpty()) {
            Toast.makeText(this, "Plus de bande d'annonce : tous les films de cette publicité sont déjà passés.", Toast.LENGTH_LONG).show();
            return;
        }
        pubManuelle = idxPub;
        pubManuelleBandes = bs;
        pubManuelleNum = 0;
        differe = true;                                       // on quitte le direct ; le bouton propose « Revenir en direct »
        if (tts != null && ttsPret) tts.speak(PHRASE_PUB, TextToSpeech.QUEUE_FLUSH, null, "pub");
        majAffichagePubManuelle();
        jouerBandeManuelle();
    }

    private void jouerBandeManuelle() {
        if (pubManuelle < 0) return;
        if (pubManuelleNum >= pubManuelleBandes.size()) {     // toutes les bandes ont passé : retour au direct
            finPubManuelle();
            return;
        }
        final Prog x = pubManuelleBandes.get(pubManuelleNum);
        final int gen = ++generation;
        lu = x;
        pubManuelleLecture = 0;
        majAffichagePubManuelle();
        voile(TEXTE_SECOURS_PUB);
        FbLien.Direct d = FbLien.deja(x.lien);
        if (d != null) {
            demarrerBandeManuelle(d, x, gen);
            return;
        }
        new Thread(() -> {
            final FbLien.Direct r = FbLien.resoudre(x.lien);
            runOnUiThread(() -> {
                if (gen != generation || !actif || pubManuelle < 0) return;
                if (r == null) {                              // lien indisponible : bande suivante
                    pubManuelleNum++;
                    jouerBandeManuelle();
                } else {
                    demarrerBandeManuelle(r, x, gen);
                }
            });
        }).start();
    }

    private void demarrerBandeManuelle(FbLien.Direct d, Prog x, int gen) {
        if (gen != generation || !actif || player == null || pubManuelle < 0) return;
        Map<String, String> entetes = new HashMap<>();
        entetes.put("Referer", "https://www.facebook.com/");
        DefaultHttpDataSource.Factory f = new DefaultHttpDataSource.Factory()
                .setUserAgent(d.ua)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setDefaultRequestProperties(entetes);
        String[] v = d.variantes.get(0);
        ProgressiveMediaSource.Factory usine = new ProgressiveMediaSource.Factory(f);
        MediaSource src = usine.createMediaSource(MediaItem.fromUri(v[0]));
        if (v[1] != null) src = new MergingMediaSource(src, usine.createMediaSource(MediaItem.fromUri(v[1])));
        player.setVolume(pubManuelleNum == 0 ? 0.35f : 1f);   // au début, la voix « TV Kouamé… » passe par-dessus
        player.setMediaSource(src, x.extraitMs);
        player.prepare();
        player.play();
        suivreBandeManuelle(gen, SystemClock.elapsedRealtime());
    }

    /** Passe à la bande suivante après 10 s de lecture réelle (ou si la bande ne démarre pas au bout de 25 s). */
    private void suivreBandeManuelle(final int gen, final long t0) {
        h.postDelayed(() -> {
            if (gen != generation || !actif || pubManuelle < 0) return;
            long t = SystemClock.elapsedRealtime();
            if (pubManuelleLecture == 0 && player != null && player.getPlaybackState() == Player.STATE_READY) pubManuelleLecture = t;
            boolean finie = (pubManuelleLecture > 0 && t - pubManuelleLecture >= BANDE_MANUELLE_MS) || t - t0 >= 25_000L;
            if (finie) {
                pubManuelleNum++;
                jouerBandeManuelle();
            } else {
                suivreBandeManuelle(gen, t0);
            }
        }, 500);
    }

    /** Fin (ou arrêt) de la publicité à la demande : le direct reprend à la bonne minute. */
    private void finPubManuelle() {
        pubManuelle = -1;
        pubManuelleBandes = new ArrayList<>();
        pubManuelleNum = 0;
        generation++;
        differe = false;
        debutCourant = -1;                                    // le prochain tick relance le direct
        pubVue.setVisibility(View.GONE);
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        tick();
    }

    private void majAffichagePubManuelle() {
        int n = pubManuelleBandes.size();
        Prog x = pubManuelleNum < n ? pubManuelleBandes.get(pubManuelleNum) : null;
        etat.setText("📣 PUBLICITÉ · à la demande");
        titreV.setText(x != null ? "🎬 Bande d'annonce : " + titreDe(x) : "📣 Publicité TV Kouamé");
        heure.setText("Bande " + Math.min(pubManuelleNum + 1, Math.max(1, n)) + " sur " + n);
        barre.setProgress(n == 0 ? 0 : (int) (1000L * pubManuelleNum / n));
        suivant.setText("Le direct reprend à la fin de la publicité.");
        revoir.setText("🔴 Revenir en direct");
        pubVue.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------ boutons

    private void basculerDifferePlusDirect() {
        if (pubManuelle >= 0) {                           // publicité à la demande en cours : on revient au direct
            finPubManuelle();
            return;
        }
        if (player == null || courant < 0) return;
        if (progs.get(courant).pub) return;               // pas de « revoir depuis le début » pendant un spot publicitaire
        if (differe) {
            differe = false;
            debutCourant = -1;              // le prochain tick relance le direct
            tick();
        } else {
            differe = true;
            if (player.getPlaybackState() == Player.STATE_READY) voile(null);
            player.seekTo(0);
            player.play();
        }
    }

    private void basculerPlein() {
        pleinEcran = !pleinEcran;
        entete.setVisibility(pleinEcran ? View.GONE : View.VISIBLE);
        infos.setVisibility(pleinEcran ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) zone.getLayoutParams();
        lp.height = pleinEcran ? -1 : Ui.dp(this, 230);
        zone.setLayoutParams(lp);
        // l'image REMPLIT tout l'écran (recadrée si besoin) ; fond et barres noirs : plus de bandes blanches
        vue.setResizeMode(pleinEcran ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                : androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                pleinEcran ? Color.BLACK : Ui.BG));
        Ui.modePleinEcran(this, pleinEcran);
        plein.setText(pleinEcran ? "⛶ Quitter le plein écran" : "⛶ Plein écran");
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        // les barres reviennent parfois (notification, volume) : on les recache
        if (focus && pleinEcran) Ui.rappelerPleinEcran(this, true);
    }
}
