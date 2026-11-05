package bj.radio;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Données de Stock Massiers. Tout est enregistré dans la base Supabase (fonctions stock_* de
 * section18_stock_massiers.sql) : comptes propriétaires, productions et ventes avec leur auteur.
 * Les méthodes réseau (charger, addEntry, createOwner, connexion) sont à appeler HORS du thread principal ;
 * les autres lisent la copie gardée en mémoire par le dernier chargement.
 */
public class StockDb {
    public static final String PROD = "PROD", SALE = "SALE";
    public static final int PRIX_515 = 1300, PRIX_510 = 1400;

    /** Enregistrements : type, massier, n15, n10, date, auteur — le plus récent d'abord. */
    private static volatile List<String[]> entrees = new ArrayList<>();
    /** Propriétaires : téléphone, nom et prénom, date de création (administrateur seulement). */
    private static volatile List<String[]> proprietaires = new ArrayList<>();
    /** Secrétariat : id, lot, massier, producteur, n15, n10, date, auteur — le plus récent d'abord. */
    private static volatile List<String[]> secretariat = new ArrayList<>();
    /** Comptes secrétaire : téléphone, nom et prénom, massier, date de création (administrateur seulement). */
    private static volatile List<String[]> secretaires = new ArrayList<>();
    /** Massiers enregistrés par l'administrateur (administrateur seulement). */
    private static volatile List<String> massiersRegistre = new ArrayList<>();
    private static volatile boolean pret = false;

    public StockDb(Context c) { }

    public static boolean pret() { return pret; }

    public static void vider() {
        entrees = new ArrayList<>();
        proprietaires = new ArrayList<>();
        secretariat = new ArrayList<>();
        secretaires = new ArrayList<>();
        massiersRegistre = new ArrayList<>();
        pret = false;
    }

    public static String now() {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(new Date());
    }

    // ------------------------------------------------------------------ réseau

    private static JSONObject appel(String fonction, JSONObject corps) {
        String r = Supabase.rest("POST", "rpc/" + fonction, corps.toString(), null);
        if (r == null) return null;
        try { return new JSONObject(r); } catch (Exception e) { return null; }
    }

    private static JSONObject identite() throws Exception {
        return new JSONObject()
                .put("p_tel", StockSession.admin() ? "" : StockSession.phone)
                .put("p_pin", StockSession.admin() ? "" : StockSession.pin);
    }

    /** Message à afficher quand le serveur ne répond pas. */
    public static String erreurReseau() {
        String e = Supabase.derniereErreur;
        if (e != null && e.contains("404"))
            return "La base n'est pas prête : exécutez section18_stock_massiers.sql dans Supabase.";
        return e == null || e.isEmpty() ? "Connexion impossible : vérifiez internet" : e;
    }

    /** Connexion d'un propriétaire (numéro + PIN). Renvoie la réponse du serveur, ou null si le réseau échoue. */
    public static JSONObject connexion(String tel, String pin) {
        try {
            return appel("stock_connexion", new JSONObject().put("p_tel", tel).put("p_pin", pin));
        } catch (Exception e) {
            return null;
        }
    }

