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
