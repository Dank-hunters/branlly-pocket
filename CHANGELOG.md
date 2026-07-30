# Journal des modifications

## 0.15.5

### Nouveautés et améliorations

- Choisir une action configurable ouvre désormais directement son formulaire, sans ajouter de node incomplet à la timeline.
- La validation des actions est transactionnelle : l’annulation conserve la routine intacte et l’édition garde le même node.
- Le formulaire PLAY_MEDIA guide la sélection de l’application puis le choix entre recherche et lien direct.
- La saisie manuelle d’un package multimédia reste disponible comme option secondaire.
- OPEN_ROUTE peut maintenant demander une destination au moment de l’exécution, y compris depuis une continuation en arrière-plan.
- La destination saisie reste propre à l’exécution et ne modifie jamais la routine enregistrée.

## 0.15.4

### Nouveautés et améliorations

- PLAY_MEDIA privilégie désormais la commande de lecture directe depuis la recherche vers la session du lecteur configuré.
- Le lecteur n’est ouvert sur sa recherche qu’en solution de secours si la commande directe ne mène pas à une lecture confirmée.

## 0.15.3

### Nouveautés et améliorations

- Correction de l’activation Bluetooth sur Android 11.
- Le contrôle Bluetooth s’adapte automatiquement à la version Android : aucune demande « Appareils à proximité » sur Android 11, et `BLUETOOTH_CONNECT` seulement à partir d’Android 12.
- L’assistant d’autorisations et l’exécution des routines utilisent désormais la même politique de capacités Android.
- Le succès Bluetooth est confirmé uniquement après l’activation réelle de l’adaptateur.
- Les autres accès système sont vérifiés selon leur besoin réel.
- Les routines existantes ne nécessitent aucune modification.

## 0.15.2

### Nouveautés et améliorations

- Deux widgets compacts permettent de lancer une sélection ordonnée de routines enregistrées.
- Le widget « Routines » affiche jusqu’à quatre routines ; « Routines + Créer » en affiche jusqu’à trois avec un accès direct à la création.
- Chaque widget possède sa configuration et sa sélection indépendantes.

## 0.15.1

### Nouveautés et améliorations

- Un assistant guide la configuration des autorisations au premier lancement.
- Les demandes d’autorisations s’adaptent désormais à la version Android utilisée.
- L’accès au contrôle de lecture est plus simple à activer et à vérifier.
- Branlly signale précisément une autorisation retirée après la configuration.
- Un test intégré permet de vérifier l’affichage et les actions des notifications.
- La petite icône des notifications est maintenant correctement affichée sur Android 11.
- Les notifications de continuation sont plus lisibles et conservent leurs actions Continuer et Annuler.
- Les routines existantes ne nécessitent aucune modification.

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
