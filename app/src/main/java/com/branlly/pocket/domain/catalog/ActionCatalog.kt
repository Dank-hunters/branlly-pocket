package com.branlly.pocket.domain.catalog

import com.branlly.pocket.domain.model.ActionCategory
import com.branlly.pocket.domain.model.ActionKind
import com.branlly.pocket.domain.model.InputValue
import com.branlly.pocket.domain.model.SettingsPanel
import com.branlly.pocket.domain.model.ShortcutAction
import com.branlly.pocket.domain.model.SoundMode
import com.branlly.pocket.domain.model.Trigger
import com.branlly.pocket.domain.model.VolumeStream

/** Catalogue local immuable. Aucun résultat ne dépend du réseau ou d'une IA. */
data class ActionDescriptor(
    val kind: ActionKind,
    val category: ActionCategory,
    val title: String,
    val description: String,
    val createDefault: () -> ShortcutAction,
)

object ActionCatalog {
    val all: List<ActionDescriptor> = listOf(
        descriptor(ActionKind.OPEN_APPLICATION, ActionCategory.OPEN, "Ouvrir une application", "Application installée") {
            ShortcutAction.OpenApplication(InputValue.AskAtRuntime)
        },
        descriptor(ActionKind.OPEN_WEBSITE, ActionCategory.OPEN, "Ouvrir un site", "Adresse HTTPS") {
            ShortcutAction.OpenWebsite(InputValue.AskAtRuntime)
        },
        descriptor(ActionKind.OPEN_ROUTE, ActionCategory.OPEN, "Lancer un itinéraire", "Destination fixe ou demandée") {
            ShortcutAction.OpenRoute(InputValue.AskAtRuntime, InputValue.AskAtRuntime)
        },
        descriptor(ActionKind.OPEN_SETTINGS, ActionCategory.OPEN, "Ouvrir des réglages", "Bluetooth, Wi-Fi, batterie…") {
            ShortcutAction.OpenSettings(SettingsPanel.BLUETOOTH)
        },
        descriptor(ActionKind.SET_VOLUME, ActionCategory.DEVICE, "Régler le volume", "Multimédia, sonnerie ou alarme") {
            ShortcutAction.SetVolume(VolumeStream.MEDIA, InputValue.Fixed(70))
        },
        descriptor(ActionKind.SET_BRIGHTNESS, ActionCategory.DEVICE, "Régler la luminosité", "Niveau de 0 à 100 %") {
            ShortcutAction.SetBrightness(InputValue.Fixed(50))
        },
        descriptor(ActionKind.SET_SOUND_MODE, ActionCategory.DEVICE, "Changer le mode sonore", "Normal, vibreur ou silencieux") {
            ShortcutAction.SetSoundMode(SoundMode.VIBRATE)
        },
        descriptor(ActionKind.PREPARE_SMS, ActionCategory.COMMUNICATE, "Préparer un SMS", "Confirmation toujours visible") {
            ShortcutAction.PrepareSms(InputValue.AskAtRuntime, InputValue.AskAtRuntime)
        },
        descriptor(ActionKind.CALL_CONTACT, ActionCategory.COMMUNICATE, "Appeler un contact", "Choisir au lancement") {
            ShortcutAction.CallContact(InputValue.AskAtRuntime)
        },
        descriptor(ActionKind.COPY_TEXT, ActionCategory.COMMUNICATE, "Copier un texte", "Dans le presse-papiers") {
            ShortcutAction.CopyText(InputValue.AskAtRuntime)
        },
        descriptor(ActionKind.WAIT, ActionCategory.ORGANIZE, "Attendre", "Délai annulable") {
            ShortcutAction.Wait(2_000)
        },
        descriptor(ActionKind.CHECKLIST, ActionCategory.ORGANIZE, "Afficher une checklist", "Liste locale interactive") {
            ShortcutAction.Checklist(listOf("Élément à compléter"))
        },
        descriptor(ActionKind.NOTIFICATION, ActionCategory.ORGANIZE, "Créer une notification", "Rappel local") {
            ShortcutAction.Notification("Branlly Pocket", "Raccourci terminé")
        },
        descriptor(ActionKind.CONFIRMATION, ActionCategory.ORGANIZE, "Demander confirmation", "Continuer ou annuler") {
            ShortcutAction.Confirmation("Continuer ?")
        },
        descriptor(ActionKind.RUN_SHORTCUT, ActionCategory.CONTROL, "Lancer un raccourci", "Sous-raccourci réutilisable") {
            ShortcutAction.RunShortcut(com.branlly.pocket.domain.model.ShortcutId("missing"))
        },
        descriptor(ActionKind.STOP_EXECUTION, ActionCategory.CONTROL, "Arrêter l’exécution", "Fin immédiate et propre") {
            ShortcutAction.StopExecution
        },
    )

    fun orderedFor(trigger: Trigger): List<ActionDescriptor> {
        val priorities = LocalRecommendations.forTrigger(trigger)
        return all.sortedWith(compareBy<ActionDescriptor> { priorities.indexOf(it.kind).orLast() }.thenBy { it.category.ordinal })
    }

    private fun descriptor(
        kind: ActionKind,
        category: ActionCategory,
        title: String,
        description: String,
        create: () -> ShortcutAction,
    ) = ActionDescriptor(kind, category, title, description, create)

    private fun Int.orLast(): Int = if (this < 0) Int.MAX_VALUE else this
}
