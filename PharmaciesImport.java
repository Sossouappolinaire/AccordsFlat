package bj.radio;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Lecture d'un fichier de pharmacies (Excel .xlsx ou CSV) pour l'import en lot (section21_import_pharmacies.sql).
 * Colonnes (ordre libre si la première ligne contient les titres, sinon ordre ci-dessous) :
 * departement ; commune ; localite (ville ou village) ; nom ; adresse ; telephone ; garde_debut ; garde_fin
 * Les deux dernières colonnes sont facultatives. Pas d'Android ici : uniquement du Java simple.
 */
final class PharmaciesImport {
    static final int MAX_LIGNES = 2000;
    private static final int MAX_OCTETS = 8 * 1024 * 1024;

    static final class Ligne {
        int numero;                 // numéro de la ligne dans le fichier (la 1re ligne = 1)
        String departement = "", commune = "", localite = "", nom = "", adresse = "", telephone = "";
        String debut = "", fin = "";   // yyyy-MM-dd ou vide
    }

    static final class Resultat {
        final List<Ligne> valides = new ArrayList<>();
        final List<String> erreurs = new ArrayList<>();
        int lues = 0;               // lignes non vides lues dans le fichier
    }

    private PharmaciesImport() { }

    // ------------------------------------------------------------------ point d'entrée

    /** @param communes département → communes officielles (sert à corriger les accents / majuscules). */
    static Resultat lire(InputStream in, String nomFichier, Map<String, String[]> communes) throws Exception {
        byte[] b = tout(in);
        boolean zip = b.length > 3 && b[0] == 'P' && b[1] == 'K';
        String nom = nomFichier == null ? "" : nomFichier.toLowerCase(Locale.ROOT);
        if (nom.endsWith(".xls") && !zip) throw new Exception("Ancien format .xls non lu : enregistrez le fichier en .xlsx ou en .csv.");
        List<List<String>> rows = zip ? lireXlsx(b) : lireCsv(b);
        return analyser(rows, communes);
    }

