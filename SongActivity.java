package bj.radio;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Création d'une chanson à partir d'un texte :
 * tonalité, mesure, rythme, tempo, instruments, voix qui chante et chœur.
 */
public class SongActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int SAVE_WAV = 7;
    private static final String[] KEYS = {"Do", "Do#", "Ré", "Mib", "Mi", "Fa", "Fa#", "Sol", "Lab", "La", "Sib", "Si"};
    private static final String[] MODES = {"Majeur", "Mineur"};
    private static final String[] SIGS = {"4/4", "3/4", "2/4", "2/2", "6/8", "12/8"};
    private static final String[] VOICES = {"Voix d'homme", "Voix de femme", "Voix d'enfant", "Voix de garçon", "Voix de fille"};
    private static final float[] VOICE_PITCH = {0.75f, 1.15f, 1.5f, 1.35f, 1.6f};
    private static final int BARS_PER_LINE = 2;
    private static final String[] SOLO_CHOICES = {"Aucune", "Solo de piano", "Solo de batterie (roulements)", "Piano puis batterie"};
    private static final String[] SOLO_BARS = {"2 mesures", "4 mesures", "8 mesures"};
    private static final int[] SOLO_BARS_N = {2, 4, 8};

    /** Un morceau de la chanson : une phrase chantée (kind 0), un solo de piano (1) ou de batterie (2). */
    static class Item {
        final int kind;
        final String text;
        final int bars;
        boolean end; // fin d'un couplet
        Item(int kind, String text, int bars) { this.kind = kind; this.text = text; this.bars = bars; }
    }

    private Spinner spKey, spMode, spSig, spStyle, spVoice;
    private SeekBar tempo;
    private TextView tempoLabel, status, lineNow, choirTotal;
    private CheckBox cbPiano, cbBass, cbDrums, cbStrings, cbFill;
    private Spinner spSolo, spSoloBars;
    private EditText lyrics;
    private Button bGo, bStop, bSave;
    private final int[] cnt = {3, 2, 0}; // femmes, hommes, enfants
    private final TextView[] cntLabel = new TextView[3];

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private AudioTrack track;
    private short[] lastPcm;
    private final Handler h = new Handler(Looper.getMainLooper());

    private int dp(int v) { return AccordsUi.dp(this, v); }

    // ------------------------------------------------------------------ Interface

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tts = new TextToSpeech(this, this);

        final int p = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AccordsUi.BG);

        // En-tête
        LinearLayout header = AccordsUi.row(this);
        header.setPadding(p, p, p, dp(8));
        Button back = AccordsUi.iconButton(this, "‹");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = new TextView(this);
        title.setText("Créer une chanson");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(AccordsUi.TEXT);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title);
        root.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(p, 0, p, p);

        // --- Texte ---
        LinearLayout cText = card();
        cText.addView(section("TEXTE DE LA CHANSON"));
        lyrics = new EditText(this);
        lyrics.setHint("Écris ou colle ton texte ici.\nUne ligne = une phrase chantée.\nUne ligne vide sépare les couplets.\nPour un solo à un endroit précis, écris sur une ligne : [SOLO PIANO] ou [SOLO BATTERIE] (ex. [SOLO PIANO 4] pour 4 mesures).");
        lyrics.setHintTextColor(AccordsUi.MUTED);
        lyrics.setTextColor(AccordsUi.TEXT);
        lyrics.setTextSize(15);
        lyrics.setMinLines(8);
        lyrics.setGravity(Gravity.TOP | Gravity.START);
        lyrics.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        lyrics.setBackground(AccordsUi.solid(0x1AFFFFFF, dp(12)));
        lyrics.setPadding(dp(12), dp(10), dp(12), dp(10));
        cText.addView(lyrics, AccordsUi.lp(this, -1, -2, 8));
        body.addView(cText);

        // --- Musique ---
        LinearLayout cMusic = card();
        cMusic.addView(section("MUSIQUE"));
        spKey = spinner(KEYS);
        spMode = spinner(MODES);
        spSig = spinner(SIGS);
        spStyle = spinner(SongSynth.STYLES);
        addLabeled(cMusic, "Tonalité", spKey);
        addLabeled(cMusic, "Mode", spMode);
        addLabeled(cMusic, "Mesure (temps)", spSig);
        addLabeled(cMusic, "Rythme", spStyle);

        tempoLabel = new TextView(this);
        tempoLabel.setTextColor(AccordsUi.MUTED);
        tempoLabel.setTextSize(13);
        cMusic.addView(tempoLabel, AccordsUi.lp(this, -1, -2, 12));
        tempo = new SeekBar(this);
        tempo.setMax(110); // 50 à 160 BPM
        tempo.setProgressTintList(ColorStateList.valueOf(AccordsUi.AMBER));
        tempo.setThumbTintList(ColorStateList.valueOf(AccordsUi.AMBER));
        tempo.setProgress(SongSynth.defaultBpm("Adoration") - 50);
        updateTempoLabel();
        tempo.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int v, boolean user) { updateTempoLabel(); }
            public void onStartTrackingTouch(SeekBar sb) { }
            public void onStopTrackingTouch(SeekBar sb) { }
        });
        cMusic.addView(tempo);
        // Le tempo s'ajuste au rythme choisi (modifiable ensuite)
        spStyle.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a, View v, int pos, long id) {
                tempo.setProgress(SongSynth.defaultBpm(SongSynth.STYLES[pos]) - 50);
            }
            public void onNothingSelected(android.widget.AdapterView<?> a) { }
        });
        body.addView(cMusic, AccordsUi.lp(this, -1, -2, 12));

        // --- Instruments ---
        LinearLayout cInst = card();
        cInst.addView(section("INSTRUMENTS"));
        cbPiano = check("Piano solo", true);
        cbBass = check("Basse", true);
        cbDrums = check("Batterie / percussions", false);
        cbStrings = check("Cordes (nappe)", false);
        cInst.addView(cbPiano);
        cInst.addView(cbBass);
        cInst.addView(cbDrums);
        cInst.addView(cbStrings);
        body.addView(cInst, AccordsUi.lp(this, -1, -2, 12));

        // --- Parties instrumentales ---
        LinearLayout cSolo = card();
        cSolo.addView(section("PARTIES INSTRUMENTALES"));
        spSolo = spinner(SOLO_CHOICES);
        spSoloBars = spinner(SOLO_BARS);
        spSoloBars.setSelection(1);
        addLabeled(cSolo, "Après le 1er couplet", spSolo);
        addLabeled(cSolo, "Durée du solo", spSoloBars);
        cbFill = check("Roulement de batterie à la fin des couplets (si batterie cochée)", true);
        cSolo.addView(cbFill, AccordsUi.lp(this, -1, -2, 8));
        TextView soloNote = new TextView(this);
        soloNote.setText("Tu peux aussi placer [SOLO PIANO] ou [SOLO BATTERIE] n'importe où dans le texte : "
                + "ces repères remplacent alors le choix ci-dessus.");
        soloNote.setTextColor(AccordsUi.MUTED);
        soloNote.setTextSize(11);
        cSolo.addView(soloNote, AccordsUi.lp(this, -1, -2, 6));
        body.addView(cSolo, AccordsUi.lp(this, -1, -2, 12));

        // --- Voix + chœur ---
        LinearLayout cVoice = card();
        cVoice.addView(section("VOIX"));
        spVoice = spinner(VOICES);
        addLabeled(cVoice, "Qui chante", spVoice);
        TextView choirTitle = section("CHŒUR");
        cVoice.addView(choirTitle, AccordsUi.lp(this, -2, -2, 16));
        counter(cVoice, "Femmes", 0);
        counter(cVoice, "Hommes", 1);
        counter(cVoice, "Enfants", 2);
        choirTotal = new TextView(this);
        choirTotal.setTextColor(AccordsUi.AMBER);
        choirTotal.setTextSize(13);
        cVoice.addView(choirTotal, AccordsUi.lp(this, -1, -2, 6));
        updateChoirTotal();
        body.addView(cVoice, AccordsUi.lp(this, -1, -2, 12));

        // --- Lecture ---
        LinearLayout cPlay = new LinearLayout(this);
        cPlay.setOrientation(LinearLayout.VERTICAL);
        cPlay.setBackground(AccordsUi.gradient(0xFF3A2D9C, 0xFF7B2FA8, dp(24)));
        cPlay.setPadding(p, p, p, p);
        cPlay.setElevation(dp(6));
        lineNow = new TextView(this);
        lineNow.setTextColor(Color.WHITE);
        lineNow.setTextSize(16);
        lineNow.setTypeface(Typeface.DEFAULT_BOLD);
        lineNow.setGravity(Gravity.CENTER);
        lineNow.setMinLines(2);
        cPlay.addView(lineNow);
        status = new TextView(this);
        status.setTextColor(0xFFF1F2FF);
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER);
        cPlay.addView(status, AccordsUi.lp(this, -1, -2, 4));

        bGo = AccordsUi.button(this, "▶  Créer et jouer", AccordsUi.AMBER, AccordsUi.ORANGE, AccordsUi.DARK);
        bGo.setOnClickListener(v -> generate());
        cPlay.addView(bGo, AccordsUi.lp(this, -1, -2, 12));

        LinearLayout row = AccordsUi.row(this);
        bStop = AccordsUi.chip(this, "■  Arrêter");
        bStop.setOnClickListener(v -> stopAll("Arrêté"));
        bSave = AccordsUi.chip(this, "Enregistrer (WAV)");
        bSave.setEnabled(false);
        bSave.setOnClickListener(v -> saveWav());
        row.addView(bStop, new LinearLayout.LayoutParams(0, -2, 1f));
        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        row.addView(bSave, new LinearLayout.LayoutParams(0, -2, 1f));
        cPlay.addView(row, AccordsUi.lp(this, -1, -2, 10));
        body.addView(cPlay, AccordsUi.lp(this, -1, -2, 16));

        TextView note = new TextView(this);
        note.setText("La voix est une voix de synthèse qui récite le texte en rythme sur la musique ; "
                + "le chœur est un « aaah » synthétique. Ce n'est pas un vrai chant.");
        note.setTextColor(AccordsUi.MUTED);
        note.setTextSize(11);
        body.addView(note, AccordsUi.lp(this, -1, -2, 10));

        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    // ------------------------------------------------------------------ Petits constructeurs d'interface

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        AccordsUi.card(c, this);
        return c;
    }

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11);
        t.setLetterSpacing(0.15f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(AccordsUi.MUTED);
        return t;
    }

    private CheckBox check(String text, boolean on) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setChecked(on);
        c.setTextColor(AccordsUi.TEXT);
        c.setButtonTintList(ColorStateList.valueOf(AccordsUi.AMBER));
        return c;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, cv, parent);
                t.setText(getItem(pos) + "  ▾");
                t.setTextColor(AccordsUi.TEXT);
                t.setTextSize(15);
                return t;
            }

            @Override
            public View getDropDownView(int pos, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getDropDownView(pos, cv, parent);
                t.setTextColor(AccordsUi.TEXT);
                t.setBackgroundColor(AccordsUi.CARD);
                t.setPadding(dp(16), dp(12), dp(16), dp(12));
                return t;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setBackground(AccordsUi.solid(0x1AFFFFFF, dp(12)));
        s.setPopupBackgroundDrawable(AccordsUi.solid(AccordsUi.CARD, dp(12)));
        s.setPadding(dp(8), dp(6), dp(8), dp(6));
        return s;
    }

    private void addLabeled(LinearLayout parent, String label, Spinner s) {
        LinearLayout r = AccordsUi.row(this);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(AccordsUi.TEXT);
        t.setTextSize(14);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(s, new LinearLayout.LayoutParams(0, -2, 1f));
        parent.addView(r, AccordsUi.lp(this, -1, -2, 10));
    }

    private void counter(LinearLayout parent, String label, int idx) {
        LinearLayout r = AccordsUi.row(this);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(AccordsUi.TEXT);
        t.setTextSize(14);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        Button minus = AccordsUi.iconButton(this, "−");
        minus.setOnClickListener(v -> changeCount(idx, -1));
        cntLabel[idx] = new TextView(this);
        cntLabel[idx].setText(String.valueOf(cnt[idx]));
        cntLabel[idx].setTextColor(Color.WHITE);
        cntLabel[idx].setTextSize(18);
        cntLabel[idx].setTypeface(Typeface.DEFAULT_BOLD);
        cntLabel[idx].setGravity(Gravity.CENTER);
        Button plus = AccordsUi.iconButton(this, "+");
        plus.setOnClickListener(v -> changeCount(idx, 1));
        r.addView(minus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        r.addView(cntLabel[idx], new LinearLayout.LayoutParams(dp(40), -2));
        r.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        parent.addView(r, AccordsUi.lp(this, -1, -2, 4));
    }

    private void changeCount(int idx, int d) {
        cnt[idx] = Math.max(0, Math.min(50, cnt[idx] + d));
        cntLabel[idx].setText(String.valueOf(cnt[idx]));
        updateChoirTotal();
    }

    private void updateChoirTotal() {
        int total = cnt[0] + cnt[1] + cnt[2];
        choirTotal.setText(total == 0 ? "Pas de chœur"
                : "Chœur de " + total + " personne" + (total > 1 ? "s" : "")
                + " : " + cnt[0] + " femme(s), " + cnt[1] + " homme(s), " + cnt[2] + " enfant(s)");
    }

    private void updateTempoLabel() {
        tempoLabel.setText("Tempo : " + (50 + tempo.getProgress()) + " battements par minute");
    }

    // ------------------------------------------------------------------ Voix de synthèse

    @Override
    public void onInit(int st) {
        if (st == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(Locale.FRENCH);
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
        }
    }

    /** Découpe le texte : une ligne = une phrase ; les lignes trop longues sont coupées aux mots. */
    private List<String> splitLines(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            while (s.length() > 55) {
                int cut = s.lastIndexOf(' ', 45);
                if (cut < 15) cut = s.indexOf(' ', 45);
                if (cut < 0) break;
                out.add(s.substring(0, cut).trim());
                s = s.substring(cut).trim();
            }
            if (!s.isEmpty()) out.add(s);
            if (out.size() >= 60) break;
        }
        return out;
    }

    /** Transforme le texte en suite de phrases chantées et de solos. */
    private List<Item> buildItems(String text) {
        int choice = spSolo.getSelectedItemPosition();
        int soloBars = SOLO_BARS_N[spSoloBars.getSelectedItemPosition()];
        String[] raws = text.split("\\r?\\n");

        boolean markers = false;
        for (String r : raws) if (isMarker(r.trim())) markers = true;

        List<Item> items = new ArrayList<>();
        Item lastLyric = null;
        boolean firstDone = false;
        int lyricCount = 0;
        for (String raw : raws) {
            String s = raw.trim();
            if (s.isEmpty()) {
                if (lastLyric != null) {
                    lastLyric.end = true;
                    lastLyric = null;
                    if (!markers && !firstDone) { addDefaultSolos(items, choice, soloBars); firstDone = true; }
                }
            } else if (isMarker(s)) {
                if (lastLyric != null) { lastLyric.end = true; lastLyric = null; }
                String low = s.toLowerCase(Locale.ROOT);
                int kind = (low.contains("batterie") || low.contains("drum")) ? 2 : 1;
                int bars = soloBars;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(low);
                if (m.find()) bars = Math.max(1, Math.min(16, Integer.parseInt(m.group(1))));
                items.add(new Item(kind, "", bars));
            } else {
                for (String part : splitLines(s)) {
                    if (lyricCount >= 60) break;
                    lastLyric = new Item(0, part, BARS_PER_LINE);
                    items.add(lastLyric);
                    lyricCount++;
                }
            }
        }
        if (lastLyric != null) {
            lastLyric.end = true;
            if (!markers && !firstDone) addDefaultSolos(items, choice, soloBars);
        }
        return items;
    }

    private boolean isMarker(String s) {
        return s.length() > 2 && s.startsWith("[") && s.endsWith("]");
    }

    private void addDefaultSolos(List<Item> items, int choice, int bars) {
        if (choice == 1 || choice == 3) items.add(new Item(1, "", bars));
        if (choice == 2 || choice == 3) items.add(new Item(2, "", bars));
    }

    // ------------------------------------------------------------------ Création + lecture

    private void generate() {
        String text = lyrics.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Écris d'abord un texte", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<Item> items = buildItems(text);

        final SongSynth.Params p = new SongSynth.Params();
        p.root = spKey.getSelectedItemPosition();
        p.minor = spMode.getSelectedItemPosition() == 1;
        String[] sg = SIGS[spSig.getSelectedItemPosition()].split("/");
        p.beats = Integer.parseInt(sg[0]);
        p.unit = Integer.parseInt(sg[1]);
        p.style = SongSynth.STYLES[spStyle.getSelectedItemPosition()];
        p.bpm = 50 + tempo.getProgress();
        p.piano = cbPiano.isChecked();
        p.bass = cbBass.isChecked();
        p.drums = cbDrums.isChecked();
        p.strings = cbStrings.isChecked();
        p.women = cnt[0];
        p.men = cnt[1];
        p.children = cnt[2];
        int total = 1; // mesure d'introduction
        for (Item it : items) total += it.bars;
        p.bars = total + 1; // + mesure de fin
        p.barKind = new int[p.bars];
        p.fill = new boolean[p.bars];
        int bi = 1;
        for (int k = 0; k < items.size(); k++) {
            Item it = items.get(k);
            for (int j = 0; j < it.bars; j++) p.barKind[bi + j] = it.kind;
            boolean nextDrumSolo = k + 1 < items.size() && items.get(k + 1).kind == 2;
            if (it.kind == 0 && it.end && cbFill.isChecked() && !nextDrumSolo) p.fill[bi + it.bars - 1] = true;
            bi += it.bars;
        }
        if (!(p.piano || p.bass || p.drums || p.strings) && p.women + p.men + p.children == 0) {
            Toast.makeText(this, "Choisis au moins un instrument ou un chœur", Toast.LENGTH_SHORT).show();
            return;
        }
        if (items.isEmpty()) {
            Toast.makeText(this, "Écris d'abord un texte", Toast.LENGTH_SHORT).show();
            return;
        }
        final double barSec = p.beats * (4.0 / p.unit) * 60.0 / p.bpm;
        final float pitch = VOICE_PITCH[spVoice.getSelectedItemPosition()];

        stopAll(null);
        bGo.setEnabled(false);
        status.setText("Création de la musique… (cela peut prendre un moment)");
        lineNow.setText("");
        new Thread(() -> {
            short[] pcm;
            try {
                pcm = SongSynth.render(p);
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    bGo.setEnabled(true);
                    status.setText("Erreur : " + e.getMessage());
                });
                return;
            }
            final short[] result = pcm;
            runOnUiThread(() -> {
                bGo.setEnabled(true);
                if (isFinishing()) return;
                lastPcm = result;
                bSave.setEnabled(true);
                play(result, items, barSec, pitch);
            });
        }).start();
    }

    private void play(short[] pcm, List<Item> items, double barSec, float pitch) {
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SongSynth.SR)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            track.write(pcm, 0, pcm.length);
            track.play();
        } catch (Exception e) {
            status.setText("Lecture impossible : " + e.getMessage());
            return;
        }
        status.setText(ttsReady ? "En lecture…" : "En lecture (voix de synthèse française indisponible : musique seule)");

        int bar = 1;
        for (Item it : items) {
            final Item item = it;
            final double lineSec = it.bars * barSec;
            long delay = (long) (bar * barSec * 1000);
            bar += it.bars;
            h.postDelayed(() -> {
                if (item.kind != 0) {
                    lineNow.setText(item.kind == 1 ? "♪ Solo de piano" : "♪ Solo de batterie");
                    if (tts != null) tts.stop();
                    return;
                }
                lineNow.setText(item.text);
                if (!ttsReady) return;
                // débit calé sur la durée de la phrase
                float rate = (float) Math.max(0.6, Math.min(1.6, item.text.length() / 13.0 / (lineSec * 0.85)));
                tts.setPitch(pitch);
                tts.setSpeechRate(rate);
                tts.speak(item.text, TextToSpeech.QUEUE_FLUSH, null, "ligne");
            }, delay);
        }
        h.postDelayed(() -> {
            lineNow.setText("");
            status.setText("Terminé");
        }, (long) (pcm.length * 1000L / SongSynth.SR));
    }

    private void stopAll(String msg) {
        h.removeCallbacksAndMessages(null);
        if (tts != null) tts.stop();
        if (track != null) {
            try { track.stop(); } catch (Exception ignored) { }
            try { track.release(); } catch (Exception ignored) { }
            track = null;
        }
        if (msg != null) {
            status.setText(msg);
            lineNow.setText("");
        }
    }

    // ------------------------------------------------------------------ Enregistrement

    private void saveWav() {
        if (lastPcm == null) return;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/wav");
        i.putExtra(Intent.EXTRA_TITLE, "ma-chanson-instrumental.wav");
        startActivityForResult(i, SAVE_WAV);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != SAVE_WAV || res != RESULT_OK || data == null || data.getData() == null || lastPcm == null) return;
        try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
            SongSynth.writeWav(os, lastPcm);
            Toast.makeText(this, "Instrumental enregistré (sans la voix)", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Enregistrement impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAll("Arrêté");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAll(null);
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
    }
}
