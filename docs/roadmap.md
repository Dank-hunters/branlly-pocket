# Feuille de route contrôlée

1. **Modèle de données** — socle créé ; compléter migrations et persistance atomique, puis extraire le domaine dans un module Kotlin Multiplatform commun à Android et iOS.
2. **Éditeur visuel** — première version fonctionnelle ; ajouter glisser-déposer accessible et annulation.
3. **Bibliothèque d’actions** — catalogue local créé ; compléter compatibilités Android.
4. **Formulaires** — prochaine étape : formulaires typés et valeurs fixe/lancement/déclencheur.
5. **Moteur d’exécution** — exécution séquentielle annulable, timeouts et journal local expurgé.
6. **Création guidée** — enrichir les paramètres contextuels.
7. **Blueprints** — compléter les sept modèles via des fabriques du modèle canonique.
8. **Recommandations** — étendre la matrice locale testée.
9. **Favoris et récents**.
10. **Sous-séquences réutilisables** avec détection de cycles.
11. **Mode avancé**.

Chaque étape doit réussir tests unitaires, lint Android, build release R8 et revue des permissions avant fusion. L’application iOS et ses tests sur macOS seront ajoutés après stabilisation du domaine partagé ; aucune API Android ne doit entrer dans le domaine entre-temps.
