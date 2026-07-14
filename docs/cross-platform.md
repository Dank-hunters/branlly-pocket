# Architecture Android et iOS

Android est la première plateforme testée, mais le domaine ne doit dépendre d’aucune API Android.

## Parties communes prévues

- schéma versionné des raccourcis ;
- catalogue d’actions ;
- blueprints ;
- recommandations déterministes ;
- règles de validation génériques ;
- détection des cycles et limites d’exécution ;
- formats d’import et d’export.

Ces composants migreront dans un module Kotlin Multiplatform avant l’introduction de l’application iOS. Les classes du domaine actuel n’importent volontairement aucune API Android.

## Adaptateurs de plateforme

Chaque plateforme fournit explicitement :

- ses capacités disponibles ;
- la résolution sécurisée des applications et intents/URL ;
- les permissions nécessaires ;
- l’exécution et l’annulation ;
- les déclencheurs autorisés en arrière-plan.

Une action non disponible est signalée dans l’éditeur et ne peut pas être activée. Aucun comportement Android ne doit être simulé ou annoncé comme disponible sous iOS.

## Contraintes iOS connues

Bluetooth, Wi-Fi, volume système et exécution automatique en arrière-plan sont fortement encadrés. Branlly Pocket utilisera les API Apple publiques, App Intents et Shortcuts lorsque cela est approprié. Aucun contournement par API privée ne sera accepté.
