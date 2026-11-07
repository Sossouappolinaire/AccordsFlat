package bj.radio;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/**
 * Image d'une question de « Questions pour un champion » : si la question parle d'un pays, on affiche son drapeau en grand ;
 * sinon une grande icône de la catégorie (sciences, calcul, sport…). Rien à télécharger : tout est dessiné par le téléphone.
 */
final class QuizImage {
    private QuizImage() {}

    /** {nom du pays tel qu'écrit dans les questions, code ISO}, du nom le plus long au plus court. */
    private static final String[][] PAYS = {
        {"République démocratique du Congo", "CD"},
        {"São Tomé-et-Príncipe", "ST"},
        {"République du Congo", "CG"},
        {"Émirats arabes unis", "AE"},
        {"Guinée équatoriale", "GQ"},
        {"Nouvelle-Zélande", "NZ"},
        {"Arabie saoudite", "SA"},
        {"Afrique du Sud", "ZA"},
        {"Côte d'Ivoire", "CI"},
        {"Guinée-Bissau", "GW"},
        {"Soudan du Sud", "SS"},
        {"Corée du Nord", "KP"},
        {"Burkina Faso", "BF"},
        {"Sierra Leone", "SL"},
        {"Centrafrique", "CF"},
        {"Corée du Sud", "KR"},
        {"Royaume-Uni", "GB"},
        {"Philippines", "PH"},
        {"Afghanistan", "AF"},
        {"Mauritanie", "MR"},
        {"Mozambique", "MZ"},
        {"Madagascar", "MG"},
        {"Seychelles", "SC"},
        {"Luxembourg", "LU"},
        {"Angleterre", "GB"},
        {"Bangladesh", "BD"},
        {"Kazakhstan", "KZ"},
        {"États-Unis", "US"},
        {"Costa Rica", "CR"},
        {"Allemagne", "DE"},
        {"Slovaquie", "SK"},
        {"Indonésie", "ID"},
        {"Thaïlande", "TH"},
        {"Singapour", "SG"},
        {"Sri Lanka", "LK"},
        {"Australie", "AU"},
        {"Argentine", "AR"},
        {"Venezuela", "VE"},
        {"Cap-Vert", "CV"},
        {"Cameroun", "CM"},
        {"Zimbabwe", "ZW"},
        {"Botswana", "BW"},
        {"Eswatini", "SZ"},
        {"Tanzanie", "TZ"},
        {"Éthiopie", "ET"},
        {"Érythrée", "ER"},
        {"Djibouti", "DJ"},
        {"Belgique", "BE"},
        {"Portugal", "PT"},
        {"Pays-Bas", "NL"},
        {"Danemark", "DK"},
        {"Finlande", "FI"},
        {"Autriche", "AT"},
        {"Tchéquie", "CZ"},
        {"Roumanie", "RO"},
        {"Bulgarie", "BG"},
        {"Pakistan", "PK"},
        {"Malaisie", "MY"},
        {"Cambodge", "KH"},
        {"Jordanie", "JO"},
        {"Mongolie", "MN"},
        {"Jamaïque", "JM"},
        {"Colombie", "CO"},
        {"Équateur", "EC"},
        {"Paraguay", "PY"},
        {"Nigeria", "NG"},
        {"Sénégal", "SN"},
        {"Liberia", "LR"},
        {"Namibie", "NA"},
        {"Lesotho", "LS"},
        {"Maurice", "MU"},
        {"Ouganda", "UG"},
        {"Burundi", "BI"},
        {"Somalie", "SO"},
        {"Tunisie", "TN"},
        {"Algérie", "DZ"},
        {"Comores", "KM"},
        {"Espagne", "ES"},
        {"Irlande", "IE"},
        {"Norvège", "NO"},
        {"Islande", "IS"},
        {"Pologne", "PL"},
        {"Hongrie", "HU"},
        {"Turquie", "TR"},
        {"Ukraine", "UA"},
        {"Croatie", "HR"},
        {"Vietnam", "VN"},
        {"Mexique", "MX"},
        {"Bolivie", "BO"},
        {"Uruguay", "UY"},
        {"Guinée", "GN"},
        {"Gambie", "GM"},
        {"Angola", "AO"},
        {"Zambie", "ZM"},
        {"Malawi", "MW"},
        {"Rwanda", "RW"},
        {"Soudan", "SD"},
        {"Égypte", "EG"},
        {"France", "FR"},
        {"Suisse", "CH"},
        {"Italie", "IT"},
        {"Russie", "RU"},
        {"Serbie", "RS"},
        {"Israël", "IL"},
        {"Canada", "CA"},
        {"Brésil", "BR"},
        {"Panama", "PA"},
        {"Bénin", "BJ"},
        {"Niger", "NE"},
        {"Ghana", "GH"},
        {"Tchad", "TD"},
        {"Gabon", "GA"},
        {"Congo", "CG"},
        {"Kenya", "KE"},
        {"Libye", "LY"},
        {"Maroc", "MA"},
        {"Suède", "SE"},
        {"Grèce", "GR"},
        {"Chine", "CN"},
        {"Japon", "JP"},
        {"Népal", "NP"},
        {"Liban", "LB"},
        {"Syrie", "SY"},
        {"Qatar", "QA"},
        {"Haïti", "HT"},
        {"Chili", "CL"},
        {"Pérou", "PE"},
        {"Togo", "TG"},
        {"Mali", "ML"},
        {"Inde", "IN"},
        {"Iran", "IR"},
        {"Irak", "IQ"},
        {"Cuba", "CU"},
    };

