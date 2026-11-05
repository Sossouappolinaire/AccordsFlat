package bj.radio;

/** Banque de questions de « Questions pour un champion ». L'identifiant d'une question est sa position (à partir de 1) ; 
 *  la base (table quiz_questions, section22_quiz.sql) connaît le même numéro et la bonne réponse. */
final class QuizBanque {
    private QuizBanque() {}

    /** {catégorie, question, réponse A, B, C, D}. */
    private static final String[][] TEXTES = {
        {"Bénin", "Quelle est la capitale politique du Bénin ?", "Parakou", "Abomey", "Porto-Novo", "Cotonou"},
        {"Bénin", "Quelle est la capitale économique du Bénin ?", "Natitingou", "Cotonou", "Porto-Novo", "Ouidah"},
        {"Bénin", "En quelle année le Bénin a-t-il obtenu son indépendance ?", "1960", "1958", "1963", "1975"},
        {"Bénin", "Quel était l'ancien nom du Bénin ?", "Dahomey", "Haute-Volta", "Togoland", "Soudan français"},
        {"Bénin", "Quelle est la monnaie du Bénin ?", "Le naira", "Le cedi", "Le dalasi", "Le franc CFA"},
        {"Bénin", "Quelle est la langue officielle du Bénin ?", "Le portugais", "Le fon", "Le français", "L'anglais"},
        {"Bénin", "Quelle cité lacustre du Bénin est surnommée la Venise de l'Afrique ?", "Ganvié", "Ouidah", "Grand-Popo", "Savalou"},
        {"Bénin", "Quel pays borde le Bénin à l'est ?", "Le Niger", "Le Burkina Faso", "Le Togo", "Le Nigeria"},
        {"Bénin", "Quel pays borde le Bénin à l'ouest ?", "Le Togo", "Le Nigeria", "La Côte d'Ivoire", "Le Ghana"},
        {"Bénin", "Quelles sont les couleurs du drapeau du Bénin ?", "Vert, jaune et rouge", "Noir, rouge et or", "Bleu, blanc et rouge", "Vert, blanc et orange"},
        {"Bénin", "Quel océan borde le sud du Bénin ?", "L'océan Pacifique", "L'océan Indien", "L'océan Arctique", "L'océan Atlantique"},
        {"Bénin", "Quel parc national du nord du Bénin est célèbre pour ses éléphants et ses lions ?", "L'Ouémé", "Le Mono", "La Pendjari", "La Comoé"},
        {"Bénin", "Quel est le plus long fleuve du Bénin ?", "Le Couffo", "L'Ouémé", "La Pendjari", "Le Mono"},
        {"Bénin", "Quelle ville du Bénin est célèbre pour ses palais royaux classés à l'UNESCO ?", "Parakou", "Abomey", "Kandi", "Lokossa"},
        {"Bénin", "Quelle fête nationale est célébrée chaque 10 janvier au Bénin ?", "La fête du Vodoun", "La fête de l'igname", "Le Gaani", "La Tabaski"},
        {"Bénin", "Comment appelait-on les femmes guerrières du royaume du Dahomey ?", "Les Amazones", "Les Walkyries", "Les Spartiates", "Les Vestales"},
        {"Bénin", "Quel roi du Dahomey a résisté à la France à la fin du dix-neuvième siècle ?", "Ghézo", "Tegbessou", "Agadja", "Béhanzin"},
        {"Bénin", "Quelle chanteuse internationale est originaire du Bénin ?", "Miriam Makeba", "Céline Dion", "Aya Nakamura", "Angélique Kidjo"},
        {"Afrique", "Quel est le plus long fleuve d'Afrique ?", "Le Niger", "Le Congo", "Le Zambèze", "Le Nil"},
        {"Afrique", "Quel est le plus grand désert chaud du monde ?", "Le Kalahari", "Le Sahara", "Le Namib", "Le Gobi"},
        {"Afrique", "Quel est le plus haut sommet d'Afrique ?", "Le Kilimandjaro", "Le mont Stanley", "Le mont Cameroun", "Le mont Kenya"},
        {"Afrique", "Quelle est la capitale du Nigeria ?", "Kano", "Abuja", "Ibadan", "Lagos"},
        {"Afrique", "Quelle est la capitale politique de la Côte d'Ivoire ?", "Abidjan", "Yamoussoukro", "San-Pédro", "Bouaké"},
        {"Afrique", "Quelle est la capitale du Ghana ?", "Takoradi", "Accra", "Kumasi", "Tamale"},
        {"Afrique", "Quelle est la capitale du Sénégal ?", "Touba", "Thiès", "Dakar", "Saint-Louis"},
        {"Afrique", "Quelle est la capitale du Togo ?", "Lomé", "Kara", "Atakpamé", "Sokodé"},
        {"Afrique", "Quelle est la capitale du Mali ?", "Tombouctou", "Mopti", "Gao", "Bamako"},
        {"Afrique", "Quelle est la capitale de l'Égypte ?", "Le Caire", "Assouan", "Alexandrie", "Louxor"},
        {"Afrique", "Quelle est la capitale du Maroc ?", "Rabat", "Fès", "Casablanca", "Marrakech"},
        {"Afrique", "Quelle est la capitale du Kenya ?", "Nairobi", "Mombasa", "Nakuru", "Kisumu"},
        {"Afrique", "Quelle est la capitale de l'Éthiopie ?", "Asmara", "Nairobi", "Addis-Abeba", "Khartoum"},
        {"Afrique", "Quel est le pays le plus peuplé d'Afrique ?", "L'Éthiopie", "L'Égypte", "La République démocratique du Congo", "Le Nigeria"},
        {"Afrique", "Quel est le plus grand pays d'Afrique par sa superficie ?", "Le Soudan", "La Libye", "L'Algérie", "La République démocratique du Congo"},
        {"Afrique", "En quelle année Nelson Mandela est-il devenu président de l'Afrique du Sud ?", "1999", "1994", "1990", "1985"},
        {"Afrique", "Quelle organisation, créée en 2002, regroupe les États africains ?", "L'OPEP", "La CEDEAO", "La SADC", "L'Union africaine"},
        {"Afrique", "Quel pays africain a battu l'armée italienne à Adoua en 1896 ?", "Le Soudan", "L'Éthiopie", "L'Égypte", "La Libye"},
        {"Afrique", "Quelle est la plus grande île d'Afrique ?", "Madagascar", "Zanzibar", "Bioko", "Maurice"},
        {"Afrique", "Quel est le plus grand lac d'Afrique ?", "Le lac Victoria", "Le lac Tchad", "Le lac Tanganyika", "Le lac Malawi"},
        {"Afrique", "Sur quel fleuve se trouvent les chutes Victoria ?", "Le Nil", "Le Congo", "Le Niger", "Le Zambèze"},
        {"Afrique", "Qui a été le premier président du Sénégal, poète et écrivain ?", "Léopold Sédar Senghor", "Mamadou Dia", "Abdou Diouf", "Abdoulaye Wade"},
        {"Afrique", "Qui a dirigé le Ghana vers l'indépendance en 1957 ?", "Kwame Nkrumah", "Félix Houphouët-Boigny", "Patrice Lumumba", "Léopold Senghor"},
        {"Afrique", "Dans quel pays se trouve la ville de Tombouctou ?", "Le Mali", "Le Tchad", "La Mauritanie", "Le Niger"},
        {"Afrique", "Quel instrument à cordes est joué par les griots d'Afrique de l'Ouest ?", "Le balafon", "Le djembé", "La kora", "La flûte peule"},
        {"Afrique", "Qui est l'auteure du roman « Une si longue lettre » ?", "Chinua Achebe", "Aminata Sow Fall", "Mariama Bâ", "Calixthe Beyala"},
        {"Monde", "Quelle est la capitale de l'Australie ?", "Perth", "Canberra", "Sydney", "Melbourne"},
        {"Monde", "Quelle est la capitale du Canada ?", "Montréal", "Ottawa", "Vancouver", "Toronto"},
        {"Monde", "Quelle est la capitale du Brésil ?", "Salvador", "Brasilia", "São Paulo", "Rio de Janeiro"},
        {"Monde", "Quelle est la capitale du Japon ?", "Tokyo", "Nagoya", "Kyoto", "Osaka"},
        {"Monde", "Quelle est la capitale de la Turquie ?", "Izmir", "Ankara", "Bursa", "Istanbul"},
        {"Monde", "Quel est le plus grand océan du monde ?", "L'océan Indien", "L'océan Arctique", "L'océan Atlantique", "L'océan Pacifique"},
        {"Monde", "Quel est le plus long fleuve d'Europe ?", "Le Danube", "La Seine", "Le Rhin", "La Volga"},
        {"Monde", "Quelle est la plus haute montagne du monde ?", "Le Kilimandjaro", "Le mont Blanc", "Le K2", "L'Everest"},
        {"Monde", "Quel est le plus grand pays du monde par sa superficie ?", "La Russie", "Les États-Unis", "Le Canada", "La Chine"},
        {"Monde", "Quel détroit sépare l'Europe de l'Afrique, à l'entrée de la Méditerranée ?", "Le Bosphore", "Le détroit de Gibraltar", "Le détroit d'Ormuz", "Le détroit de Magellan"},
        {"Monde", "Quel est le plus petit pays du monde ?", "Monaco", "Saint-Marin", "Le Vatican", "Le Liechtenstein"},
        {"Monde", "Dans quel pays se trouve le Taj Mahal ?", "Le Pakistan", "Le Bangladesh", "L'Inde", "Le Népal"},
        {"Monde", "Quel pays est surnommé le pays du Soleil-Levant ?", "Le Japon", "La Thaïlande", "La Chine", "La Corée du Sud"},
        {"Monde", "Quelle ville est surnommée la Ville Lumière ?", "Lyon", "Londres", "Paris", "Rome"},
        {"Monde", "Dans quel pays a lieu le célèbre carnaval de Rio ?", "L'Argentine", "Cuba", "Le Brésil", "Le Mexique"},
        {"Histoire", "En quelle année a eu lieu la Révolution française ?", "1789", "1815", "1769", "1799"},
        {"Histoire", "Qui a été le premier président des États-Unis ?", "John Adams", "George Washington", "Thomas Jefferson", "Abraham Lincoln"},
        {"Histoire", "En quelle année a commencé la Première Guerre mondiale ?", "1918", "1914", "1939", "1905"},
        {"Histoire", "En quelle année s'est terminée la Seconde Guerre mondiale ?", "1946", "1944", "1939", "1945"},
        {"Histoire", "Quel empereur français a été vaincu à Waterloo ?", "Napoléon Ier", "Louis XIV", "Napoléon III", "Charlemagne"},
        {"Histoire", "Quel peuple a construit les pyramides de Gizeh ?", "Les Grecs", "Les Mayas", "Les Égyptiens", "Les Romains"},
        {"Histoire", "En quelle année l'homme a-t-il marché sur la Lune pour la première fois ?", "1972", "1957", "1969", "1961"},
        {"Histoire", "Quel navigateur est arrivé en Amérique en 1492 ?", "Magellan", "Marco Polo", "Christophe Colomb", "Vasco de Gama"},
        {"Histoire", "Quel mur, symbole de la division de l'Allemagne, est tombé en 1989 ?", "Le mur d'Hadrien", "Le mur de Berlin", "La Grande Muraille", "Le mur des Lamentations"},
        {"Histoire", "Quel roi de France était surnommé le Roi-Soleil ?", "Louis XIV", "Henri IV", "Louis XVI", "Charles V"},
        {"Histoire", "Quel pays a offert la statue de la Liberté aux États-Unis ?", "L'Italie", "La France", "L'Espagne", "L'Angleterre"},
        {"Histoire", "Quel paquebot a coulé en 1912 après avoir heurté un iceberg ?", "Le Lusitania", "Le Bismarck", "Le Queen Mary", "Le Titanic"},
        {"Sciences", "Quelle planète est la plus proche du Soleil ?", "Vénus", "Mercure", "Mars", "La Terre"},
        {"Sciences", "Quelle planète est surnommée la planète rouge ?", "Jupiter", "Saturne", "Vénus", "Mars"},
        {"Sciences", "Quelle est la plus grande planète du système solaire ?", "Neptune", "Uranus", "Saturne", "Jupiter"},
        {"Sciences", "Quel gaz les plantes absorbent-elles pour la photosynthèse ?", "L'azote", "Le dioxyde de carbone", "L'oxygène", "L'hélium"},
        {"Sciences", "Combien d'os compte le corps d'un adulte ?", "206", "306", "186", "256"},
        {"Sciences", "Quelle est la formule chimique de l'eau ?", "O2", "CO2", "H2O", "NaCl"},
        {"Sciences", "Quel organe pompe le sang dans le corps ?", "Le rein", "Le foie", "Le cœur", "Les poumons"},
        {"Sciences", "À quelle température l'eau bout-elle au niveau de la mer ?", "110 degrés", "120 degrés", "100 degrés", "90 degrés"},
        {"Sciences", "Quel est le plus grand animal terrestre ?", "L'hippopotame", "La girafe", "Le rhinocéros", "L'éléphant d'Afrique"},
        {"Sciences", "Quel est l'animal terrestre le plus rapide ?", "Le guépard", "Le cheval", "Le lion", "L'antilope"},
        {"Sciences", "Quel est le plus grand mammifère du monde ?", "La baleine bleue", "L'éléphant", "Le requin-baleine", "La girafe"},
        {"Sciences", "Combien de pattes a une araignée ?", "Quatre", "Six", "Huit", "Dix"},
        {"Sciences", "Qui a formulé la théorie de la relativité ?", "Albert Einstein", "Galilée", "Isaac Newton", "Nikola Tesla"},
        {"Sciences", "Quel élément chimique a pour symbole Au ?", "L'argent", "Le cuivre", "L'or", "L'aluminium"},
        {"Sciences", "Combien de planètes compte le système solaire ?", "Neuf", "Dix", "Sept", "Huit"},
        {"Sciences", "Comment s'appelle l'unique satellite naturel de la Terre ?", "La Lune", "Titan", "Phobos", "Europe"},
        {"Sciences", "Quelle vitamine la peau fabrique-t-elle grâce au soleil ?", "La vitamine A", "La vitamine D", "La vitamine B12", "La vitamine C"},
        {"Sciences", "Quelle est la vitesse de la lumière, environ ?", "3 millions de km par seconde", "150 000 km par seconde", "300 000 km par seconde", "30 000 km par seconde"},
        {"Sciences", "Quel métal est liquide à température ambiante ?", "Le mercure", "L'étain", "Le plomb", "Le fer"},
        {"Sciences", "Quel instrument mesure la température ?", "Le baromètre", "Le thermomètre", "L'anémomètre", "L'hygromètre"},
        {"Sciences", "Quelle partie de la plante absorbe l'eau du sol ?", "La tige", "Les fleurs", "Les feuilles", "Les racines"},
        {"Sciences", "Quel est le plus grand organe du corps humain ?", "Le cerveau", "La peau", "Le cœur", "Le foie"},
        {"Sport", "Combien de joueurs y a-t-il sur le terrain dans une équipe de football ?", "Dix", "Douze", "Neuf", "Onze"},
        {"Sport", "Quel pays a remporté la Coupe du monde de football en 2018 ?", "La France", "Le Brésil", "La Croatie", "La Belgique"},
        {"Sport", "Quel pays a remporté la Coupe du monde de football en 2022 ?", "La France", "Le Maroc", "La Croatie", "L'Argentine"},
        {"Sport", "Quel pays africain a atteint les demi-finales de la Coupe du monde 2022 ?", "Le Sénégal", "Le Cameroun", "Le Ghana", "Le Maroc"},
        {"Sport", "Quel pays a remporté le plus de Coupes du monde de football ?", "Le Brésil", "L'Allemagne", "L'Argentine", "L'Italie"},
        {"Sport", "Dans quelle ville ont eu lieu les Jeux olympiques d'été de 2024 ?", "Tokyo", "Paris", "Londres", "Los Angeles"},
        {"Sport", "Combien de joueurs d'une équipe de basket-ball sont sur le terrain ?", "Six", "Sept", "Onze", "Cinq"},
        {"Sport", "Quel sport pratique-t-on à Roland-Garros ?", "Le golf", "Le cyclisme", "Le rugby", "Le tennis"},
        {"Sport", "Combien de minutes dure un match de football, sans prolongations ?", "90 minutes", "100 minutes", "120 minutes", "80 minutes"},
        {"Sport", "Quel footballeur est surnommé CR7 ?", "Kylian Mbappé", "Cristiano Ronaldo", "Lionel Messi", "Neymar"},
        {"Sport", "Quelle est la distance d'un marathon ?", "40 km", "21 km", "50 km", "42,195 km"},
        {"Sport", "De quelle couleur est le maillot du leader du Tour de France ?", "Vert", "Jaune", "Blanc", "Rouge"},
        {"Sport", "Quel pays a remporté la Coupe d'Afrique des nations disputée début 2024 ?", "La République démocratique du Congo", "Le Nigeria", "La Côte d'Ivoire", "Le Sénégal"},
        {"Sport", "Quel pays a remporté la Coupe d'Afrique des nations disputée début 2022 ?", "Le Cameroun", "L'Égypte", "Le Sénégal", "L'Algérie"},
        {"Sport", "Combien d'anneaux compte le drapeau olympique ?", "Six", "Cinq", "Quatre", "Sept"},
        {"Sport", "Combien de joueurs d'une équipe de volley-ball sont sur le terrain ?", "Six", "Cinq", "Neuf", "Sept"},
        {"Langue", "Quel est le féminin du mot acteur ?", "Actresse", "Actrice", "Acteure", "Actrix"},
        {"Langue", "Quel est le pluriel du mot cheval ?", "Chevaus", "Chevaux", "Chevals", "Chevalles"},
        {"Langue", "Combien de lettres compte l'alphabet français ?", "25", "26", "28", "24"},
        {"Langue", "Comment appelle-t-on un mot de sens contraire à un autre ?", "Un homonyme", "Un acronyme", "Un synonyme", "Un antonyme"},
        {"Culture", "Qui a écrit « Les Misérables » ?", "Balzac", "Molière", "Émile Zola", "Victor Hugo"},
        {"Culture", "Qui a peint la Joconde ?", "Raphaël", "Michel-Ange", "Picasso", "Léonard de Vinci"},
        {"Culture", "Qui a écrit « Le Petit Prince » ?", "Albert Camus", "Antoine de Saint-Exupéry", "Jules Verne", "Victor Hugo"},
        {"Culture", "Combien de notes compte la gamme de musique ?", "Cinq", "Douze", "Sept", "Huit"},
        {"Culture", "Que signifie le sigle W W W sur internet ?", "West Wide Web", "Word Wide Window", "World Wide Web", "World Web Wire"},
        {"Culture", "Quelle couleur obtient-on en mélangeant du bleu et du jaune ?", "L'orange", "Le vert", "Le marron", "Le violet"},
        {"Culture", "Combien font douze fois douze ?", "124", "144", "154", "122"},
        {"Culture", "Quelle est la racine carrée de quatre-vingt-un ?", "Onze", "Neuf", "Sept", "Huit"},
        {"Culture", "Combien de côtés a un hexagone ?", "Six", "Huit", "Cinq", "Sept"},
        {"Culture", "Combien de jours compte une année bissextile ?", "367", "365", "364", "366"},
        {"Culture", "Quelle est la monnaie du Nigeria ?", "Le cedi", "Le rand", "Le shilling", "Le naira"},
        {"Culture", "Quelle est la monnaie du Ghana ?", "Le dalasi", "Le cedi", "Le franc CFA", "Le naira"},
        {"Culture", "Quelle est la monnaie du Japon ?", "Le yuan", "Le ringgit", "Le won", "Le yen"},
    };

