# Accords

Application Android (Java) : tonalité, tempo, notes, accords, grille d'accords, transposition, suivi en lecture, export PDF, paroles (hors ligne avec Vosk ou en ligne via une API compatible OpenAI).

Tous les fichiers sont à la racine du projet (sauf `.github/workflows/build.yml`, imposé par GitHub).

## Compiler sur GitHub
1. Envoie tout le contenu de ce dossier dans un dépôt GitHub.
2. Onglet **Actions** → « Build APK » se lance au push.
3. Télécharge l'APK dans **Artifacts → accords-apk**.

### Signature de l'APK
Rien à configurer : le build signe avec `sossokouame.keystore` (à garder à la racine du dépôt, même clé à chaque version).
Facultatif : si les secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` et
`ANDROID_KEY_PASSWORD` existent dans **Settings → Secrets and variables → Actions**, ils sont utilisés à la place.
Garde le dépôt **privé** : le keystore et son mot de passe y sont.

Au premier lancement, l'appli télécharge le modèle Vosk français (≈ 40 Mo) et le garde pour un usage hors ligne.
- Accords en notation française (Do, Ré, Mi, Fa, Sol, La, Si ; dièses ou bémols dans Réglages).
- Paroles horodatées avec les accords placés au-dessus des mots, dans l'écran et dans le PDF.
- Sections instrumentales repérées dans les silences des paroles : [INTRO piano], [SOLO], [PAUSE solo piano], [FIN instrumentale], avec la suite d'accords jouée.
- Barre de progression 0 → 100 % pendant l'analyse et l'extraction des paroles ; paroles affichées en direct pendant la lecture.
- Interface sombre bleu nuit, vert terrain et accents chauds; cartes arrondies, boutons à dégradé, animations d'entrée et icône adaptative.

Tous les fichiers sont à la racine, y compris l'icône et le thème (ic_launcher_*.xml, colors.xml, themes.xml). Le dossier `res/` exigé par Android est créé automatiquement pendant la compilation (voir build.gradle).
