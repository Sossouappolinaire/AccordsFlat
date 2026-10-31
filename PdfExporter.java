package bj.radio;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Écrit un texte monospace (résumé + grille d'accords) dans un PDF A4. */
public class PdfExporter {

    public static void write(OutputStream os, String title, String text) throws IOException {
        final int W = 595, H = 842, M = 40, MAXCH = 84;
        final float LH = 14f;

        PdfDocument doc = new PdfDocument();
        Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextSize(10);
        Paint head = new Paint(body);
        head.setTextSize(16);
        head.setFakeBoldText(true);

        String t = title.length() > 48 ? title.substring(0, 47) + "…" : title;
        List<String> lines = wrap(text, MAXCH);

        int n = 0;
        PdfDocument.Page pg = null;
        Canvas cv = null;
        float y = 0;
        for (String line : lines) {
            if (pg == null || y > H - M) {
                if (pg != null) doc.finishPage(pg);
                pg = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, ++n).create());
                cv = pg.getCanvas();
                y = M;
                if (n == 1) { cv.drawText(t, M, y + 12, head); y += 34; }
            }
            cv.drawText(line, M, y + 10, body);
            y += LH;
        }
        if (pg != null) doc.finishPage(pg);
        doc.writeTo(os);
        doc.close();
    }

    private static List<String> wrap(String text, int max) {
        List<String> out = new ArrayList<>();
        for (String raw : text.split("\n", -1)) {
            String s = raw;
            while (s.length() > max) {
                int cut = s.lastIndexOf(' ', max);
                if (cut <= 0) cut = max;
                out.add(s.substring(0, cut));
                s = s.substring(cut).trim();
            }
            out.add(s);
        }
        return out;
    }
}