    /** Index (0 à 3) de la bonne réponse de chaque question. */
    private static final int[] BONNES = {2, 1, 0, 0, 3, 2, 0, 3, 0, 0, 3, 2, 1, 1, 0, 0, 3, 3, 3, 1, 0, 1, 1, 1, 2, 0, 3, 0, 0, 0, 2, 3, 2, 1, 3, 1, 0, 0, 3, 0, 0, 0, 2, 2, 1, 1, 1, 0, 1, 3, 3, 3, 0, 1, 2, 2, 0, 2, 2, 0, 1, 1, 3, 0, 2, 2, 2, 1, 0, 1, 3, 1, 3, 3, 1, 0, 2, 2, 2, 3, 0, 0, 2, 0, 2, 3, 0, 1, 2, 0, 1, 3, 1, 3, 0, 3, 3, 0, 1, 3, 3, 0, 1, 3, 1, 2, 2, 1, 0, 1, 1, 1, 3, 3, 3, 1, 2, 2, 1, 1, 1, 0, 3, 3, 1, 3};

    static int taille() { return TEXTES.length; }

    /** Vrai si cette question existe dans cette version de l'appli (id à partir de 1). */
    static boolean existe(int id) { return id >= 1 && id <= TEXTES.length; }

    static String categorie(int id) { return TEXTES[id - 1][0]; }

    static String question(int id) { return TEXTES[id - 1][1]; }

    /** Les quatre réponses proposées, dans l'ordre A, B, C, D. */
    static String[] reponses(int id) {
        String[] t = TEXTES[id - 1];
        return new String[]{t[2], t[3], t[4], t[5]};
    }

    /** Bonne réponse (0 à 3), connue de l'appli pour le jeu en solo. */
    static int bonne(int id) { return BONNES[id - 1]; }
}
