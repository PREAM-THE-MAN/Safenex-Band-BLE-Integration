package com.safenex.app.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import com.safenex.app.domain.model.BleConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.Queue

/**
 * Manages the Bluetooth GATT connection lifecycle with the SAFENEX-BAND.
 * Handles service discovery, sequential CCCD descriptor subscription queues,
 * notification streaming, and robust error recovery.
 */
class BleManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "SAFENEX_BleManager"
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var activeDevice: BluetoothDevice? = null

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    // Stream of raw characteristic updates (UUID string -> ByteArray)
    private val _characteristicUpdates = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 64)
    val characteristicUpdates: SharedFlow<Pair<String, ByteArray>> = _characteristicUpdates.asSharedFlow()

    // Queue for sequential descriptor writes required by Android GATT stack
    private val descriptorWriteQueue: Queue<BluetoothGattDescriptor> = ArrayDeque()
    private var isWritingDescriptor = false

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                val errorMsg = "GATT connection error status: $status"
                Log.e(TAG, errorMsg)
                closeGatt()
                _connectionState.value = BleConnectionState.Error(errorMsg)
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server. Starting service discovery...")
                    _connectionState.value = BleConnectionState.Connecting
                    // Give connection a moment to stabilize before service discovery
                    scope.launch {
                        try {
                            gatt?.discoverServices()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error initiating discoverServices", e)
                            _connectionState.value = BleConnectionState.Error("Service discovery error: ${e.message}")
                        }
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server.")
                    closeGatt()
                    _connectionState.value = BleConnectionState.Disconnected
                }

                BluetoothProfile.STATE_CONNECTING -> {
                    _connectionState.value = BleConnectionState.Connecting
                }

                BluetoothProfile.STATE_DISCONNECTING -> {
                    _connectionState.value = BleConnectionState.Disconnecting
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                val errorMsg = "GATT service discovery failed with status $status"
                Log.e(TAG, errorMsg)
                _connectionState.value = BleConnectionState.Error(errorMsg)
                return
            }

            Log.i(TAG, "Services discovered. Inspecting SAFENEX Service (${BleConstants.SERVICE_UUID})...")
            val safenexService = gatt.getService(BleConstants.SERVICE_UUID)

            if (safenexService == null) {
                val errorMsg = "SAFENEX Service not found on device!"
                Log.e(TAG, errorMsg)
                _connectionState.value = BleConnectionState.Error(errorMsg)
                return
            }

            // Find SOS Characteristic
            val sosChar = safenexService.getCharacteristic(BleConstants.SOS_CHAR_UUID)
            if (sosChar == null) {
                Log.w(TAG, "SOS Characteristic not found (${BleConstants.SOS_CHAR_UUID})")
            } else {
                Log.i(TAG, "Found SOS characteristic. Subscribing to notifications...")
                enableNotification(gatt, sosChar)
            }

            // Find STATUS Characteristic
            val statusChar = safenexService.getCharacteristic(BleConstants.STATUS_CHAR_UUID)
            if (statusChar == null) {
                Log.w(TAG, "STATUS Characteristic not found (${BleConstants.STATUS_CHAR_UUID})")
            } else {
                Log.i(TAG, "Found STATUS characteristic. Subscribing to notifications...")
                enableNotification(gatt, statusChar)
            }

            val devName = try {
                gatt.device?.name ?: BleConstants.DEVICE_NAME
            } catch (e: SecurityException) {
                BleConstants.DEVICE_NAME
            }

            _connectionState.value = BleConnectionState.Connected(
                deviceName = devName,
                deviceAddress = gatt.device?.address ?: ""
            )
        }

        // Android 13+ (API 33) notification callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicValue(characteristic.uuid.toString(), value)
        }

        // Legacy notification callback (API < 33)
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && characteristic != null) {
                val value = characteristic.value ?: ByteArray(0)
                handleCharacteristicValue(characteristic.uuid.toString(), value)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            Log.d(TAG, "onDescriptorWrite: char=${descriptor?.characteristic?.uuid}, status=$status")
            isWritingDescriptor = false
            processNextDescriptorWrite(gatt)
        }
    }

    private fun handleCharacteristicValue(uuid: String, value: ByteArray) {
        val payloadStr = String(value, Charsets.UTF_8).trim()
        Log.i(TAG, "Notification received from [$uuid]: '$payloadStr' (${value.size} bytes)")
        scope.launch {
            _characteristicUpdates.emit(Pair(uuid, value))
        }
    }

    /**
     * Queues a notification subscription for a characteristic.
     */
    @SuppressLint("MissingPermission")
    private fun enableNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        // 1. Enable local notification listening
        val registered = gatt.setCharacteristicNotification(characteristic, true)
        if (!registered) {
            Log.e(TAG, "Failed to register local notification for ${characteristic.uuid}")
            return
        }

        // 2. Queue CCCD descriptor write on the peripheral
        val descriptor = characteristic.getDescriptor(BleConstants.CCCD_UUID)
        if (descriptor != null) {
            synchronized(descriptorWriteQueue) {
                descriptorWriteQueue.add(descriptor)
                if (!isWritingDescriptor) {
                    processNextDescriptorWrite(gatt)
                }
            }
        } else {
            Log.e(TAG, "CCCD descriptor (0x2902) missing for ${characteristic.uuid}")
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun processNextDescriptorWrite(gatt: BluetoothGatt?) {
        val targetGatt = gatt ?: bluetoothGatt ?: return
        synchronized(descriptorWriteQueue) {
            if (descriptorWriteQueue.isEmpty()) {
                isWritingDescriptor = false
                return
            }

            val descriptor = descriptorWriteQueue.poll() ?: return
            isWritingDescriptor = true

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val result = targetGatt.writeDescriptor(
                        descriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    )
                    Log.d(TAG, "writeDescriptor (Tiramisu+) result: $result for ${descriptor.characteristic?.uuid}")
                } else {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    val result = targetGatt.writeDescriptor(descriptor)
                    Log.d(TAG, "writeDescriptor (legacy) result: $result for ${descriptor.characteristic?.uuid}")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException during writeDescriptor", e)
                isWritingDescriptor = false
            } catch (e: Exception) {
                Log.e(TAG, "Exception during writeDescriptor", e)
                isWritingDescriptor = false
            }
        }
    }

    /**
     * Connects to a discovered BluetoothDevice.
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        Log.i(TAG, "Connecting to device: ${device.address}")
        _connectionState.value = BleConnectionState.Connecting
        activeDevice = device

        try {
            // Close any existing GATT instance
            closeGatt()

            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(
                    context,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied during connectGatt", e)
            _connectionState.value = BleConnectionState.Error("Bluetooth connect permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating connectGatt", e)
            _connectionState.value = BleConnectionState.Error("Connection error: ${e.localizedMessage}")
        }
    }

    /**
     * Disconnects from the current GATT server.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.i(TAG, "Disconnect requested")
        _connectionState.value = BleConnectionState.Disconnecting
        try {
            bluetoothGatt?.disconnect()
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied during disconnect", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }
    }

    /**
     * Cleans up and releases GATT resources.
     */
    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        synchronized(descriptorWriteQueue) {
            descriptorWriteQueue.clear()
            isWritingDescriptor = false
        }
        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GATT: ${e.message}")
        } finally {
            bluetoothGatt = null
        }
    }

    fun release() {
        disconnect()
        closeGatt()
    }
}
