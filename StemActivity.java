package bj.radio;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Séparateur de pistes : voix, batterie, basse, piano et autres instruments.
 * On écoute chaque piste puis on la télécharge en MP3.
 */
public class StemActivity extends Activity {

    private static final int PICK = 41;
    private static final int SAVE = 42;

    private Uri audioUri;
    private String audioName = "morceau";
    private TextView fileLabel, status;
    private ProgressBar bar;
    private Button bGo;
    private LinearLayout results;
    private Button[] playBtns;
    private File[] stemFiles;
    private MediaPlayer player;
    private int playingIdx = -1;
    private int pendingSave = -1;
    private volatile boolean busy;
    private final AtomicBoolean cancel = new AtomicBoolean();

    private int dp(int v) { return AccordsUi.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(AccordsUi.BG);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(36), dp(18), dp(24));
        scroll.addView(body);

        TextView title = new TextView(this);
        title.setText("Séparateur de voix");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(AccordsUi.TEXT);
        body.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Sépare un morceau en pistes : voix, batterie, basse, piano et autres instruments. "
                + "Chaque piste s'écoute puis se télécharge en MP3. Tout est calculé sur le téléphone.");
        sub.setTextSize(13);
        sub.setTextColor(AccordsUi.MUTED);
        body.addView(sub, AccordsUi.lp(this, -1, -2, 6));

        // Carte fichier
        LinearLayout fileCard = new LinearLayout(this);
        fileCard.setOrientation(LinearLayout.VERTICAL);
        AccordsUi.card(fileCard, this);
        Button pick = AccordsUi.button(this, "♪  Choisir un morceau", AccordsUi.VIOLET, AccordsUi.MAGENTA, Color.WHITE);
        pick.setOnClickListener(v -> {
            if (busy) return;
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
        body.addView(fileCard, AccordsUi.lp(this, -1, -2, 18));

        // Bouton de séparation + progression
        bGo = AccordsUi.button(this, "✂  Séparer les pistes", AccordsUi.ORANGE, AccordsUi.MAGENTA, Color.WHITE);
        bGo.setOnClickListener(v -> {
            if (busy) cancel.set(true); else startSeparation();
        });
        body.addView(bGo, AccordsUi.lp(this, -1, -2, 14));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(AccordsUi.AMBER));
        bar.setVisibility(View.GONE);
        body.addView(bar, AccordsUi.lp(this, -1, dp(8), 14));

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(AccordsUi.MUTED);
        body.addView(status, AccordsUi.lp(this, -1, -2, 6));

        // Résultats
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        AccordsUi.card(results, this);
        results.setVisibility(View.GONE);
        TextView rt = new TextView(this);
        rt.setText("Pistes séparées");
        rt.setTextSize(16);
        rt.setTypeface(Typeface.DEFAULT_BOLD);
        rt.setTextColor(AccordsUi.TEXT);
        results.addView(rt);

        playBtns = new Button[StemSeparator.STEMS.length];
        for (int i = 0; i < StemSeparator.STEMS.length; i++) {
            final int idx = i;
            LinearLayout row = AccordsUi.row(this);
            TextView name = new TextView(this);
            name.setText(StemSeparator.LABELS[i]);
            name.setTextSize(14);
            name.setTextColor(AccordsUi.TEXT);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            Button play = AccordsUi.chip(this, "▶ Écouter");
            play.setOnClickListener(v -> togglePlay(idx));
            playBtns[i] = play;
            row.addView(play);
            Button dl = AccordsUi.chip(this, "⬇ MP3");
            dl.setOnClickListener(v -> askSave(idx));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
            dlp.leftMargin = dp(8);
            row.addView(dl, dlp);
            results.addView(row, AccordsUi.lp(this, -1, -2, 12));
        }
        body.addView(results, AccordsUi.lp(this, -1, -2, 18));

        setContentView(scroll);
        AccordsUi.enter(fileCard, 0);
        AccordsUi.enter(bGo, 80);
    }

    // ------------------------------------------------------------------ Séparation

    private void ui(int pct, String msg) {
        runOnUiThread(() -> {
            bar.setProgress(pct);
            status.setText(msg + " : " + pct + " %");
        });
    }

    private void startSeparation() {
        if (audioUri == null) {
            Toast.makeText(this, "Choisis d'abord un morceau", Toast.LENGTH_SHORT).show();
            return;
        }
        busy = true;
        cancel.set(false);
        stopPlayer();
        results.setVisibility(View.GONE);
        bGo.setText("Annuler");
        bar.setProgress(0);
        bar.setVisibility(View.VISIBLE);
        status.setText("");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final Uri uri = audioUri;

        new Thread(() -> {
            try {
                StemSeparator.ensureModels(this, this::ui);
                if (cancel.get()) throw new CancellationException();
                float[][] pcm = AudioDecoder.decodeStereo44k(this, uri, pct -> ui(pct, "Lecture du morceau"));
                if (cancel.get()) throw new CancellationException();
                final File[] files = StemSeparator.separate(this, pcm, this::ui, cancel);
                runOnUiThread(() -> {
                    stemFiles = files;
                    results.setVisibility(View.VISIBLE);
                    status.setText("Terminé. Écoute chaque piste ou télécharge-la en MP3.");
                });
            } catch (CancellationException ce) {
                runOnUiThread(() -> status.setText("Séparation annulée"));
            } catch (OutOfMemoryError oom) {
                runOnUiThread(() -> status.setText("Mémoire insuffisante : essaie un morceau plus court."));
            } catch (Throwable e) {
                final String m = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> status.setText("Échec : " + m));
            } finally {
                runOnUiThread(() -> {
                    busy = false;
                    bGo.setText("✂  Séparer les pistes");
                    bar.setVisibility(View.GONE);
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ Écoute

    private void togglePlay(int i) {
        if (stemFiles == null) return;
        if (playingIdx == i) { stopPlayer(); return; }
        stopPlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(stemFiles[i].getPath());
            player.prepare();
            player.setOnCompletionListener(mp -> stopPlayer());
            player.start();
            playingIdx = i;
            playBtns[i].setText("■ Stop");
        } catch (Exception e) {
            Toast.makeText(this, "Lecture impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopPlayer();
        }
    }

    private void stopPlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
        if (playingIdx >= 0 && playBtns != null) playBtns[playingIdx].setText("▶ Écouter");
        playingIdx = -1;
    }

    // ------------------------------------------------------------------ Téléchargement MP3

    private void askSave(int i) {
        if (stemFiles == null) return;
        pendingSave = i;
        String base = audioName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        Intent in = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        in.addCategory(Intent.CATEGORY_OPENABLE);
        in.setType("audio/mpeg");
        in.putExtra(Intent.EXTRA_TITLE, base + "-" + StemSeparator.SLUGS[i] + ".mp3");
        startActivityForResult(in, SAVE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;

        if (req == PICK) {
            stopPlayer();
            audioUri = data.getData();
            audioName = "morceau";
            try (Cursor c = getContentResolver().query(audioUri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (i >= 0) audioName = c.getString(i);
                }
            } catch (Exception ignored) { }
            fileLabel.setText(audioName);
            results.setVisibility(View.GONE);
            status.setText("");
            stemFiles = null;
        } else if (req == SAVE && pendingSave >= 0 && stemFiles != null) {
            final int idx = pendingSave;
            final Uri dest = data.getData();
            final File wav = stemFiles[idx];
            Toast.makeText(this, "Conversion en MP3…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                File tmp = new File(getCacheDir(), "export-" + StemSeparator.SLUGS[idx] + ".mp3");
                try {
                    Mp3Encoder.wavToMp3(wav, tmp);
                    try (InputStream in = new FileInputStream(tmp);
                         OutputStream out = getContentResolver().openOutputStream(dest)) {
                        byte[] buf = new byte[65536];
                        int r;
                        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                    }
                    runOnUiThread(() -> Toast.makeText(this, "MP3 enregistré : " + StemSeparator.LABELS[idx],
                            Toast.LENGTH_SHORT).show());
                } catch (Throwable e) {
                    final String m = e.getMessage() == null ? e.toString() : e.getMessage();
                    runOnUiThread(() -> Toast.makeText(this, "Export impossible : " + m, Toast.LENGTH_LONG).show());
                } finally {
                    tmp.delete();
                }
            }).start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPlayer();
        cancel.set(true);
    }
}
