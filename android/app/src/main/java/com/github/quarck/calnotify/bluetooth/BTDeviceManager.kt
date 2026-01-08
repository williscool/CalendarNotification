package com.github.quarck.calnotify.bluetooth

import android.bluetooth.*
import android.content.Context
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusSystemClock

data class BTDeviceSummary(val name: String, val address: String, val currentlyConnected: Boolean)


class BTDeviceManager(
    val ctx: Context,
    private val clock: CNPlusClockInterface = CNPlusSystemClock()
){
    companion object {
        private const val LOG_TAG = "BTDeviceManager"
    }

    val adapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    val manager: BluetoothManager? by lazy { ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }

    val storage: BTCarModeStorage by lazy { ctx.btCarModeSettings }

    /**
     * Get list of paired Bluetooth devices.
     * Returns null if Bluetooth is not available or if permission is denied.
     * On Android 12+ (API 31), BLUETOOTH_CONNECT permission is required.
     */
    val pairedDevices: List<BTDeviceSummary>?
        get() = try {
            adapter
                ?.bondedDevices
                ?.map { BTDeviceSummary(it.name ?: "Unknown", it.address, isDeviceConnected(it)) }
                ?.toList()
        } catch (e: SecurityException) {
            DevLog.error(LOG_TAG, "SecurityException accessing bonded devices - missing BLUETOOTH_CONNECT permission", e)
            null
        }

    fun isDeviceConnected(device: BluetoothDevice) =
            manager?.getConnectionState(device, android.bluetooth.BluetoothProfile.GATT) ==
                    android.bluetooth.BluetoothProfile.STATE_CONNECTED

    fun isDeviceConnected(address: String): Boolean =
            adapter?.getRemoteDevice(address)?.let { isDeviceConnected(it) } ?: false

    fun isDeviceConnected(dev: BTDeviceSummary): Boolean =
            adapter?.getRemoteDevice(dev.address)?.let { isDeviceConnected(it) } ?: false

    private val hasCarModeTriggersConnected: Boolean
        get() = storage.carModeTriggerDevices.any { isDeviceConnected(it) }


    val carModeSilentUntil: Long
        get() {
            val lastCachedValue = storage.carModeSilentUntil
            val now = clock.currentTimeMillis()

            if (now + Consts.MINUTE_IN_MILLISECONDS < lastCachedValue ) {
                return lastCachedValue
            }

            try {
                if (!hasCarModeTriggersConnected) {
                    return 0
                }
            }
            catch (ex: Exception) {
                return 0
            }

            val newValue = now + Consts.CAR_MODE_SLEEP_QUANTUM
            storage.carModeSilentUntil = newValue
            return newValue
        }
}