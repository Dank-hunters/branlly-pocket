# Architecture modulaire des actions

## Flux

```text
ActionNode
→ ActionRegistry.registration(kind)
→ handler.validate(action)
→ handler.execute(action)
→ ActionResult
→ ErrorStrategy
→ node suivant
```

`ShortcutExecutor` ne contient aucune branche par fournisseur ou type d’action. Il consulte uniquement `ActionRegistry`.

## Ajouter une action

Une action simple nécessite normalement :

1. son modèle et son `ActionKind` dans `domain/model/Action.kt` ;
2. son `ActionHandler` ;
3. son `RegisteredAction` dans `AndroidActionRegistry` (présentation, disponibilité, factory) ;
4. son configurateur dans `ActionEditorRegistry` ;
5. son `ActionJsonCodec` dans `ActionJsonCodecRegistry` ;
6. ses tests.

`ShortcutExecutor`, `RoutineExecutionService` et `RoutineOrchestrator` ne doivent pas être modifiés.

Une action sans handler reste lisible depuis les anciennes données, mais la validation la refuse et elle n’apparaît pas dans l’éditeur.

## Ajouter un fournisseur

Un fournisseur multimédia implémente `MediaAppAdapter`. Un fournisseur de navigation implémente `NavigationProviderAdapter`. Il est ensuite ajouté à `BuiltInProviderCatalog`, avec ses tests.

Le moteur, l’orchestrateur et les handlers génériques ne changent pas.

Les packages, schémas URI et particularités fournisseur restent exclusivement dans `platform/android/actions/ProviderAdapters.kt` ou dans un fichier d’adaptateur voisin.

## Lancements externes

Tous les handlers externes passent par `ExternalActivityGateway` :

- résolution de l’Intent ;
- flags Android ;
- journalisation ;
- exceptions ;
- détection d’une exécution en arrière-plan ;
- notification de continuation utilisateur.

Un lancement depuis l’arrière-plan rend `ActionResult.UserActionRequired`. Aucun handler ne lance le node suivant.

## Continuation utilisateur persistante

`UserActionRequired` n’est pas un résultat terminal. `ShortcutExecutor` retourne `RoutineExecutionResult.WaitingUserAction` avec le node courant. `RoutineOrchestrator` persiste alors une `RoutineContinuation` contenant :

- `continuationId`, `executionId` et `routineId` ;
- `nodeId`, `nodeIndex` et `actionKind` ;
- paramètres sérialisés de l’action ;
- snapshot immuable de la routine et des nodes restants ;
- dates de création et d’expiration.

L’unique état actif est conservé dans `routine_execution_state` avec `RUNNING` ou `WAITING_USER_ACTION`. Il remplace les anciens verrous mémoire indépendants. Une routine en attente refuse donc tout nouveau départ même après recréation du service.

La notification ouvre `ContinuationActivity`, qui transmet la même identité à :

```text
RoutineExecutionService
→ RoutineOrchestrator
→ RoutineValidator
→ ShortcutExecutor
→ ActionRegistry
```

La continuation est réclamée atomiquement avant exécution. Le node bloqué est rejoué avec `ActionExecutionContext.userInitiated=true`, puis les nodes suivants sont parcourus normalement. Un second appui reçoit `AlreadyConsumed`.

L’action **Annuler** supprime atomiquement l’état actif et la notification. Une alarme Android nettoie les continuations expirées. La durée par défaut est de dix minutes et peut être injectée dans l’orchestrateur.

## Compatibilité

Le stockage reste DataStore + JSON. `ActionJsonCodecRegistry` délègue à un codec par `ActionKind`. Les actions historiques sans handler restent décodables afin que la validation puisse afficher une erreur explicite. L’ancien `finalForegroundNodeId` est lu comme donnée legacy puis ignoré : l’ordre de `nodes` est désormais l’unique ordre d’exécution.
