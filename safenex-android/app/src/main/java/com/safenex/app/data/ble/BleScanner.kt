package com.safenex.app.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.safenex.app.domain.model.ScanState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages targeted BLE discovery of the SAFENEX-BAND device with automated timeouts and deduplication.
 */
class BleScanner(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    companion object {
        private const val TAG = "SAFENEX_BleScanner"
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var scanner: BluetoothLeScanner? = null
    private var scanTimeoutJob: Job? = null
    private var isScanningInternal = false

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device: BluetoothDevice = result.device ?: return
            val name = try {
                device.name ?: result.scanRecord?.deviceName
            } catch (e: SecurityException) {
                result.scanRecord?.deviceName
            }

            Log.d(TAG, "Scanned device: name='$name', address='${device.address}', rssi=${result.rssi}")

            // Match specifically for SAFENEX-BAND (or service UUID in advertised records)
            val matchesName = name?.contains(BleConstants.DEVICE_NAME, ignoreCase = true) == true
            val matchesService = result.scanRecord?.serviceUuids?.any {
                it.uuid == BleConstants.SERVICE_UUID
            } == true

            if (matchesName || matchesService) {
                Log.i(TAG, "Target SAFENEX-BAND found: ${device.address}")
                stopScan()
                _scanState.value = ScanState.DeviceFound(
                    device = device,
                    name = name ?: BleConstants.DEVICE_NAME,
                    rssi = result.rssi
                )
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            val reason = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE Scan unsupported"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal Bluetooth error"
                else -> "Scan failed (code $errorCode)"
            }
            Log.e(TAG, "BLE scan failed: $reason")
            isScanningInternal = false
            scanTimeoutJob?.cancel()
            _scanState.value = ScanState.Failed(reason)
        }
    }

    /**
     * Checks if Bluetooth is physically enabled on the device.
     */
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /**
     * Initiates a targeted scan for SAFENEX-BAND with automatic timeout.
     */
    @SuppressLint("MissingPermission")
    fun startScan(timeoutMs: Long = BleConstants.SCAN_TIMEOUT_MS) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _scanState.value = ScanState.Failed("Bluetooth is disabled")
            return
        }

        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _scanState.value = ScanState.Failed("Bluetooth LE Scanner unavailable")
            return
        }

        if (isScanningInternal) {
            stopScan()
        }

        _scanState.value = ScanState.Scanning
        isScanningInternal = true

        try {
            // Build filters: filter by name or accept all for manual inspection
            val filters = mutableListOf<ScanFilter>()
            val nameFilter = ScanFilter.Builder()
                .setDeviceName(BleConstants.DEVICE_NAME)
                .build()
            val serviceFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
                .build()
            filters.add(nameFilter)
            filters.add(serviceFilter)

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()

            // To maximize discovery reliability across different ESP32 advertisement formats,
            // we scan with settings and do custom matching in callback as well.
            scanner?.startScan(null, settings, scanCallback)
            Log.i(TAG, "BLE scan started, timeout in ${timeoutMs}ms")

            // Setup scan timeout job
            scanTimeoutJob?.cancel()
            scanTimeoutJob = scope.launch {
                delay(timeoutMs)
                if (isScanningInternal && _scanState.value is ScanState.Scanning) {
                    Log.w(TAG, "Scan timed out without finding SAFENEX-BAND")
                    stopScan()
                    _scanState.value = ScanState.NotFound
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied when starting BLE scan", e)
            isScanningInternal = false
            _scanState.value = ScanState.Failed("Bluetooth scan permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting BLE scan", e)
            isScanningInternal = false
            _scanState.value = ScanState.Failed("Scan error: ${e.localizedMessage}")
        }
    }

    /**
     * Stops the active BLE scan.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanTimeoutJob?.cancel()
        if (isScanningInternal) {
            try {
                scanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            } finally {
                isScanningInternal = false
            }
        }
    }

    /**
     * Resets scan state back to Idle.
     */
    fun reset() {
        stopScan()
        _scanState.value = ScanState.Idle
    }
}
