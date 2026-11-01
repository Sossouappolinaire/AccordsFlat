package bj.radio;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Mise en page A4 d'un exposé déjà rédigé, avec une page PDF par entrée fournie. */
public final class ScolairePdfExporter {

    private static final int LARGEUR = 595;
    private static final int HAUTEUR = 842;
    private static final int MARGE = 48;

    private ScolairePdfExporter() {}

    /**
     * pages[0] doit contenir le plan; la dernière entrée doit contenir la conclusion.
     * Le nombre d'entrées doit correspondre au nombre de pages demandé (minimum 3).
     */
    public static void ecrire(OutputStream sortie, String theme, List<String> pages) throws IOException {
        if (sortie == null || pages == null || pages.size() < 3) {
            throw new IllegalArgumentException("Un exposé doit contenir au moins trois pages.");
        }

        PdfDocument document = new PdfDocument();
        Paint titre = new Paint(Paint.ANTI_ALIAS_FLAG);
        titre.setColor(Color.rgb(35, 45, 82));
        titre.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        titre.setTextSize(18f);

        Paint corps = new Paint(Paint.ANTI_ALIAS_FLAG);
        corps.setColor(Color.rgb(38, 42, 52));
        corps.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        corps.setTextSize(11.5f);

        Paint pied = new Paint(Paint.ANTI_ALIAS_FLAG);
        pied.setColor(Color.rgb(100, 108, 125));
        pied.setTextSize(9f);

        try {
            for (int i = 0; i < pages.size(); i++) {
                PdfDocument.Page page = document.startPage(
                        new PdfDocument.PageInfo.Builder(LARGEUR, HAUTEUR, i + 1).create());
                Canvas canvas = page.getCanvas();
                canvas.drawColor(Color.WHITE);

                canvas.drawText(limiter(theme, titre, LARGEUR - MARGE * 2), MARGE, 58, titre);
                canvas.drawLine(MARGE, 74, LARGEUR - MARGE, 74, corps);

                String intertitre;
                if (i == 0) intertitre = "Plan de l’exposé";
                else if (i == pages.size() - 1) intertitre = "Conclusion";
                else intertitre = "Développement — partie " + i;
                canvas.drawText(intertitre, MARGE, 108, titre);

                List<String> lignes = envelopper(pages.get(i), corps, LARGEUR - MARGE * 2);
                float y = 138;
                float hauteurLigne = 17f;
                int capacite = (int) ((HAUTEUR - MARGE - y) / hauteurLigne);
                if (lignes.size() > capacite) {
                    document.finishPage(page);
                    throw new IllegalArgumentException(
                            "Le texte de la page " + (i + 1) + " dépasse la capacité d’une page A4.");
                }
                for (String ligne : lignes) {
                    canvas.drawText(ligne, MARGE, y, corps);
                    y += hauteurLigne;
                }

                canvas.drawText("Page " + (i + 1) + " / " + pages.size(),
                        LARGEUR - MARGE - 70, HAUTEUR - 22, pied);
                document.finishPage(page);
            }
            document.writeTo(sortie);
        } finally {
            document.close();
        }
    }

    private static List<String> envelopper(String texte, Paint pinceau, float largeurMax) {
        List<String> lignes = new ArrayList<>();
        if (texte == null || texte.trim().isEmpty()) return lignes;
        for (String paragraphe : texte.split("\\n", -1)) {
            if (paragraphe.trim().isEmpty()) {
                lignes.add("");
                continue;
            }
            StringBuilder ligne = new StringBuilder();
            for (String mot : paragraphe.trim().split("\\s+")) {
                String essai = ligne.length() == 0 ? mot : ligne + " " + mot;
                if (pinceau.measureText(essai) > largeurMax && ligne.length() > 0) {
                    lignes.add(ligne.toString());
                    ligne.setLength(0);
                    ligne.append(mot);
                } else {
                    if (ligne.length() > 0) ligne.append(' ');
                    ligne.append(mot);
                }
            }
            if (ligne.length() > 0) lignes.add(ligne.toString());
        }
        return lignes;
    }

    private static String limiter(String texte, Paint pinceau, float largeurMax) {
        if (texte == null) return "";
        String resultat = texte.trim();
        while (!resultat.isEmpty() && pinceau.measureText(resultat) > largeurMax) {
            resultat = resultat.substring(0, resultat.length() - 2).trim() + "…";
        }
        return resultat;
    }
}
