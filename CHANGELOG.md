# Journal des modifications

## 0.15.0

### Nouveautés et améliorations

- Nouveau moteur **PLAY_MEDIA V3**, plus fiable et déterministe.
- La routine attend désormais le démarrage réel de la lecture avant de passer à l’action suivante.
- Une action média interrompue peut reprendre à l’étape en cours sans recommencer inutilement la routine.
- Les continuations et les clics répétés sont protégés contre les doubles exécutions.
- La création et le test de routines comportant plusieurs actions fonctionnent à nouveau correctement.
- Les erreurs de validation sont affichées directement près de l’action concernée dans l’éditeur.
- L’éditeur donne accès au réglage Android nécessaire pour autoriser le contrôle de lecture.
- Les destinations sont maintenant correctement transmises à Waze lors du lancement d’un itinéraire.

### Compatibilité

- L’ancien moteur de lecture média inutilisé a été retiré.
- **WAIT_FOR_MEDIA_PLAYBACK** reste disponible dans le mode avancé pour les anciennes routines compatibles.
