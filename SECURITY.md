# Politique de sécurité

## Frontières actuelles

- Le manifeste ne déclare pas `INTERNET`.
- Le trafic en clair est interdit dans le manifeste et dans la configuration réseau.
- Seule l’activité de lancement est exportée ; aucun service, receiver ou provider ne l’est.
- Les sauvegardes cloud et le transfert d’appareil sont exclus.
- Les URL fixes sont limitées à HTTPS, sans identifiants intégrés.
- Les durées, pourcentages, tailles de listes et nombres de tentatives sont bornés dans le domaine.
- Les identifiants Bluetooth sont validés avant d’entrer dans le modèle.
- La release active R8, la suppression des ressources et interdit le débogage.

## Chaîne de publication

- Les workflows GitHub Actions sont épinglés à des commits précis.
- La clé Android n’est jamais stockée dans Git et n’est restaurée que pendant le job de publication.
- Les APK publics sont signés, hachés avec SHA-256 et produits uniquement depuis un tag Git.
- La future version iOS utilisera exclusivement les API publiques Apple, une signature Apple et TestFlight/App Store.

## Règles pour les prochaines phases

1. Ne jamais construire une commande shell depuis une valeur utilisateur.
2. Résoudre les intents explicitement et présenter l’application cible avant lancement.
3. Utiliser les Activity Result APIs et demander chaque permission au dernier moment.
4. Ne jamais stocker le contenu des contacts, tags NFC ou appareils observés dans les journaux.
5. Chiffrer les données sensibles avec une clé non exportable de l’Android Keystore.
6. Vérifier les cycles de sous-raccourcis avec une profondeur maximale stricte.
7. Conserver les raccourcis désactivés après migration jusqu’à validation complète.
8. Ajouter une dépendance uniquement avec verrouillage et contrôle de vulnérabilités.
9. Maintenir un modèle partagé Android/iOS et isoler chaque API de plateforme derrière un adaptateur.
10. Refuser toute API privée ou technique de contournement des restrictions Android ou iOS.

## Signalement

Ne pas publier une vulnérabilité contenant des données utilisateur dans une issue publique. Fournir une reproduction minimale sans secret au mainteneur du projet.
