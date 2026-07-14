# Installation

## Android — méthode recommandée

1. Ouvrir la page **Releases** du dépôt GitHub depuis le téléphone.
2. Télécharger `Branlly-Pocket.apk` et son fichier `.sha256`.
3. Autoriser temporairement l’installation d’applications inconnues pour le navigateur utilisé.
4. Installer l’APK, puis retirer immédiatement cette autorisation au navigateur.

Android affichera un avertissement normal pour une application distribuée hors Play Store. Ne jamais installer un APK envoyé par messagerie ou provenant d’un miroir non officiel.

Pour les utilisateurs non techniques, la distribution Play Store sera préférable dès que le produit sera stable.

## Vérification facultative de l’APK

Sur un ordinateur :

```bash
sha256sum -c Branlly-Pocket.apk.sha256
```

La somme doit être identique à celle publiée dans la release GitHub.

## Versions de test Android

Chaque contrôle GitHub Actions produit un APK de débogage conservé sept jours. Ces APK sont réservés aux tests internes. Les versions publiques doivent toujours provenir de GitHub Releases et être signées avec la clé de production.

## iOS — phase ultérieure

La méthode de test prévue est **TestFlight**, puis l’App Store pour les versions publiques. Un compte Apple Developer et une signature Apple sont obligatoires. Aucun IPA générique ne sera proposé : cela contournerait le modèle de sécurité d’iOS et ne constituerait pas une installation fiable.