    private static byte[] tout(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] t = new byte[16384];
        int n;
        while ((n = in.read(t)) > 0) {
            o.write(t, 0, n);
            if (o.size() > MAX_OCTETS) throw new Exception("Fichier trop gros (8 Mo au maximum).");
        }
        return o.toByteArray();
    }

    // ------------------------------------------------------------------ analyse des lignes

    /** Minuscules, sans accents, sans espace ni ponctuation : « Ville / Village » → « villevillage ». */
    static String cle(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static int colonne(String titre) {
        String k = cle(titre);
        switch (k) {
            case "departement": case "dep": return 0;
            case "commune": return 1;
            case "localite": case "ville": case "village": case "villevillage": case "villeouvillage": case "quartier": return 2;
            case "nom": case "pharmacie": case "nomdelapharmacie": case "nompharmacie": return 3;
            case "adresse": case "repere": case "adresserepere": return 4;
            case "telephone": case "tel": case "contact": return 5;
            case "gardedebut": case "debutgarde": case "debut": case "gardedu": return 6;
            case "gardefin": case "fingarde": case "fin": case "gardeau": return 7;
            default: return -1;
        }
    }

    private static Resultat analyser(List<List<String>> rows, Map<String, String[]> communes) throws Exception {
        Resultat res = new Resultat();
        if (rows.isEmpty()) throw new Exception("Le fichier est vide.");

        int[] map = {0, 1, 2, 3, 4, 5, 6, 7};          // ordre par défaut
        int premiere = 0;
        List<String> tete = rows.get(0);
        int trouves = 0;
        int[] m2 = {-1, -1, -1, -1, -1, -1, -1, -1};
        for (int j = 0; j < tete.size(); j++) {
            int c = colonne(tete.get(j));
            if (c >= 0 && m2[c] < 0) { m2[c] = j; trouves++; }
        }
        if (trouves >= 3 && m2[0] >= 0 && m2[3] >= 0) {   // titres reconnus : colonnes dans l'ordre du fichier
            map = m2;
            premiere = 1;
        }

        Map<String, String> deps = new HashMap<>();
        Map<String, String> coms = new HashMap<>();       // « departement|commune » normalisés → commune officielle
        for (Map.Entry<String, String[]> e : communes.entrySet()) {
            deps.put(cle(e.getKey()), e.getKey());
            for (String c : e.getValue()) coms.put(cle(e.getKey()) + "|" + cle(c), c);
        }

        for (int i = premiere; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            Ligne l = new Ligne();
            l.numero = i + 1;
            l.departement = champ(r, map[0]);
            l.commune = champ(r, map[1]);
            l.localite = champ(r, map[2]);
            l.nom = champ(r, map[3]);
            l.adresse = champ(r, map[4]);
            l.telephone = champ(r, map[5]);
            String d = champ(r, map[6]), f = champ(r, map[7]);
            if (l.departement.isEmpty() && l.commune.isEmpty() && l.nom.isEmpty() && l.localite.isEmpty()
                    && l.adresse.isEmpty() && l.telephone.isEmpty()) continue;          // ligne vide
            res.lues++;
            if (res.lues > MAX_LIGNES) throw new Exception("Trop de lignes : " + MAX_LIGNES + " pharmacies au maximum par fichier.");

            String dep = deps.get(cle(l.departement));
            if (dep == null) { res.erreurs.add("Ligne " + l.numero + " : département inconnu « " + l.departement + " »"); continue; }
            l.departement = dep;
            if (l.commune.isEmpty()) { res.erreurs.add("Ligne " + l.numero + " : commune manquante"); continue; }
            String off = coms.get(cle(dep) + "|" + cle(l.commune));
            if (off != null) l.commune = off;
            if (l.nom.isEmpty()) { res.erreurs.add("Ligne " + l.numero + " : nom de la pharmacie manquant"); continue; }
            if (l.commune.length() > 80 || l.localite.length() > 80 || l.nom.length() > 120
                    || l.adresse.length() > 200 || l.telephone.length() > 40) {
                res.erreurs.add("Ligne " + l.numero + " : texte trop long"); continue;
            }
            String di = date(d), fi = date(f);
            if (di == null || fi == null) { res.erreurs.add("Ligne " + l.numero + " : date de garde illisible (« " + (di == null ? d : f) + " »)"); continue; }
            if (di.isEmpty() != fi.isEmpty()) { res.erreurs.add("Ligne " + l.numero + " : garde_debut et garde_fin vont ensemble"); continue; }
            if (!di.isEmpty() && fi.compareTo(di) < 0) { res.erreurs.add("Ligne " + l.numero + " : la fin de garde est avant le début"); continue; }
            l.debut = di;
            l.fin = fi;
            res.valides.add(l);
        }
        if (res.lues == 0) throw new Exception("Aucune pharmacie trouvée dans le fichier.");
        return res;
    }

    private static String champ(List<String> r, int j) {
        if (j < 0 || j >= r.size()) return "";
        String s = r.get(j);
        return s == null ? "" : s.replace('\u00A0', ' ').trim();
    }

    /** Date → « yyyy-MM-dd » ; « » si vide ; null si illisible. Accepte aaaa-mm-jj, jj/mm/aaaa, jj-mm-aaaa et les dates Excel. */
    static String date(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.isEmpty()) return "";
        try {
            if (s.matches("\\d{4}-\\d{1,2}-\\d{1,2}.*")) {
                String[] p = s.substring(0, Math.min(s.length(), 10)).split("-");
                return iso(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2].replaceAll("\\D.*", "")));
            }
            if (s.matches("\\d{1,2}[/.\\-]\\d{1,2}[/.\\-]\\d{2,4}.*")) {
                String[] p = s.split("[/.\\-\\s]");
                int a = Integer.parseInt(p[2]);
                return iso(a < 100 ? 2000 + a : a, Integer.parseInt(p[1]), Integer.parseInt(p[0]));
            }
            if (s.matches("\\d{5}(\\.\\d+)?")) {              // date Excel : nombre de jours depuis 1899-12-30
                long jours = (long) Double.parseDouble(s);
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                return f.format(new Date((jours - 25569L) * 86_400_000L));
            }
        } catch (Exception ignore) { }
        return null;
    }

    private static String iso(int a, int m, int j) {
        if (a < 2000 || a > 2100 || m < 1 || m > 12 || j < 1 || j > 31) return null;
        return String.format(Locale.ROOT, "%04d-%02d-%02d", a, m, j);
    }

    // ------------------------------------------------------------------ CSV

    private static String decoder(byte[] b) {
        int debut = (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) ? 3 : 0;
        try {
            return Charset.forName("UTF-8").newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b, debut, b.length - debut)).toString();
        } catch (CharacterCodingException e) {                  // fichier Excel « CSV » en Windows-1252
            return new String(b, debut, b.length - debut, Charset.forName("windows-1252"));
        }
    }

    static List<List<String>> lireCsv(byte[] b) {
        String t = decoder(b);
        // séparateur : celui qui apparaît le plus dans la première ligne (hors guillemets) ; « ; » en cas d'égalité
        int pv = 0, vi = 0, tab = 0;
        boolean q = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '"') q = !q;
            else if (!q && (c == '\n' || c == '\r')) break;
            else if (!q && c == ';') pv++;
            else if (!q && c == ',') vi++;
            else if (!q && c == '\t') tab++;
        }
        char sep = (pv >= vi && pv >= tab) ? ';' : (vi >= tab ? ',' : '\t');

        List<List<String>> rows = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        q = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < t.length() && t.charAt(i + 1) == '"') { sb.append('"'); i++; }
                    else q = false;
                } else sb.append(c);
            } else if (c == '"') {
                q = true;
            } else if (c == sep) {
                cur.add(sb.toString()); sb.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') i++;
                cur.add(sb.toString()); sb.setLength(0);
                rows.add(cur); cur = new ArrayList<>();
            } else sb.append(c);
        }
        if (sb.length() > 0 || !cur.isEmpty()) { cur.add(sb.toString()); rows.add(cur); }
        return rows;
    }

    // ------------------------------------------------------------------ Excel (.xlsx)

    static List<List<String>> lireXlsx(byte[] b) throws Exception {
        Map<String, byte[]> f = new HashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(b))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String n = e.getName();
                if (e.isDirectory() || !(n.equals("xl/workbook.xml") || n.equals("xl/_rels/workbook.xml.rels")
                        || n.equals("xl/sharedStrings.xml") || (n.startsWith("xl/worksheets/") && n.endsWith(".xml")))) continue;
                f.put(n, tout(z));
            }
        }
        if (f.isEmpty()) throw new Exception("Ce fichier n'est pas un classeur Excel (.xlsx) lisible.");

        // première feuille du classeur
        String feuille = "xl/worksheets/sheet1.xml";
        byte[] wb = f.get("xl/workbook.xml"), rel = f.get("xl/_rels/workbook.xml.rels");
        if (wb != null && rel != null) {
            final String[] rid = {null};
            parser(wb, new DefaultHandler() {
                @Override public void startElement(String u, String l, String q, Attributes a) {
                    if (rid[0] == null && (q.equals("sheet") || q.endsWith(":sheet"))) {
                        String v = a.getValue("r:id");
                        if (v == null) v = a.getValue("id");
                        rid[0] = v;
                    }
                }
            });
            if (rid[0] != null) {
                final String cible = rid[0];
                final String[] cb = {null};
                parser(rel, new DefaultHandler() {
                    @Override public void startElement(String u, String l, String q, Attributes a) {
                        if (q.equals("Relationship") && cible.equals(a.getValue("Id"))) cb[0] = a.getValue("Target");
                    }
                });
                if (cb[0] != null) {
                    String t = cb[0].startsWith("/") ? cb[0].substring(1) : "xl/" + cb[0];
                    if (f.containsKey(t)) feuille = t;
                }
            }
        }
        byte[] sh = f.get(feuille);
        if (sh == null) {                                    // dernier recours : n'importe quelle feuille
            for (Map.Entry<String, byte[]> e : f.entrySet()) if (e.getKey().startsWith("xl/worksheets/")) { sh = e.getValue(); break; }
        }
        if (sh == null) throw new Exception("Aucune feuille trouvée dans le classeur.");

        // textes partagés
        final List<String> textes = new ArrayList<>();
        byte[] ss = f.get("xl/sharedStrings.xml");
        if (ss != null) {
            parser(ss, new DefaultHandler() {
                StringBuilder cur = null;
                boolean dansT = false;
                int phon = 0;
                @Override public void startElement(String u, String l, String q, Attributes a) {
                    if (q.equals("si")) cur = new StringBuilder();
                    else if (q.equals("rPh")) phon++;
                    else if (q.equals("t") && phon == 0) dansT = true;
                }
                @Override public void characters(char[] c, int s, int n) { if (dansT && cur != null) cur.append(c, s, n); }
                @Override public void endElement(String u, String l, String q) {
                    if (q.equals("t")) dansT = false;
                    else if (q.equals("rPh")) phon--;
                    else if (q.equals("si")) { textes.add(cur == null ? "" : cur.toString()); cur = null; }
                }
            });
        }

        // cellules de la feuille
        final List<List<String>> rows = new ArrayList<>();
        parser(sh, new DefaultHandler() {
            List<String> ligne = null;
            String type = "", ref = "";
            StringBuilder val = new StringBuilder();
            boolean dansV = false, dansT = false;
            @Override public void startElement(String u, String l, String q, Attributes a) {
                switch (q) {
                    case "row": ligne = new ArrayList<>(); break;
                    case "c":
                        type = a.getValue("t") == null ? "" : a.getValue("t");
                        ref = a.getValue("r") == null ? "" : a.getValue("r");
                        val.setLength(0);
                        break;
                    case "v": dansV = true; break;
                    case "t": dansT = true; break;        // texte direct (inlineStr)
                    default: break;
                }
            }
            @Override public void characters(char[] c, int s, int n) { if (dansV || dansT) val.append(c, s, n); }
            @Override public void endElement(String u, String l, String q) {
                switch (q) {
                    case "v": dansV = false; break;
                    case "t": dansT = false; break;
                    case "c": {
                        if (ligne == null) break;
                        int col = colonneDe(ref);
                        while (ligne.size() < col) ligne.add("");
                        String v = val.toString();
                        if (type.equals("s")) {
                            try { v = textes.get(Integer.parseInt(v.trim())); } catch (Exception e) { v = ""; }
                        } else if (type.isEmpty() || type.equals("n")) {
                            v = nombre(v);
                        }
                        if (col < ligne.size()) ligne.set(col, v); else ligne.add(v);
                        break;
                    }
                    case "row": if (ligne != null) rows.add(ligne); ligne = null; break;
                    default: break;
                }
            }
        });
        return rows;
    }

    /** « B7 » → 1 ; « AA3 » → 26. */
    private static int colonneDe(String ref) {
        int n = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c < 'A' || c > 'Z') break;
            n = n * 26 + (c - 'A' + 1);
        }
        return Math.max(0, n - 1);
    }

    /** Nombre Excel → texte sans « .0 » ni notation scientifique. */
    private static String nombre(String v) {
        v = v.trim();
        if (v.isEmpty()) return v;
        try {
            if (v.indexOf('E') >= 0 || v.indexOf('e') >= 0) return new BigDecimal(v).toPlainString();
            if (v.endsWith(".0")) return v.substring(0, v.length() - 2);
        } catch (Exception ignore) { }
        return v;
    }

    private static void parser(byte[] xml, DefaultHandler h) throws Exception {
        SAXParserFactory fa = SAXParserFactory.newInstance();
        try { fa.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignore) { }
        try { fa.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignore) { }
        try { fa.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignore) { }
        SAXParser p = fa.newSAXParser();
        p.parse(new InputSource(new ByteArrayInputStream(xml)), h);
    }
}
