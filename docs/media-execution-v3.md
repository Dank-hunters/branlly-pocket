# Exécution média V3

## Objectif

`PLAY_MEDIA` reste une action unique. La progression générale reste exclusivement :

```text
RoutineExecutionService → RoutineOrchestrator → ShortcutExecutor → ActionRegistry
→ PlayMediaHandler → ActionResult → node suivant
```

`ShortcutExecutor` est l’unique propriétaire du node suivant. Les composants média ne connaissent ni le node précédent ni le node suivant.

## Runtime actif

`PlayMediaCoordinator` est l’unique moteur de `PLAY_MEDIA`. L’ancien `PlayMediaWorkflow`, ses stratégies et ses checkpoints `pendingStrategyId` ont été supprimés ; aucun câblage runtime ne permet de les restaurer.

## Source de vérité V3

Une `MediaExecutionSession` est la source de vérité unique pour la clé :

```text
executionId + nodeId
```

Elle porte :

- identités d’exécution et package cible ;
- action immuable et baseline capturé avant toute opération ;
- plan immuable et opérations exécutées ;
- état courant, version, deadline globale et deadline automatique ;
- résultat terminal unique ;
- continuation consommée et assistance manuelle affichée.

Chaque transition terminale est atomique. Après un résultat terminal, tout callback est ignoré et le nettoyage central retire l’observateur et la notification manuelle.

## Machine à états

```text
PRECHECK
  → CAPTURE_BASELINE
  → START_OBSERVATION
  → BUILD_PLAN
  → EXECUTE_OPERATION
  → AWAIT_OUTCOME ── lecture confirmée ──→ COMPLETED
        │                    │
        │                    └── cleanup → ActionResult.Completed
        ├── opération sans résultat → EXECUTE_OPERATION
        ├── blocage Android avant lancement → AWAIT_USER_LAUNCH
        │       └── continuation consommée → EXECUTE_OPERATION (une seule fois)
        └── ouverture/recherche faite sans lecture → AWAIT_MANUAL_PLAY
                └── même observateur → COMPLETED | TIMED_OUT | CANCELLED
```

États terminaux : `COMPLETED`, `FAILED`, `CANCELLED`, `TIMED_OUT`.

## Baseline et confirmation

Avant toute commande, la session capture les sessions du package exact, leur token, état de lecture et métadonnées disponibles. Une lecture déjà active est `PREEXISTING_PLAYBACK` : elle ne termine jamais automatiquement l’action.

Les confirmations internes sont :

- `PLAYBACK_AND_CONTENT_CONFIRMED` ;
- `PLAYBACK_CONFIRMED_CONTENT_UNVERIFIED` ;
- `PREEXISTING_PLAYBACK`.

Le mode simple accepte une nouvelle lecture du package exact si le contenu exact ne peut pas être vérifié. Une politique exacte exige une preuve de contenu.

## Plan et opérations

Le plan est construit une seule fois depuis les capacités initiales, dans cet ordre : URI directe, commande MediaSession, recherche fournisseur, automatisation fournisseur réellement disponible, assistance manuelle.

Une `MediaOperation` possède un `operationId` stable, un statut et une exécution maximale de un. Elle provoque seulement un événement ; l’observateur unique décide la réussite.

L’assistance manuelle ne lance jamais d’Intent : elle affiche une seule consigne après une ouverture/recherche déjà réussie et conserve l’observateur existant.

## Observateur unique

`MediaOutcomeObserver` est installé avant la première opération et vit jusqu’au résultat terminal. Il utilise `OnActiveSessionsChangedListener` et `MediaController.Callback`, filtre le package exact, compare avec le baseline et ne fait aucun polling. Il est retiré dans le nettoyage central.

## Continuation

Une continuation identifie un état métier, pas une stratégie à rejouer :

```text
executionId + nodeId + operationId + stateVersion
```

Elle conserve le plan et le baseline. Après claim atomique, elle exécute uniquement l’opération préparée, une fois. Si Android bloque encore ce lancement, la session échoue explicitement : aucune nouvelle continuation identique n’est créée.

## Notifications

- Service foreground : informative, silencieuse, sans texte de validation interactive.
- Continuation : une seule demande interactive à la fois ; supprimée au clic, à l’annulation, à l’expiration et au résultat terminal.
- Assistance manuelle : une seule consigne, supprimée au résultat terminal.

## Legacy

`OPEN_APPLICATION` et `WAIT_FOR_MEDIA_PLAYBACK` restent décodables et avancés. `AndroidMediaPlaybackWaiter` est réservé à `WAIT_FOR_MEDIA_PLAYBACK` et n’est jamais injecté dans `PLAY_MEDIA`. Le mode simple n’ajoute jamais ces nodes à `PLAY_MEDIA`. Une routine mélangeant `PLAY_MEDIA` avec une action legacy du même package est refusée par validation.
