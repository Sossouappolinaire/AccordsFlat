package bj.radio;

/** Qui est connecté à Stock Massiers : l'administrateur (compte de l'appli) ou un propriétaire (numéro + PIN). */
public class StockSession {
    public static String role, phone, nom, pin, massier = "";

    public static boolean admin() { return "ADMIN".equals(role); }

    public static boolean secretaire() { return "SECRETARY".equals(role); }

    /** Ouvre une session Stock et efface les données gardées d'une session précédente. */
    public static void ouvrir(String role, String phone, String nom, String pin) {
        StockSession.role = role;
        StockSession.phone = phone == null ? "" : phone;
        StockSession.nom = nom == null ? "" : nom;
        StockSession.pin = pin == null ? "" : pin;
        StockSession.massier = "";
        StockDb.vider();
    }

    public static void fermer() {
        role = null; phone = null; nom = null; pin = null; massier = "";
        StockDb.vider();
    }
}
