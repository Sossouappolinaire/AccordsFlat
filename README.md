# Accords

Application Android (Java) : tonalité, tempo, notes, accords, grille d'accords, transposition, suivi en lecture, export PDF, paroles (hors ligne avec Vosk ou en ligne via une API compatible OpenAI).

Tous les fichiers sont à la racine du projet (sauf `.github/workflows/build.yml`, imposé par GitHub).

## Compiler sur GitHub
1. Envoie tout le contenu de ce dossier dans un dépôt GitHub.
2. Onglet **Actions** → « Build APK » se lance au push.
3. Télécharge l'APK dans **Artifacts → accords-apk**.

Au premier lancement, l'appli télécharge le modèle Vosk français (≈ 40 Mo) et le garde pour un usage hors ligne.
- Accords en notation française (Do, Ré, Mi, Fa, Sol, La, Si ; dièses ou bémols dans Réglages).
- Paroles horodatées avec les accords placés au-dessus des mots, dans l'écran et dans le PDF.
- Sections instrumentales repérées dans les silences des paroles : [INTRO piano], [SOLO], [PAUSE solo piano], [FIN instrumentale], avec la suite d'accords jouée.
- Barre de progression 0 → 100 % pendant l'analyse et l'extraction des paroles ; paroles affichées en direct pendant la lecture.
- Design sombre (indigo/violet, accent ambre), cartes arrondies, boutons à dégradé avec effet d'appui, animations d'entrée, effet « pop » sur l'accord ; icône adaptative (croches sur dégradé violet).

Tous les fichiers sont à la racine, y compris l'icône et le thème (ic_launcher_*.xml, colors.xml, themes.xml). Le dossier `res/` exigé par Android est créé automatiquement pendant la compilation (voir build.gradle).
