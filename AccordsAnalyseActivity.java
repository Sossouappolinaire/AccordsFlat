package bj.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public class AccordsAnalyseActivity extends Activity {

    private static final int PICK = 1, SAVE_PDF = 2;

    private Uri audioUri;
    private String audioName = "audio.mp3";
    private AudioDecoder.Result decoded;

    private ChordAnalyzer.Analysis analysis;
    private ChordAnalyzer.Grid grid;
    private List<Lyrics.Word> lyricWords;
    private List<Lyrics.Line> lyricLines;
    private String instrument = "piano";
    private int gridBase;
    private int transpose = 0;
    private boolean chordsShown = false;
    private BackgroundColorSpan curSpan;
    private ForegroundColorSpan curFg;
    private String lastChord = "";

    // progression des tâches longues
    private volatile boolean busy = false;
    private volatile int stageBase = 0, stageWeight = 100;
    private volatile boolean creeping = false;

    private MediaPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        public void run() {
            tick();
            if (player != null && player.isPlaying()) handler.postDelayed(this, 100);
        }
    };

    private TextView fileLabel, modelStatus, output, currentChord, lyricNow, transposeLabel, progressText;
    private SeekBar seek;
    private Button bPlay;
    private ProgressBar bar;
    private Button[] actions;

    interface Task { String run() throws Exception; }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void loadPrefs() {
        SharedPreferences sp = getSharedPreferences("cfg", MODE_PRIVATE);
        ChordAnalyzer.flats = sp.getBoolean("flats", false);
        ChordAnalyzer.allowSevenths = sp.getBoolean("sevenths", false);
        instrument = sp.getString("instrument", "piano");
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        loadPrefs();
        final int p = dp(16);

        // ===== Zone fixe : en-tête + lecteur =====
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(p, p, p, 0);

        // En-tête : logo + titre
        LinearLayout header = AccordsUi.row(this);
        FrameLayout logo = new FrameLayout(this);
        logo.setBackground(AccordsUi.gradient(AccordsUi.VIOLET, AccordsUi.MAGENTA, dp(14)));
        logo.setClipToOutline(true);
        ImageView logoImg = new ImageView(this);
        logoImg.setImageResource(R.drawable.accords_logo_fg);
        logoImg.setScaleX(1.7f);
        logoImg.setScaleY(1.7f);
        logo.addView(logoImg, new FrameLayout.LayoutParams(-1, -1));
        header.addView(logo, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("Accords");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(AccordsUi.TEXT);
        TextView subtitle = new TextView(this);
        subtitle.setText("Notes · accords · paroles");
        subtitle.setTextSize(12);
        subtitle.setTextColor(AccordsUi.MUTED);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles);
        top.addView(header);

        // Carte lecteur (dégradé)
        LinearLayout playerCard = new LinearLayout(this);
        playerCard.setOrientation(LinearLayout.VERTICAL);
        playerCard.setBackground(AccordsUi.gradient(0xFF3A2D9C, 0xFF7B2FA8, dp(24)));
        playerCard.setPadding(p, p, p, p);
        playerCard.setElevation(dp(6));

        currentChord = new TextView(this);
        currentChord.setText("—");
        currentChord.setTextSize(46);
        currentChord.setTypeface(Typeface.DEFAULT_BOLD);
        currentChord.setTextColor(Color.WHITE);
        currentChord.setGravity(Gravity.CENTER);
        currentChord.setShadowLayer(dp(8), 0, dp(2), 0x66000000);
        playerCard.addView(currentChord);

        lyricNow = new TextView(this);
        lyricNow.setTextSize(15);
        lyricNow.setTextColor(0xFFF1F2FF);
        lyricNow.setGravity(Gravity.CENTER);
        lyricNow.setMinLines(2);
        playerCard.addView(lyricNow);

        seek = new SeekBar(this);
        seek.setProgressTintList(ColorStateList.valueOf(AccordsUi.AMBER));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(0x44FFFFFF));
        seek.setThumbTintList(ColorStateList.valueOf(AccordsUi.AMBER));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int v, boolean fromUser) {
                if (fromUser && player != null) { player.seekTo(v); tick(); }
            }
            public void onStartTrackingTouch(SeekBar sb) { }
            public void onStopTrackingTouch(SeekBar sb) { }
        });
        playerCard.addView(seek, AccordsUi.lp(this, -1, -2, 6));

        // Commandes : transposition | lecture | PDF
        LinearLayout controls = AccordsUi.row(this);

        LinearLayout left = AccordsUi.row(this);
        left.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        LinearLayout pill = AccordsUi.row(this);
        pill.setBackground(AccordsUi.solid(0x26FFFFFF, dp(22)));
        Button bMinus = AccordsUi.iconButton(this, "−");
        bMinus.setOnClickListener(v -> changeTranspose(-1));
        transposeLabel = new TextView(this);
        transposeLabel.setGravity(Gravity.CENTER);
        transposeLabel.setTextSize(12);
        transposeLabel.setTextColor(Color.WHITE);
        transposeLabel.setPadding(dp(4), 0, dp(4), 0);
        Button bPlus = AccordsUi.iconButton(this, "+");
        bPlus.setOnClickListener(v -> changeTranspose(1));
        pill.addView(bMinus, new LinearLayout.LayoutParams(dp(40), dp(40)));
        pill.addView(transposeLabel, new LinearLayout.LayoutParams(-2, -2));
        pill.addView(bPlus, new LinearLayout.LayoutParams(dp(40), dp(40)));
        left.addView(pill);
        controls.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

        bPlay = AccordsUi.circle(this, "▶", AccordsUi.AMBER, AccordsUi.ORANGE, AccordsUi.DARK);
        bPlay.setOnClickListener(v -> togglePlay());
        controls.addView(bPlay, new LinearLayout.LayoutParams(dp(60), dp(60)));

        LinearLayout right = AccordsUi.row(this);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button bPdf = AccordsUi.chip(this, "PDF");
        bPdf.setOnClickListener(v -> exportPdf());
        right.addView(bPdf);
        controls.addView(right, new LinearLayout.LayoutParams(0, -2, 1f));

        playerCard.addView(controls, AccordsUi.lp(this, -1, -2, 6));
        top.addView(playerCard, AccordsUi.lp(this, -1, -2, 14));
        updateTransposeLabel();

        // ===== Partie défilante =====
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(p, p, p, p);

        // Carte fichier
        LinearLayout fileCard = new LinearLayout(this);
        fileCard.setOrientation(LinearLayout.VERTICAL);
        AccordsUi.card(fileCard, this);
        Button pick = AccordsUi.button(this, "♪  Choisir un fichier audio", AccordsUi.VIOLET, AccordsUi.MAGENTA, Color.WHITE);
        pick.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("audio/*");
            startActivityForResult(i, PICK);
        });
        fileCard.addView(pick);
        fileLabel = new TextView(this);
        fileLabel.setText("Aucun fichier choisi");
        fileLabel.setTextColor(AccordsUi.TEXT);
        fileLabel.setTextSize(14);
        fileCard.addView(fileLabel, AccordsUi.lp(this, -1, -2, 10));
        modelStatus = new TextView(this);
        modelStatus.setTextSize(12);
        modelStatus.setTextColor(AccordsUi.MUTED);
        fileCard.addView(modelStatus, AccordsUi.lp(this, -1, -2, 2));
        body.addView(fileCard);

        // Carte actions
        LinearLayout actionsCard = new LinearLayout(this);
        actionsCard.setOrientation(LinearLayout.VERTICAL);
        AccordsUi.card(actionsCard, this);
        TextView actionsTitle = new TextView(this);
        actionsTitle.setText("ANALYSE");
        actionsTitle.setTextSize(11);
        actionsTitle.setLetterSpacing(0.15f);
        actionsTitle.setTypeface(Typeface.DEFAULT_BOLD);
        actionsTitle.setTextColor(AccordsUi.MUTED);
        actionsCard.addView(actionsTitle);

        // --- Accords ---
        Button bChords = AccordsUi.button(this, "Accords, notes et grille", AccordsUi.VIOLET, AccordsUi.MAGENTA, Color.WHITE);
        bChords.setOnClickListener(v -> runTask("Analyse des accords…", () -> {
            analysis = null;
            prepare(0, 20, decoded == null ? 80 : 100);
            return "";
        }, this::renderChords));

        // --- Paroles hors ligne ---
        Button bOff = AccordsUi.button(this, "Paroles + accords (hors ligne)", AccordsUi.TEAL, AccordsUi.BLUE, Color.WHITE);
        bOff.setOnClickListener(v -> runTask("Paroles hors ligne…", () -> {
            int base = 0;
            if (!ModelDownloader.isReady(this)) {
                stage(0, 15);
                ensureModel(true);
                base = 15;
            }
            base = prepare(base, 10, 30);
            stage(base, 100 - base);
            AudioDecoder.Result d = getDecoded(null);
            float[] x16 = AudioDecoder.resample(d.samples, d.sampleRate, 16000);
            final List<Lyrics.Word> words = new ArrayList<>();
            final String[] err = new String[1];
            CountDownLatch latch = new CountDownLatch(1);
            LyricsOffline.transcribe(this, x16, new LyricsOffline.Callback() {
                public void onResult(List<Lyrics.Word> w) { words.addAll(w); latch.countDown(); }
                public void onError(String m) { err[0] = m; latch.countDown(); }
            }, pct -> report(pct, "Reconnaissance des paroles"));
            latch.await();
            if (err[0] != null) throw new Exception(err[0]);
            setLyrics(words);
            return "";
        }, this::renderChords));

        // --- Paroles en ligne ---
        Button bOn = AccordsUi.button(this, "Paroles + accords (en ligne)", AccordsUi.BLUE, AccordsUi.INDIGO, Color.WHITE);
        bOn.setOnClickListener(v -> {
            SharedPreferences sp = getSharedPreferences("cfg", MODE_PRIVATE);
            runTask("Paroles en ligne…", () -> {
                final int base = prepare(0, 10, 30);
                final int rem = 100 - base;
                final int upW = rem * 3 / 10;
                final double estSec = Math.max(10, analysis.dur / 8);
                List<Lyrics.Word> words;
                try {
                    words = LyricsOnline.transcribe(this, audioUri, audioName,
                            sp.getString("url", "https://api.openai.com/v1"),
                            sp.getString("key", ""),
                            sp.getString("model", "whisper-1"),
                            new LyricsOnline.Listener() {
                                public void onUpload(int pct) {
                                    stage(base, upW);
                                    report(pct, "Envoi de l'audio au serveur");
                                }
                                public void onWaiting() { startCreep(base + upW, rem - upW, estSec); }
                            });
                } finally {
                    creeping = false;
                }
                setLyrics(words);
                return "";
            }, this::renderChords);
        });

        Button bSet = AccordsUi.ghost(this, "⚙  Réglages (notation, instrument, API)");
        bSet.setOnClickListener(v -> showSettings());

        actions = new Button[]{bChords, bOff, bOn};
        actionsCard.addView(bChords, AccordsUi.lp(this, -1, -2, 10));
        actionsCard.addView(bOff, AccordsUi.lp(this, -1, -2, 10));
        actionsCard.addView(bOn, AccordsUi.lp(this, -1, -2, 10));
        actionsCard.addView(bSet, AccordsUi.lp(this, -1, -2, 12));
        body.addView(actionsCard, AccordsUi.lp(this, -1, -2, 14));

        // Progression
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setIndeterminate(false);
        bar.setProgressTintList(ColorStateList.valueOf(AccordsUi.AMBER));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        bar.setVisibility(View.INVISIBLE);
        body.addView(bar, AccordsUi.lp(this, -1, dp(6), 14));

        progressText = new TextView(this);
        progressText.setTextSize(12);
        progressText.setTextColor(AccordsUi.MUTED);
        progressText.setGravity(Gravity.CENTER);
        body.addView(progressText, AccordsUi.lp(this, -1, -2, 4));

        // Carte résultats
        LinearLayout outCard = new LinearLayout(this);
        outCard.setOrientation(LinearLayout.VERTICAL);
        AccordsUi.card(outCard, this);
        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(12);
        output.setTextColor(AccordsUi.TEXT);
        output.setTextIsSelectable(true);
        outCard.addView(output);
        body.addView(outCard, AccordsUi.lp(this, -1, -2, 10));

        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(body);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AccordsUi.BG);
        root.addView(top);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        // Animations d'entrée
        AccordsUi.enter(header, 0);
        AccordsUi.enter(playerCard, 80);
        AccordsUi.enter(fileCard, 160);
        AccordsUi.enter(actionsCard, 240);
        AccordsUi.enter(outCard, 320);

        if (ModelDownloader.isReady(this)) {
            modelStatus.setText("Modèle de paroles hors ligne : prêt ✓");
        } else {
            modelStatus.setText("Premier démarrage : téléchargement du modèle de paroles (≈ 40 Mo)…");
            new Thread(() -> {
                try { ensureModel(false); }
                catch (Exception e) {
                    runOnUiThread(() -> modelStatus.setText("Téléchargement impossible (" + e.getMessage()
                            + "). Il sera retenté au prochain usage des paroles hors ligne."));
                }
            }).start();
        }
    }

    /** Affiche l'accord courant avec un petit effet « pop » quand il change. */
    private void setChord(String c) {
        if (c.equals(lastChord)) return;
        lastChord = c;
        currentChord.setText(c);
        currentChord.setScaleX(1.18f);
        currentChord.setScaleY(1.18f);
        currentChord.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
    }

    // ---------------- Progression ----------------

    private void stage(int base, int weight) {
        stageBase = base;
        stageWeight = weight;
    }

    /** pctInStage : 0-100 dans l'étape en cours ; la barre globale va de 0 à 100 %. */
    private void report(int pctInStage, String label) {
        if (!busy) return;
        int in = Math.max(0, Math.min(100, pctInStage));
        final int total = Math.min(100, stageBase + stageWeight * in / 100);
        runOnUiThread(() -> {
            if (!busy) return;
            bar.setProgress(total);
            progressText.setText(label + " : " + total + " %");
        });
    }

    /** Pendant que le serveur travaille (durée inconnue), la barre monte jusqu'à 95 % selon une estimation. */
    private void startCreep(int base, int weight, double estSec) {
        stage(base, weight);
        creeping = true;
        new Thread(() -> {
            long t0 = System.currentTimeMillis();
            while (creeping) {
                double el = (System.currentTimeMillis() - t0) / 1000.0;
                report((int) Math.min(95, 100 * el / estSec), "Le serveur transcrit (estimation)");
                try { Thread.sleep(500); } catch (InterruptedException e) { return; }
            }
        }).start();
    }

    /** Décodage + analyse des accords si pas déjà faits. Retourne la nouvelle base de progression. */
    private int prepare(int base, int wDec, int wAna) throws Exception {
        if (decoded == null) {
            stage(base, wDec);
            getDecoded(pct -> report(pct, "Décodage de l'audio"));
            base += wDec;
        }
        if (analysis == null) {
            stage(base, wAna);
            AudioDecoder.Result d = getDecoded(null);
            analysis = ChordAnalyzer.analyze(
                    AudioDecoder.resample(d.samples, d.sampleRate, ChordAnalyzer.SR),
                    pct -> report(pct, "Analyse des accords"));
            base += wAna;
        }
        return base;
    }

    private void setLyrics(List<Lyrics.Word> words) {
        lyricWords = words;
        lyricLines = Lyrics.lines(words);
    }

    // ---------------- Fichier audio ----------------

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;

        if (req == PICK) {
            releasePlayer();
            audioUri = data.getData();
            decoded = null;
            analysis = null;
            grid = null;
            lyricWords = null;
            lyricLines = null;
            chordsShown = false;
            audioName = "audio.mp3";
            try (Cursor c = getContentResolver().query(audioUri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (i >= 0) audioName = c.getString(i);
                }
            } catch (Exception ignored) { }
            fileLabel.setText(audioName);
            output.setText("");
            progressText.setText("");
            bar.setVisibility(View.INVISIBLE);
            setChord("—");
            lyricNow.setText("");
            seek.setProgress(0);
            bPlay.setText("▶");
        } else if (req == SAVE_PDF) {
            try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
                PdfExporter.write(os, audioName, chordsText());
                Toast.makeText(this, "PDF enregistré", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Export impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private synchronized AudioDecoder.Result getDecoded(java.util.function.IntConsumer prog) throws Exception {
        if (decoded == null) decoded = AudioDecoder.decodeMono(this, audioUri, prog);
        return decoded;
    }

    // ---------------- Accords, paroles, transposition, illumination ----------------

    private String chordsText() {
        grid = ChordAnalyzer.buildGrid(analysis, transpose);
        StringBuilder h = new StringBuilder(ChordAnalyzer.summary(analysis, transpose)).append('\n');
        if (lyricWords != null) {
            h.append("PAROLES ET ACCORDS :\n\n")
                    .append(Lyrics.sheet(lyricWords, analysis, transpose, instrument)).append('\n');
        }
        h.append("GRILLE D'ACCORDS (début à ").append(String.format(Locale.US, "%.1f", analysis.t0)).append(" s");
        if (transpose != 0) h.append(", transposée de ").append(signed(transpose));
        h.append(") :\n");
        gridBase = h.length();
        return h + grid.text;
    }

    private void renderChords() {
        if (analysis == null) return;
        String full = chordsText();
        output.setText(full, TextView.BufferType.SPANNABLE);
        output.setAlpha(0.2f);
        output.animate().alpha(1f).setDuration(350).start();
        chordsShown = true;
        curSpan = null;
        curFg = null;
        tick();
    }

    private String signed(int v) { return (v >= 0 ? "+" : "") + v; }

    private void updateTransposeLabel() { transposeLabel.setText("Transp. " + signed(transpose)); }

    private void changeTranspose(int d) {
        transpose += d;
        if (transpose > 6) transpose -= 12;
        if (transpose < -6) transpose += 12;
        updateTransposeLabel();
        if (analysis != null && chordsShown) renderChords(); else tick();
    }

    private void highlight(int idx) {
        if (!chordsShown || grid == null) return;
        CharSequence t = output.getText();
        if (!(t instanceof Spannable)) return;
        Spannable sp = (Spannable) t;
        if (curSpan != null) { sp.removeSpan(curSpan); curSpan = null; }
        if (curFg != null) { sp.removeSpan(curFg); curFg = null; }
        if (idx >= 0 && idx < grid.start.length && grid.end[idx] > grid.start[idx]) {
            curSpan = new BackgroundColorSpan(AccordsUi.AMBER);
            curFg = new ForegroundColorSpan(AccordsUi.DARK);
            sp.setSpan(curSpan, gridBase + grid.start[idx], gridBase + grid.end[idx],
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(curFg, gridBase + grid.start[idx], gridBase + grid.end[idx],
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    // ---------------- Lecture ----------------

    private void togglePlay() {
        if (audioUri == null) {
            Toast.makeText(this, "Choisis d'abord un fichier audio", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (player == null) {
                player = MediaPlayer.create(this, audioUri);
                if (player == null) throw new Exception("Lecture impossible");
                seek.setMax(player.getDuration());
                player.setOnCompletionListener(mp -> {
                    bPlay.setText("▶");
                    handler.removeCallbacks(ticker);
                });
            }
            if (player.isPlaying()) {
                player.pause();
                bPlay.setText("▶");
            } else {
                player.start();
                bPlay.setText("⏸");
                handler.post(ticker);
            }
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void tick() {
        if (player == null) return;
        int pos = player.getCurrentPosition();
        seek.setProgress(pos);
        showLyricNow(pos / 1000.0);
        if (analysis == null) {
            setChord("—");
            return;
        }
        int idx = ChordAnalyzer.halfIndex(analysis, pos / 1000.0);
        setChord(idx < 0 ? "—" : ChordAnalyzer.transposeChord(analysis.chords[idx], transpose));
        highlight(idx);
    }

    /** Affiche la ligne de paroles en cours (en gras) et la suivante (en gris) pendant la lecture. */
    private void showLyricNow(double sec) {
        if (lyricLines == null) {
            lyricNow.setText("(paroles non extraites : touche « Paroles + accords »)");
            return;
        }
        if (lyricLines.isEmpty()) {
            lyricNow.setText("(aucune parole détectée)");
            return;
        }
        int i = -1;
        for (int k = 0; k < lyricLines.size(); k++) {
            if (lyricLines.get(k).start <= sec + 0.3) i = k; else break;
        }
        String cur, next = "";
        if (i < 0) {
            cur = "(intro instrumentale)";
            next = lyricLines.get(0).text;
        } else {
            Lyrics.Line l = lyricLines.get(i);
            cur = sec > l.end + 3 ? "(instrumental)" : l.text;
            if (i + 1 < lyricLines.size()) next = lyricLines.get(i + 1).text;
        }
        SpannableString sp = new SpannableString(cur + "\n" + next);
        sp.setSpan(new StyleSpan(Typeface.BOLD), 0, cur.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!next.isEmpty())
            sp.setSpan(new ForegroundColorSpan(0xFF888888), cur.length() + 1, sp.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        lyricNow.setText(sp);
    }

    private void releasePlayer() {
        handler.removeCallbacks(ticker);
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && player.isPlaying()) {
            player.pause();
            bPlay.setText("▶");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        creeping = false;
        releasePlayer();
    }

    // ---------------- PDF ----------------

    private void exportPdf() {
        if (analysis == null) {
            Toast.makeText(this, "Lance d'abord « Accords, notes et grille »", Toast.LENGTH_SHORT).show();
            return;
        }
        String base = audioName.replaceAll("\\.[^.]+$", "");
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_TITLE, "accords-" + base + ".pdf");
        startActivityForResult(i, SAVE_PDF);
    }

    // ---------------- Tâches longues ----------------

    private void runTask(String label, Task t, Runnable onDone) {
        if (audioUri == null) {
            Toast.makeText(this, "Choisis d'abord un fichier audio", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true);
        stage(0, 100);
        bar.setProgress(0);
        progressText.setText("0 %");
        chordsShown = false;
        output.setText(label);
        new Thread(() -> {
            String r;
            boolean ok = true;
            try { r = t.run(); } catch (Throwable e) { r = "Erreur : " + e.getMessage(); ok = false; }
            creeping = false;
            final String out = r;
            final boolean success = ok;
            runOnUiThread(() -> {
                setBusy(false);
                output.setText(out);
                if (success) {
                    bar.setProgress(100);
                    progressText.setText("Terminé : 100 %");
                    if (onDone != null) onDone.run();
                } else {
                    progressText.setText("Échec");
                }
            });
        }).start();
    }

    private void setBusy(boolean b) {
        busy = b;
        if (b) bar.setVisibility(View.VISIBLE);
        for (Button x : actions) x.setEnabled(!b);
    }

    private void ensureModel(boolean inTask) throws Exception {
        ModelDownloader.ensure(this, pct -> {
            if (inTask) report(pct, "Téléchargement du modèle de paroles");
            runOnUiThread(() -> modelStatus.setText("Téléchargement du modèle de paroles : " + pct + " %"));
        });
        runOnUiThread(() -> modelStatus.setText("Modèle de paroles hors ligne : prêt ✓"));
    }

    // ---------------- Réglages ----------------

    private void showSettings() {
        SharedPreferences sp = getSharedPreferences("cfg", MODE_PRIVATE);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), 0);

        CheckBox cbFlat = new CheckBox(this);
        cbFlat.setText("Bémols (Réb, Mib, Solb…) au lieu de dièses");
        cbFlat.setChecked(sp.getBoolean("flats", false));
        cbFlat.setButtonTintList(ColorStateList.valueOf(AccordsUi.AMBER));
        l.addView(cbFlat);

        CheckBox cbSev = new CheckBox(this);
        cbSev.setText("Inclure les accords de 7ème (moins fiable)");
        cbSev.setChecked(sp.getBoolean("sevenths", false));
        cbSev.setButtonTintList(ColorStateList.valueOf(AccordsUi.AMBER));
        l.addView(cbSev);

        EditText inst = field(l, "Instrument des intros/solos (ex. piano)", sp.getString("instrument", "piano"));

        TextView apiTitle = new TextView(this);
        apiTitle.setText("\nAPI en ligne (compatible OpenAI)");
        l.addView(apiTitle);
        EditText url = field(l, "URL de l'API", sp.getString("url", "https://api.openai.com/v1"));
        EditText key = field(l, "Clé API", sp.getString("key", ""));
        EditText model = field(l, "Modèle", sp.getString("model", "whisper-1"));

        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        new AlertDialog.Builder(this)
                .setTitle("Réglages")
                .setView(sv)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    boolean sevChanged = cbSev.isChecked() != ChordAnalyzer.allowSevenths;
                    sp.edit()
                            .putBoolean("flats", cbFlat.isChecked())
                            .putBoolean("sevenths", cbSev.isChecked())
                            .putString("instrument", inst.getText().toString().trim())
                            .putString("url", url.getText().toString().trim())
                            .putString("key", key.getText().toString().trim())
                            .putString("model", model.getText().toString().trim())
                            .apply();
                    loadPrefs();
                    if (sevChanged) Toast.makeText(this,
                            "Relance « Accords, notes et grille » pour appliquer les 7èmes", Toast.LENGTH_LONG).show();
                    if (analysis != null && chordsShown) renderChords(); else tick();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private EditText field(LinearLayout parent, String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        parent.addView(e);
        return e;
    }
}
