package com.safenex.app

import android.Manifest
import android.app.KeyguardManager
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.safenex.app.domain.model.EmergencyState
import com.safenex.app.ui.screens.EmergencyScreen
import com.safenex.app.ui.screens.HomeScreen
import com.safenex.app.ui.screens.VerifySafetyScreen
import com.safenex.app.ui.theme.SafenexDarkBg
import com.safenex.app.ui.theme.SafenexTheme
import com.safenex.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Request permissions launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(
                this,
                "Permissions are required for SAFENEX emergency protection",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Bluetooth enable prompt launcher
    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Toast.makeText(
                this,
                "Bluetooth must be enabled to connect to SAFENEX Band",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupLockscreenWake()
        checkAndRequestPermissions()
        setupEmergencyKioskInterception()

        setContent {
            SafenexTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SafenexDarkBg
                ) {
                    SafenexAppNavigation(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setupLockscreenWake()
    }

    /**
     * Intercepts Android hardware & gesture back button during emergency.
     * Prevents exiting or navigating away until safety PIN 1234 is verified.
     */
    private fun setupEmergencyKioskInterception() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.emergencyState.value != EmergencyState.SAFE) {
                    Toast.makeText(
                        this@MainActivity,
                        "🚨 EMERGENCY LOCKED! PIN Verification (1234) required to disarm.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        // Observe emergency state and dynamically enforce Window flags
        lifecycleScope.launch {
            viewModel.emergencyState.collect { state ->
                if (state != EmergencyState.SAFE) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    setupLockscreenWake()
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    /**
     * Intercepts Home Button / App Switcher to prevent minimizing during active emergency.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (viewModel.emergencyState.value != EmergencyState.SAFE) {
            val bringToFrontIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(bringToFrontIntent)
        }
    }

    private fun setupLockscreenWake() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf<String>()

        // Location Permissions for real-time GPS tracking
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Emergency Alert Dispatch permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.SEND_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.CALL_PHONE)
        }

        // Audio & Camera Evidence permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.CAMERA)
        }

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ Bluetooth permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (requiredPermissions.isNotEmpty()) {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }

        // Check if Bluetooth is currently active
        val btManager = getSystemService(android.bluetooth.BluetoothManager::class.java)
        val btAdapter = btManager?.adapter
        if (btAdapter != null && !btAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableBtIntent)
        }
    }
}

@Composable
fun SafenexAppNavigation(viewModel: MainViewModel) {
    val emergencyState by viewModel.emergencyState.collectAsState()

    // Enforce back gesture lockout in Compose root
    BackHandler(enabled = emergencyState != EmergencyState.SAFE) {
        // Intentionally block back navigation while emergency is active
    }

    AnimatedContent(
        targetState = emergencyState,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "EmergencyStateTransition"
    ) { state ->
        when (state) {
            EmergencyState.SAFE -> {
                HomeScreen(viewModel = viewModel)
            }
            EmergencyState.EMERGENCY_ACTIVE -> {
                EmergencyScreen(viewModel = viewModel)
            }
            EmergencyState.VERIFYING, EmergencyState.SAFETY_VERIFIED -> {
                VerifySafetyScreen(viewModel = viewModel)
            }
        }
    }
}
