# Journal des modifications

## 0.15.11

### Corrections

- PLAY_MEDIA affiche désormais la cause compréhensible et un code stable lorsqu’une commande directe échoue.
- En mode Automatique, l’information précède l’ouverture du lecteur ; en mode Arrière-plan uniquement, elle confirme que le lecteur reste fermé.
- Les notifications de résultat restent temporaires en mode Automatique et dismissibles en mode Arrière-plan uniquement.

### Validation

- Des tests automatisés couvrent les raisons, le fallback, l’absence d’ouverture et l’idempotence de checkpoint.
- Cette version n’a pas été validée physiquement sur téléphone avant le test utilisateur.

## 0.15.10

### Nouveautés

- PLAY_MEDIA propose désormais les modes **Automatique**, **Arrière-plan uniquement** et **Ouvrir le lecteur**.
- Le mode choisi apparaît dans le configurateur HUD et dans le résumé de l’action.
- Branlly informe désormais l’utilisateur lorsqu’un mode Automatique ouvre le lecteur après l’échec d’une commande directe.

### Compatibilité et validation

- Les routines existantes sans mode de lancement restent interprétées en mode Automatique.
- Des tests automatisés couvrent la sérialisation, les plans de lancement et les reprises PLAY_MEDIA.
- Cette version n’a pas été validée physiquement sur téléphone avant le test utilisateur.

## 0.15.9

### Corrections

- PLAY_MEDIA confirme désormais un changement de contenu sur une MediaSession déjà en lecture avant la commande directe.
- Lorsque cette confirmation est fiable, la recherche de secours n’ouvre pas le lecteur.
- Le fallback reste utilisé si aucune transition ou modification de contenu vérifiable n’est disponible.
- Les checkpoints média conservent les données de corrélation nécessaires ; les anciens checkpoints restent lisibles.

### Validation

- La correction est couverte par des tests automatisés de baseline, checkpoint et coordination média.
- Elle n’a pas encore été validée sur un lecteur réel par test sur téléphone.

## 0.15.8

### Corrections

- PLAY_MEDIA sélectionne désormais une MediaSession compatible appartenant exactement au lecteur configuré.
- Une recherche directe utilise `playFromSearch` en priorité et n’ouvre le lecteur qu’après un échec réel ou l’absence de lecture confirmée.
- Les sessions incompatibles et les reprises de checkpoint ne provoquent plus de commande générique ou rejouée.
- Une journalisation ciblée précise les capacités MediaSession, la commande envoyée et la raison d’un éventuel fallback.

## 0.15.7

### Nouveautés et améliorations

- La création d’une routine ouvre désormais directement l’éditeur libre depuis l’accueil, la navigation et le widget.
- Les anciens parcours « Création guidée » et « Blueprint » ont été entièrement retirés.
- Les routines existantes conservent leurs actions et restent modifiables, y compris lorsqu’elles contiennent une ancienne métadonnée de mode.

## 0.15.6

### Corrections

- Les routines enregistrées peuvent de nouveau être testées et lancées depuis les widgets.
- Un lancement défaillant ne laisse plus un verrou d’exécution persistant bloquant les routines suivantes.
- Les états d’exécution orphelins sont nettoyés sans supprimer les continuations utilisateur légitimes.

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
