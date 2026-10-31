# Intégration d'Accords dans Kouamé Play Box

Après la connexion (écran de compte existant), l'accueil affiche une nouvelle tuile **🎼 Accords & Musique** :
- **Analyser un morceau** : tonalité, tempo, accords, paroles, PDF (`AccordsAnalyseActivity`)
- **Créer une chanson** : texte → musique, voix, chœur (`SongActivity`, `SongSynth`)
- **Séparateur de voix** : voix, batterie, basse, piano + autres instruments, export WAV (`StemActivity`)

Les écrans d'Accords ne sont pas exportés : on n'y accède qu'une fois connecté.

Classes renommées pour éviter les conflits : MainActivity → AccordsAnalyseActivity, HomeActivity → AccordsHomeActivity, Ui → AccordsUi.
Tout est dans le paquet `bj.radio`. Le workflow `build.yml` range les ressources `accords_*.xml`.
La version passe à 1.18 (versionCode 18).
