package com.branlly.pocket.domain.model

fun Trigger.summary(): String =
    when (this) {
        Trigger.ManualButton -> "En appuyant sur un bouton"
        is Trigger.Time -> "À $time"
        is Trigger.Bluetooth -> "Bluetooth · ${deviceName ?: "appareil à choisir"}"
        is Trigger.Wifi -> "Wi-Fi · $ssid"
        is Trigger.Charger -> "Branchement du chargeur"
        is Trigger.BatteryLevel -> "Batterie $comparison $thresholdPercent %"
        is Trigger.Nfc -> "Tag NFC"
        Trigger.Widget -> "Depuis un widget"
        Trigger.QuickTile -> "Depuis une tuile rapide"
    }

fun ShortcutAction.summary(): String =
    when (this) {
        is ShortcutAction.OpenApplication -> "Ouvrir une application"
        is ShortcutAction.WaitForMediaPlayback -> "Attendre la lecture média"
        is ShortcutAction.OpenWebsite -> "Ouvrir un site"
        is ShortcutAction.OpenRoute -> "Lancer un itinéraire"
        is ShortcutAction.OpenSettings -> "Ouvrir les réglages · ${panel.label()}"
        is ShortcutAction.SetVolume -> "Volume ${stream.label()} · ${percent.displayPercent()}"
        is ShortcutAction.SetBrightness -> "Luminosité · ${percent.displayPercent()}"
        is ShortcutAction.SetSoundMode -> "Mode sonore · ${mode.label()}"
        is ShortcutAction.PrepareSms -> "Préparer un SMS"
        is ShortcutAction.CallContact -> "Appeler un contact"
        is ShortcutAction.CopyText -> "Copier un texte"
        is ShortcutAction.Wait -> "Attendre ${durationMillis / 1_000} secondes"
        is ShortcutAction.Checklist -> "Afficher une checklist · ${items.size} éléments"
        is ShortcutAction.Notification -> "Créer une notification"
        is ShortcutAction.Confirmation -> "Demander une confirmation"
        is ShortcutAction.RunShortcut -> "Lancer un autre raccourci"
        ShortcutAction.StopExecution -> "Arrêter l’exécution"
    }

private fun InputValue<Int>.displayPercent(): String =
    when (this) {
        is InputValue.Fixed -> "$value %"
        InputValue.AskAtRuntime -> "demander au lancement"
        is InputValue.FromTrigger -> "valeur du déclencheur"
    }

private fun VolumeStream.label() =
    when (this) {
        VolumeStream.MEDIA -> "multimédia"
        VolumeStream.RING -> "sonnerie"
        VolumeStream.ALARM -> "alarme"
    }

private fun SettingsPanel.label() = name.lowercase()

private fun SoundMode.label() = name.lowercase().replace('_', ' ')
