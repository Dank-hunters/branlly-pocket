# Branlly Pocket

Créateur de raccourcis visuel, local et déterministe pour Android, puis iOS.

## Principes non négociables

- aucune IA et aucune analyse de langage naturel ;
- aucune permission Internet ;
- mêmes objets métier pour l’éditeur, l’assistant et les blueprints ;
- validation avant activation ;
- privilèges de plateforme demandés seulement au moment où une fonction les exige ;
- domaine indépendant d’Android afin de préparer le partage Kotlin Multiplatform avec iOS ;
- mode simplifié par défaut.

## État du socle (0.1)

- modèle unifié versionné pour déclencheurs, actions, conditions et erreurs ;
- écran de choix entre création guidée, blueprint et création libre ;
- assistant de sélection du déclencheur ;
- éditeur vertical avec insertion, déplacement, duplication, désactivation et suppression ;
- bibliothèque d’actions classée et ordonnée selon le contexte ;
- recommandations déterministes locales (maximum trois) ;
- premier blueprint « Mode voiture » utilisant exactement le modèle commun ;
- validateur métier et tests unitaires initiaux.

L’exécution système n’est pas encore activée : l’application ne prétend pas exécuter une action tant que les contrôles de permissions et de compatibilité ne sont pas implémentés.

## Compiler

Prérequis : JDK 17 et Android SDK 35.

```bash
./gradlew test
./gradlew assembleDebug
```

Ouvrir la racine du projet dans Android Studio fonctionne également.

## Structure

```text
app/src/main/java/com/branlly/pocket/
├── domain/model       # représentation canonique
├── domain/catalog     # catalogue et matrice locale
├── domain/validation  # validation indépendante de l’UI
├── data               # contrats de persistance
└── ui                 # Compose, état à flux unidirectionnel
```

## Installation et publication

- [Installer sur Android et stratégie iOS](docs/installation.md)
- [Architecture Android/iOS](docs/cross-platform.md)
- [Publier une release Android signée](docs/releasing-android.md)

Voir également [`SECURITY.md`](SECURITY.md) et [`docs/roadmap.md`](docs/roadmap.md).
