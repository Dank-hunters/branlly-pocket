package com.branlly.pocket.platform.android

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class PairedBluetoothDevice(val address: String, val name: String)

class PairedBluetoothCatalog(private val context: Context) {
    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    fun load(): List<PairedBluetoothDevice> {
        if (!hasPermission()) return emptyList()
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return emptyList()
        return manager.adapter
            ?.bondedDevices
            .orEmpty()
            .asSequence()
            .map { device ->
                PairedBluetoothDevice(
                    address = device.address,
                    name = device.name?.trim()?.take(MAX_NAME_LENGTH)?.ifEmpty { null } ?: "Appareil sans nom",
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, PairedBluetoothDevice::name))
            .take(MAX_DEVICE_COUNT)
            .toList()
    }

    companion object {
        private const val MAX_DEVICE_COUNT = 100
        private const val MAX_NAME_LENGTH = 80
    }
}
