package com.accords.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public class MainActivity extends Activity {

    private static final int PICK = 1, SAVE_PDF = 2;

    private Uri audioUri;
    private String audioName = "audio.mp3";
    private AudioDecoder.Result decoded;

    private ChordAnalyzer.Analysis analysis;
    private ChordAnalyzer.Grid grid;
    private int gridBase;
    private int transpose = 0;
    private boolean chordsShown = false;
    private BackgroundColorSpan curSpan;

    private MediaPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        public void run() {
            tick();
            if (player != null && player.isPlaying()) handler.postDelayed(this, 100);
        }
    };

    private TextView fileLabel, modelStatus, output, currentChord, transposeLabel;
    private SeekBar seek;
    private Button bPlay;
    private ProgressBar bar;
    private Button[] actions;

    interface Task { String run() throws Exception; }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int p = dp(16);

        // ----- Partie fixe : accord courant, lecture, transposition, PDF -----
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(p, p / 2, p, 0);

        currentChord = new TextView(this);
        currentChord.setText("—");
        currentChord.setTextSize(40);
        currentChord.setTypeface(Typeface.DEFAULT_BOLD);
        currentChord.setGravity(Gravity.CENTER);
        top.addView(currentChord);

        seek = new SeekBar(this);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                if (fromUser && player != null) { player.seekTo(v); tick(); }
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        top.addView(seek);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        bPlay = btn("▶");
        bPlay.setOnClickListener(v -> togglePlay());
        Button bMinus = btn("−");
        bMinus.setOnClickListener(v -> changeTranspose(-1));
        transposeLabel = new TextView(this);
        transposeLabel.setGravity(Gravity.CENTER);
        transposeLabel.setTextSize(12);
        Button bPlus = btn("+");
        bPlus.setOnClickListener(v -> changeTranspose(1));
        Button bPdf = btn("PDF");
        bPdf.setOnClickListener(v -> exportPdf());
        for (View v : new View[]{bPlay, bMinus, transposeLabel, bPlus, bPdf})
            row.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(row);
        updateTransposeLabel();

        // ----- Partie défilante -----
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(p, 0, p, p);

        Button pick = btn("Choisir un fichier audio");
        pick.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("audio/*");
            startActivityForResult(i, PICK);
        });
        body.addView(pick);

        fileLabel = new TextView(this);
        fileLabel.setText("Aucun fichier choisi");
        body.addView(fileLabel);

        modelStatus = new TextView(this);
        modelStatus.setTextSize(12);
        body.addView(modelStatus);

        Button bChords = btn("Accords, notes et grille");
        bChords.setOnClickListener(v -> runTask("Analyse des accords…", () -> {
            AudioDecoder.Result d = getDecoded();
            analysis = ChordAnalyzer.analyze(AudioDecoder.resample(d.samples, d.sampleRate, ChordAnalyzer.SR));
            return "";
        }, this::renderChords));

        Button bOff = btn("Paroles (hors ligne)");
        bOff.setOnClickListener(v -> runTask("Reconnaissance vocale hors ligne…", () -> {
            ensureModel();
            AudioDecoder.Result d = getDecoded();
            float[] x16 = AudioDecoder.resample(d.samples, d.sampleRate, 16000);
            final String[] res = new String[1];
            CountDownLatch latch = new CountDownLatch(1);
            LyricsOffline.transcribe(this, x16, new LyricsOffline.Callback() {
                public void onResult(String t) { res[0] = t; latch.countDown(); }
                public void onError(String m) { res[0] = m; latch.countDown(); }
            });
            latch.await();
            return res[0];
        }, null));

        Button bOn = btn("Paroles (en ligne)");
        bOn.setOnClickListener(v -> {
            SharedPreferences sp = getSharedPreferences("cfg", MODE_PRIVATE);
            runTask("Envoi au serveur…", () -> LyricsOnline.transcribe(this, audioUri, audioName,
                    sp.getString("url", "https://api.openai.com/v1"),
                    sp.getString("key", ""),
                    sp.getString("model", "whisper-1")), null);
        });

        Button bSet = btn("Paramètres API en ligne");
        bSet.setOnClickListener(v -> showSettings());

        actions = new Button[]{bChords, bOff, bOn};
        for (Button x : new Button[]{bChords, bOff, bOn, bSet}) body.addView(x);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.setVisibility(View.INVISIBLE);
        body.addView(bar);

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(13);
        output.setTextIsSelectable(true);
        body.addView(output);

        ScrollView sv = new ScrollView(this);
        sv.addView(body);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(top);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        if (ModelDownloader.isReady(this)) {
            modelStatus.setText("Modèle de paroles hors ligne : prêt ✓");
        } else {
            modelStatus.setText("Premier démarrage : téléchargement du modèle de paroles (≈ 40 Mo)…");
            new Thread(() -> {
                try { ensureModel(); }
                catch (Exception e) {
                    runOnUiThread(() -> modelStatus.setText("Téléchargement impossible (" + e.getMessage()
                            + "). Il sera retenté au prochain usage des paroles hors ligne."));
                }
            }).start();
        }
    }

    private Button btn(String t) {
        Button b = new Button(this);
        b.setText(t);
        return b;
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
            currentChord.setText("—");
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

    private synchronized AudioDecoder.Result getDecoded() throws Exception {
        if (decoded == null) decoded = AudioDecoder.decodeMono(this, audioUri);
        return decoded;
    }

    // ---------------- Accords, transposition, illumination ----------------

    private String chordsText() {
        grid = ChordAnalyzer.buildGrid(analysis, transpose);
        String head = ChordAnalyzer.summary(analysis, transpose)
                + "\nGRILLE D'ACCORDS (début à " + String.format(Locale.US, "%.1f", analysis.t0) + " s"
                + (transpose != 0 ? ", transposée de " + signed(transpose) : "") + ") :\n";
        gridBase = head.length();
        return head + grid.text;
    }

    private void renderChords() {
        if (analysis == null) return;
        String full = chordsText();
        output.setText(full, TextView.BufferType.SPANNABLE);
        chordsShown = true;
        curSpan = null;
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
        if (idx >= 0 && idx < grid.start.length && grid.end[idx] > grid.start[idx]) {
            curSpan = new BackgroundColorSpan(0xFFFFD54F);
            sp.setSpan(curSpan, gridBase + grid.start[idx], gridBase + grid.end[idx],
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
        if (analysis == null) {
            currentChord.setText("—");
            return;
        }
        int idx = ChordAnalyzer.halfIndex(analysis, pos / 1000.0);
        currentChord.setText(idx < 0 ? "—" : ChordAnalyzer.transposeChord(analysis.chords[idx], transpose));
        highlight(idx);
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
        chordsShown = false;
        output.setText(label);
        new Thread(() -> {
            String r;
            boolean ok = true;
            try { r = t.run(); } catch (Throwable e) { r = "Erreur : " + e.getMessage(); ok = false; }
            final String out = r;
            final boolean success = ok;
            runOnUiThread(() -> {
                output.setText(out);
                setBusy(false);
                if (success && onDone != null) onDone.run();
            });
        }).start();
    }

    private void setBusy(boolean busy) {
        bar.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        for (Button b : actions) b.setEnabled(!busy);
    }

    private void ensureModel() throws Exception {
        ModelDownloader.ensure(this, pct -> runOnUiThread(() ->
                modelStatus.setText("Téléchargement du modèle de paroles : " + pct + " %")));
        runOnUiThread(() -> modelStatus.setText("Modèle de paroles hors ligne : prêt ✓"));
    }

    // ---------------- Paramètres ----------------

    private void showSettings() {
        SharedPreferences sp = getSharedPreferences("cfg", MODE_PRIVATE);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), 0);
        EditText url = field(l, "URL de l'API", sp.getString("url", "https://api.openai.com/v1"));
        EditText key = field(l, "Clé API", sp.getString("key", ""));
        EditText model = field(l, "Modèle", sp.getString("model", "whisper-1"));
        new AlertDialog.Builder(this)
                .setTitle("API en ligne (compatible OpenAI)")
                .setView(l)
                .setPositiveButton("Enregistrer", (d, w) -> sp.edit()
                        .putString("url", url.getText().toString().trim())
                        .putString("key", key.getText().toString().trim())
                        .putString("model", model.getText().toString().trim())
                        .apply())
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