    private static final String[][] CATEGORIES = {
        {"Bénin", "🇧🇯"},
        {"Afrique", "🌍"},
        {"Géographie", "🗺️"},
        {"Sciences", "🔬"},
        {"Calcul", "🧮"},
        {"Anglais", "🇬🇧"},
        {"Littérature", "📚"},
        {"Histoire", "🏛️"},
        {"Sport", "⚽"},
        {"Arts", "🎨"},
        {"Culture générale", "💡"},
        {"Langues", "🗣️"},
        {"Langue", "🗣️"},
        {"Monde", "🌐"},
        {"Technologie", "💻"},
        {"Culture", "🎭"},
        {"Corps humain", "🫀"},
        {"Animaux", "🦁"},
        {"Nature", "🌿"},
        {"Économie", "💰"},
        {"Musique", "🎵"},
    };

    /** Drapeau en emoji à partir du code ISO (deux lettres → deux indicateurs régionaux). */
    static String drapeau(String iso) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < iso.length(); i++) sb.appendCodePoint(0x1F1E6 + (iso.charAt(i) - 'A'));
        return sb.toString();
    }

    private static boolean lettre(char c) { return Character.isLetter(c); }

    /** Drapeau du premier pays cité dans le texte, ou null. */
    static String drapeauDe(String texte) {
        int meilleur = Integer.MAX_VALUE;
        String iso = null;
        for (String[] p : PAYS) {
            int from = 0;
            while (true) {
                int i = texte.indexOf(p[0], from);
                if (i < 0) break;
                boolean avant = i == 0 || !lettre(texte.charAt(i - 1));
                int fin = i + p[0].length();
                boolean apres = fin >= texte.length() || !lettre(texte.charAt(fin)) || (texte.charAt(fin) == 's' && (fin + 1 >= texte.length() || !lettre(texte.charAt(fin + 1))));
                if (avant && apres) {
                    if (i < meilleur) { meilleur = i; iso = p[1]; }
                    break;
                }
                from = i + 1;
            }
        }
        return iso == null ? null : drapeau(iso);
    }

    /** L'image de la question (grand emoji), ou null s'il n'y en a pas. */
    static String pour(String categorie, String question) {
        String d = drapeauDe(question);
        if (d != null) return d;
        for (String[] c : CATEGORIES) if (c[0].equals(categorie)) return c[1];
        return null;
    }

    /** Vue à insérer au-dessus de la question. */
    static View vue(Context c, String categorie, String question) {
        String e = pour(categorie, question);
        if (e == null) return null;
        boolean flag = drapeauDe(question) != null;
        TextView t = new TextView(c);
        t.setText(e);
        t.setTextSize(flag ? 60 : 46);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 2));
        t.setContentDescription(flag ? "Drapeau" : categorie);
        return t;
    }
}