    /** Télécharge toutes les données. Renvoie null si tout va bien, sinon le message d'erreur. */
    public static String charger() {
        try {
            JSONObject r = appel("stock_donnees", identite());
            if (r == null) return erreurReseau();
            if (!r.optBoolean("ok")) return r.optString("erreur", "Accès refusé.");

            List<String[]> e = new ArrayList<>();
            JSONArray a = r.optJSONArray("entrees");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String par = o.optString("par", "") + ("ADMIN".equals(o.optString("role")) ? " (Administrateur)" : " (Propriétaire)");
                e.add(new String[]{o.optString("type"), o.optString("massier"),
                        String.valueOf(o.optInt("n15")), String.valueOf(o.optInt("n10")), o.optString("ts"), par,
                        String.valueOf(o.optLong("id"))});
            }
            List<String[]> p = new ArrayList<>();
            JSONArray b = r.optJSONArray("proprietaires");
            for (int i = 0; b != null && i < b.length(); i++) {
                JSONObject o = b.getJSONObject(i);
                p.add(new String[]{o.optString("tel"), o.optString("nom"), o.optString("cree")});
            }
            List<String[]> sc = new ArrayList<>();
            JSONArray c = r.optJSONArray("secretariat");
            for (int i = 0; c != null && i < c.length(); i++) {
                JSONObject o = c.getJSONObject(i);
                String par = o.optString("par", "") + ("ADMIN".equals(o.optString("role")) ? " (Administrateur)" : " (Secrétaire)");
                sc.add(new String[]{String.valueOf(o.optLong("id")), String.valueOf(o.optLong("lot")), o.optString("massier"),
                        o.optString("producteur"), String.valueOf(o.optInt("n15")), String.valueOf(o.optInt("n10")),
                        o.optString("ts"), par});
            }
            List<String[]> se = new ArrayList<>();
            JSONArray d = r.optJSONArray("secretaires");
            for (int i = 0; d != null && i < d.length(); i++) {
                JSONObject o = d.getJSONObject(i);
                se.add(new String[]{o.optString("tel"), o.optString("nom"), o.optString("massier"), o.optString("cree")});
            }
            List<String> mr = new ArrayList<>();
            JSONArray mj = r.optJSONArray("massiers");
            for (int i = 0; mj != null && i < mj.length(); i++) mr.add(mj.getString(i));
            massiersRegistre = mr;
            entrees = e;
            secretariat = sc;
            secretaires = se;
            proprietaires = p;
            pret = true;
            return null;
        } catch (Exception ex) {
            return "Réponse inattendue du serveur.";
        }
    }

    /** Ajoute une production (administrateur) ou une vente (propriétaire). Renvoie null si c'est enregistré, sinon l'erreur. */
    public String addEntry(String type, String massier, int n15, int n10) {
        try {
            JSONObject o = identite().put("p_type", type).put("p_massier", massier).put("p_n15", n15).put("p_n10", n10);
            JSONObject r = appel("stock_ajouter", o);
            if (r == null) return erreurReseau();
            return r.optBoolean("ok") ? null : r.optString("erreur", "Refusé par le serveur.");
        } catch (Exception e) {
            return "Erreur : " + e.getMessage();
        }
    }

    /** Crée un compte propriétaire (administrateur seulement). Renvoie null si c'est créé, sinon l'erreur. */
    public String createOwner(String nom, String numero, String pin) {
        try {
            JSONObject o = new JSONObject().put("p_nom", nom).put("p_numero", numero).put("p_code", pin);
            JSONObject r = appel("stock_creer_proprietaire", o);
            if (r == null) return erreurReseau();
            return r.optBoolean("ok") ? null : r.optString("erreur", "Refusé par le serveur.");
        } catch (Exception e) {
            return "Erreur : " + e.getMessage();
        }
    }

    private static String reponse(String fonction, JSONObject corps) {
        JSONObject r = appel(fonction, corps);
        if (r == null) return erreurReseau();
        return r.optBoolean("ok") ? null : r.optString("erreur", "Refusé par le serveur.");
    }

    /** Crée un compte secrétaire lié à un massier (administrateur seulement). Renvoie null si c'est créé, sinon l'erreur. */
    public String createSecretary(String nom, String numero, String pin, String massier) {
        try {
            return reponse("stock_creer_secretaire", new JSONObject()
                    .put("p_nom", nom).put("p_numero", numero).put("p_code", pin).put("p_massier", massier));
        } catch (Exception e) { return "Erreur : " + e.getMessage(); }
    }

    /** Le secrétaire envoie sa liste complète : tableau de {producteur, n15, n10}. */
    public String secretariatEnvoyer(JSONArray lignes) {
        try { return reponse("stock_secretariat_envoyer", identite().put("p_lignes", lignes)); }
        catch (Exception e) { return "Erreur : " + e.getMessage(); }
    }

    public String secretariatAjouter(String massier, String producteur, int n15, int n10) {
        try {
            return reponse("stock_secretariat_ajouter", new JSONObject().put("p_massier", massier)
                    .put("p_producteur", producteur).put("p_n15", n15).put("p_n10", n10));
        } catch (Exception e) { return "Erreur : " + e.getMessage(); }
    }

    public String secretariatModifier(long id, String producteur, int n15, int n10) {
        try {
            return reponse("stock_secretariat_modifier", new JSONObject().put("p_id", id)
                    .put("p_producteur", producteur).put("p_n15", n15).put("p_n10", n10));
        } catch (Exception e) { return "Erreur : " + e.getMessage(); }
    }

    public String secretariatSupprimer(long id) {
        try { return reponse("stock_secretariat_supprimer", new JSONObject().put("p_id", id)); }
        catch (Exception e) { return "Erreur : " + e.getMessage(); }
    }

    // ------------------------------------------------------------------ lecture (copie en mémoire)

    // ------------------------------------------------------------------ massiers et stock (administrateur)

    /** Ajoute un massier à la liste (administrateur seulement). */
    public String massierAjouter(String nom) {
        try { return reponse("stock_massier_ajouter", new JSONObject().put("p_nom", nom)); }
        catch (Exception e) { return "Erreur : " + e.getMessage(); }
    }

    public String massierSupprimer(String nom) {
        try { return reponse("stock_massier_supprimer", new JSONObject().put("p_nom", nom)); }
        catch (Exception e) { return "Erreur : " + e.getMessage(); }
    }

    /** Modifie une production ou une vente : massier, 5/15, 5/10. */
    public String operationModifier(long id, String massier, int n15, int n10) {
        try {
            return reponse("stock_operation_modifier", new JSONObject().put("p_id", id)
                    .put("p_massier", massier).put("p_n15", n15).put("p_n10", n10));
        } catch (Exception e) { return "Erreur : " + e.getMessage(); }
    }

    public String operationSupprimer(long id) {
        try { return reponse("stock_operation_supprimer", new JSONObject().put("p_id", id)); }
        catch (Exception e) { return "Erreur : " + e.getMessage(); }
    }

    /** Fixe le stock restant sur le terrain d'un massier (produit - vendu). */
    public String stockFixer(String massier, int n15, int n10) {
        try {
            return reponse("stock_fixer", new JSONObject().put("p_massier", massier).put("p_n15", n15).put("p_n10", n10));
        } catch (Exception e) { return "Erreur : " + e.getMessage(); }
    }

    /** Massiers enregistrés par l'administrateur, plus ceux qui ont déjà une production, par ordre alphabétique. */
    public List<String> massiersListe() {
        java.util.TreeMap<String, String> m = new java.util.TreeMap<>();
        for (String n : massiersRegistre) m.put(n.toLowerCase(Locale.ROOT), n);
        for (String n : massiers()) if (!m.containsKey(n.toLowerCase(Locale.ROOT))) m.put(n.toLowerCase(Locale.ROOT), n);
        return new ArrayList<>(m.values());
    }

    /** Liste {téléphone, nom et prénom, massier, date de création} des comptes secrétaire. */
    public List<String[]> secretaires() { return secretaires; }

    /** Listes du secrétariat regroupées par envoi, la plus récente d'abord. Ligne : id, lot, massier, producteur, n15, n10, date, auteur. */
    public List<List<String[]>> lots() {
        java.util.LinkedHashMap<String, List<String[]>> m = new java.util.LinkedHashMap<>();
        for (String[] l : secretariat) {
            List<String[]> g = m.get(l[1]);
            if (g == null) { g = new ArrayList<>(); m.put(l[1], g); }
            g.add(l);
        }
        return new ArrayList<>(m.values());
    }

    /** Liste {téléphone, nom et prénom, date de création} des comptes propriétaires. */
    public List<String[]> owners() { return proprietaires; }

    public int[] totals(String type, String massier) {
        int a = 0, b = 0;
        for (String[] e : entrees) {
            if (!e[0].equals(type)) continue;
            if (massier != null && !e[1].equals(massier)) continue;
            a += Integer.parseInt(e[2]); b += Integer.parseInt(e[3]);
        }
        return new int[]{a, b};
    }

    /** Stock = produit (ajouts cumulés, ancien stock compris) - vendu. */
    public int[] stock(String massier) {
        int[] p = totals(PROD, massier), s = totals(SALE, massier);
        return new int[]{p[0] - s[0], p[1] - s[1]};
    }

    /** Massiers ayant une production, le plus récemment servi d'abord. */
    public List<String> massiers() {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        for (String[] e : entrees) if (PROD.equals(e[0])) s.add(e[1]);
        return new ArrayList<>(s);
    }

    /** Date de la dernière production d'un massier (ou "" s'il n'y en a pas). */
    public String lastProd(String massier) {
        for (String[] e : entrees) if (PROD.equals(e[0]) && e[1].equals(massier)) return e[4];
        return "";
    }

    /** type = PROD, SALE ou null pour tout. Ligne : type, massier, n15, n10, date, auteur. */
    public List<String[]> history(String type) {
        List<String[]> l = new ArrayList<>();
        for (String[] e : entrees) if (type == null || type.equals(e[0])) l.add(e);
        return l;
    }

    /** Tous les ajouts et ventes d'un massier, le plus récent d'abord. */
    public List<String[]> historyBy(String massier) {
        List<String[]> l = new ArrayList<>();
        for (String[] e : entrees) if (e[1].equals(massier)) l.add(e);
        return l;
    }

    public static final long DEUX_JOURS = 2L * 24 * 3600 * 1000;

    public static long parse(String ts) {
        try { return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).parse(ts).getTime(); }
        catch (Exception e) { return 0; }
    }

    /** Vrai si l'ajout date de moins de 2 jours. */
    public static boolean isRecent(String ts) { return System.currentTimeMillis() - parse(ts) <= DEUX_JOURS; }

    /** Lundi 00:00 de la semaine contenant t. */
    public static long weekStart(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        int dow = c.get(Calendar.DAY_OF_WEEK);
        c.add(Calendar.DAY_OF_YEAR, -(dow == Calendar.SUNDAY ? 6 : dow - Calendar.MONDAY));
        return c.getTimeInMillis();
    }

    public static String weekLabel(long start) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(start); c.add(Calendar.DAY_OF_YEAR, 6);
        return "Du " + new SimpleDateFormat("dd/MM", Locale.FRANCE).format(new Date(start)) + " au "
                + new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(c.getTime());
    }

    /** Ventes par semaine, la plus récente d'abord (la semaine en cours est toujours première). Ligne : {début, 5/15, 5/10}. */
    public List<long[]> weeklySales() {
        TreeMap<Long, long[]> m = new TreeMap<>(Collections.<Long>reverseOrder());
        long cur = weekStart(System.currentTimeMillis());
        m.put(cur, new long[]{cur, 0, 0});
        for (String[] e : history(SALE)) {
            long w = weekStart(parse(e[4]));
            long[] a = m.get(w);
            if (a == null) { a = new long[]{w, 0, 0}; m.put(w, a); }
            a[1] += Integer.parseInt(e[2]); a[2] += Integer.parseInt(e[3]);
        }
        return new ArrayList<>(m.values());
    }
}
