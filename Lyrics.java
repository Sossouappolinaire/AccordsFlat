package bj.radio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Paroles horodatées + construction de la feuille « paroles avec accords ». */
public class Lyrics {

    public static class Word {
        public final String text;
        public final double start, end;
        public Word(String text, double start, double end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    /** Une ligne de paroles (suite de mots proches dans le temps). */
    public static class Line {
        public final List<Word> words;
        public final double start, end;
        public final String text;

        Line(List<Word> w) {
            words = w;
            start = w.get(0).start;
            double e = 0;
            StringBuilder sb = new StringBuilder();
            for (Word x : w) {
                e = Math.max(e, x.end);
                sb.append(x.text).append(' ');
            }
            end = e;
            String t = sb.toString().trim();
            text = t.isEmpty() ? t : t.substring(0, 1).toUpperCase(Locale.FRENCH) + t.substring(1);
        }
    }

    private static final double LINE_GAP = 1.0;     // silence qui coupe une ligne (s)
    private static final int LINE_CHARS = 40;       // longueur max d'une ligne
    private static final double INTRO_MIN = 5;      // intro instrumentale
    private static final double PAUSE_MIN = 6;      // pause instrumentale
    private static final double SOLO_MIN = 15;      // solo (pause longue)
    private static final double OUTRO_MIN = 8;      // fin instrumentale

    public static List<Line> lines(List<Word> words) {
        List<Line> lines = new ArrayList<>();
        if (words == null || words.isEmpty()) return lines;
        List<Word> ws = new ArrayList<>(words);
        Collections.sort(ws, (p, q) -> Double.compare(p.start, q.start));

        List<Word> cur = new ArrayList<>();
        int chars = 0;
        double lastEnd = 0;
        for (Word w : ws) {
            boolean cut = !cur.isEmpty() && (w.start - lastEnd > LINE_GAP || chars + w.text.length() + 1 > LINE_CHARS);
            if (cut) { lines.add(new Line(cur)); cur = new ArrayList<>(); chars = 0; }
            cur.add(w);
            chars += w.text.length() + 1;
            lastEnd = Math.max(lastEnd, w.end);
        }
        if (!cur.isEmpty()) lines.add(new Line(cur));
        return lines;
    }

    public static String sheet(List<Word> words, ChordAnalyzer.Analysis a, int semi, String instrument) {
        List<Line> lines = lines(words);
        if (lines.isEmpty()) return "(aucune parole détectée)\n";

        String ins = instrument == null ? "" : instrument.trim();
        StringBuilder out = new StringBuilder();

        // intro instrumentale
        double firstStart = lines.get(0).start;
        if (firstStart - a.t0 >= INTRO_MIN) {
            out.append("[INTRO").append(ins.isEmpty() ? "" : " " + ins).append("] ")
               .append(time(a.t0)).append(" : ")
               .append(ChordAnalyzer.progression(a, a.t0, firstStart, semi)).append("\n\n");
        }

        // lignes de paroles + sections instrumentales entre les lignes
        double prevEnd = -1;
        for (Line line : lines) {
            if (prevEnd >= 0) {
                double gap = line.start - prevEnd;
                if (gap >= SOLO_MIN) {
                    out.append("[SOLO] ").append(time(prevEnd)).append(" : ")
                       .append(ChordAnalyzer.progression(a, prevEnd, line.start, semi)).append("\n\n");
                } else if (gap >= PAUSE_MIN) {
                    out.append("[PAUSE").append(ins.isEmpty() ? "" : " solo " + ins).append("] ")
                       .append(time(prevEnd)).append(" : ")
                       .append(ChordAnalyzer.progression(a, prevEnd, line.start, semi)).append("\n\n");
                }
            }
            appendLine(out, line.words, a, semi);
            prevEnd = Math.max(prevEnd, line.end);
        }

        // fin instrumentale
        if (a.dur - prevEnd >= OUTRO_MIN) {
            out.append("[FIN instrumentale] ").append(time(prevEnd)).append(" : ")
               .append(ChordAnalyzer.progression(a, prevEnd, a.dur, semi)).append("\n\n");
        }
        return out.toString();
    }

    /** Ligne d'accords placée au-dessus du mot où l'accord change, puis la ligne de paroles. */
    private static void appendLine(StringBuilder out, List<Word> line, ChordAnalyzer.Analysis a, int semi) {
        StringBuilder lyr = new StringBuilder(), chd = new StringBuilder();
        String prev = null;
        boolean first = true;
        for (Word w : line) {
            int col = lyr.length();
            String c = ChordAnalyzer.chordAtTime(a, w.start, semi);
            if (c != null && !c.equals("N.C.") && !c.equals(prev)) {
                while (chd.length() < col) chd.append(' ');
                if (chd.length() > col) chd.append(' ');
                chd.append(c);
                prev = c;
            }
            String t = w.text;
            if (first && !t.isEmpty()) t = t.substring(0, 1).toUpperCase(Locale.FRENCH) + t.substring(1);
            first = false;
            lyr.append(t).append(' ');
        }
        if (chd.length() > 0) out.append(chd).append('\n');
        out.append(lyr.toString().trim()).append("\n\n");
    }

    private static String time(double s) {
        int t = (int) s;
        return String.format(Locale.US, "%d:%02d", t / 60, t % 60);
    }
}
