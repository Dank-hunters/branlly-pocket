package com.branlly.pocket.domain.model

sealed interface Condition {
    data class Battery(val comparison: NumericComparison, val percent: Int) : Condition {
        init { require(percent in 0..100) }
    }
    data class WifiConnected(val ssid: String) : Condition
    data class BluetoothConnected(val deviceAddress: String) : Condition
    data class DayMatches(val days: Set<java.time.DayOfWeek>) : Condition
    data class Group(val operator: LogicalOperator, val members: List<Condition>) : Condition {
        init { require(members.size in 2..20) }
    }
}

enum class LogicalOperator { AND, OR }
