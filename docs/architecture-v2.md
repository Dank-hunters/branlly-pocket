# Branlly Pocket — architecture d’exécution V2

## Base auditée

Point de sauvegarde local : `backup/pre-v2-v0.14.0` (`b4ae809`). Branche : `feature/execution-v2`.

La V1 modulaire est conservée :

```text
RoutineExecutionService
→ RoutineOrchestrator
→ RoutineValidator
→ ShortcutExecutor
→ ActionRegistry
→ ActionHandler
→ ActionResult
```

Composants corrects conservés : `ActionRegistry`, `ActionHandler`, `ShortcutExecutor`, `RoutineOrchestrator`, `RoutineExecutionService`, résultats génériques, validation, `ExternalActivityGateway`, adaptateurs fournisseurs, verrou persistant, continuation atomique, ordre `ShortcutDefinition.nodes` et filtrage média par package exact.

Constats réels :

- l’éditeur expose actuellement huit actions techniques ;
- `OPEN_APPLICATION` mélange déjà ouverture simple, URI et recherche média ;
- une lecture nécessite encore deux nodes visibles (`OPEN_APPLICATION` puis `WAIT_FOR_MEDIA_PLAYBACK`) ;
- Bluetooth passe par `OPEN_SETTINGS` et un polling de 500 ms, jusqu’à 120 secondes ;
- la continuation sait reprendre un node utilisateur mais ne possède pas encore de checkpoint de sous-étape ;
- le moteur central ne contient déjà aucun fournisseur et ne doit pas changer pour accueillir les actions V2.

## Cible V2

```text
Action utilisateur
→ handler enregistré
→ CapabilityResolver pur
→ workflow interne borné
   → transition interne
   → événement de progression
   → checkpoint seulement aux attentes importantes
   → résultat final générique
→ ShortcutExecutor
→ node utilisateur suivant
```

Les sous-étapes ne deviennent jamais des `ActionNode`. Un handler ne reçoit ni node précédent, ni node suivant.

## Contrats Phase 1

- `ActionWorkflow<S>` : une transition depuis un état explicite ;
- `ActionWorkflowState` : clé stable et sérialisable ;
- `ActionWorkflowStep` : `ContinueInternally`, résultats terminaux et attentes ;
- `ActionWorkflowContext` : identité, dates, logs, progression et checkpoint ;
- `ActionWorkflowCheckpoint` : état minimal migrable, sans `Intent` brut ;
- `BoundedActionWorkflowRunner` : boucle itérative bornée, timeout global et budget maximal ;
- `CapabilityResolver<A, C>` : calcul pur, sans lancement, callback permanent ou scan répété ;
- `ActionProgress` : information UI séparée du résultat.

Bornes initiales : 16 transitions par défaut, maximum absolu 64, timeout obligatoire entre 100 ms et 30 minutes. Aucun retry implicite et aucune récursion.

## Migration progressive

### Phase 2 — ENABLE_BLUETOOTH

Nouvelle action simple dédiée. Workflow :

```text
CHECKING_STATE
→ WAITING_FOR_SYSTEM_CONFIRMATION si nécessaire
→ REQUESTING_ENABLE
→ VERIFYING_STATE
→ COMPLETED / CANCELLED / FAILED / TIMED_OUT
```

Une Activity interne non exportée utilise Activity Result pour la permission Bluetooth éventuelle puis `BluetoothAdapter.ACTION_REQUEST_ENABLE`. Aucun écran général de paramètres.

### Phases suivantes

- `PLAY_MEDIA` encapsulera résolution, stratégie, ouverture, demande de lecture et confirmation `STATE_PLAYING` ;
- `START_NAVIGATION` encapsulera fournisseur, Intent, BAL et continuation ;
- les actions V1 restent décodables et seront classées en mode avancé ;
- aucune conversion automatique d’ancienne routine sans correspondance certaine.

## Persistance et performance

Un checkpoint est écrit uniquement au début d’une attente externe ou d’une stratégie restaurable. Les progressions visuelles restent en mémoire. Aucun travail n’est conservé sans routine active.

APIs événementielles prioritaires : Activity Result, callbacks MediaController, BroadcastReceiver et événements Accessibility filtrés. Le polling V1 média/Bluetooth devra être remplacé ou limité à un fallback borné lors de sa migration.

Toute ressource dynamique doit être libérée en `finally`. Aucun WakeLock ou overlay permanent n’est prévu.

## Sécurité

- Activities et receivers intermédiaires non exportés ;
- PendingIntent immutables ;
- identité de continuation vérifiée dans l’état persistant ;
- aucune confiance accordée aux seuls extras ;
- automatisation future strictement limitée au package cible et arrêtée lors d’un changement de premier plan ;
- aucune permission overlay ou Accessibility demandée par défaut.

## Compatibilité

Les modèles et codecs existants restent lisibles. Les nouvelles actions reçoivent de nouveaux `ActionKind` et codecs. La version de stockage sera augmentée sans réécriture destructive. `ShortcutExecutor` et `RoutineOrchestrator` restent indépendants des fournisseurs et des sous-étapes.
