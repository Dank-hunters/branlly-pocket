package com.branlly.pocket.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

sealed interface Trigger {
    data object ManualButton : Trigger
    data class Time(val time: LocalTime, val days: Set<DayOfWeek> = DayOfWeek.entries.toSet()) : Trigger
    data class Bluetooth(
        val deviceAddress: String?,
        val deviceName: String?,
        val event: ConnectionEvent,
        val delayMillis: Long = 0,
        val requiresConfirmation: Boolean = false,
    ) : Trigger {
        init {
            require(delayMillis in 0L..3_600_000L)
            require(deviceAddress == null || BLUETOOTH_ADDRESS.matches(deviceAddress))
        }
    }
    data class Wifi(val ssid: String, val event: ConnectionEvent) : Trigger
    data class Charger(val event: ChargerEvent) : Trigger
    data class BatteryLevel(val thresholdPercent: Int, val comparison: NumericComparison) : Trigger {
        init { require(thresholdPercent in 1..100) }
    }
    data class Nfc(val localTagId: String? = null) : Trigger
    data object Widget : Trigger
    data object QuickTile : Trigger

    companion object {
        private val BLUETOOTH_ADDRESS = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")
    }
}

enum class ConnectionEvent { CONNECTED, DISCONNECTED, BOTH }
enum class ChargerEvent { PLUGGED, UNPLUGGED, BOTH }
enum class NumericComparison { LESS_THAN, LESS_OR_EQUAL, EQUAL, GREATER_OR_EQUAL, GREATER_THAN }
