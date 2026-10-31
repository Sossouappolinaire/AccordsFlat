# Accords

Application Android (Java) : tonalité, tempo, notes, accords, grille d'accords, transposition, suivi en lecture, export PDF, paroles (hors ligne avec Vosk ou en ligne via une API compatible OpenAI).

Tous les fichiers sont à la racine du projet (sauf `.github/workflows/build.yml`, imposé par GitHub).

## Compiler sur GitHub
1. Envoie tout le contenu de ce dossier dans un dépôt GitHub.
2. Onglet **Actions** → « Build APK » se lance au push.
3. Télécharge l'APK dans **Artifacts → accords-apk**.

Au premier lancement, l'appli télécharge le modèle Vosk français (≈ 40 Mo) et le garde pour un usage hors ligne.
