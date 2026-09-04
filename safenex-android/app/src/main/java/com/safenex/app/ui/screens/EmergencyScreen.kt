package com.safenex.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safenex.app.data.guardian.AlertStatus
import com.safenex.app.data.guardian.GuardianAlert
import com.safenex.app.data.location.LocationState
import com.safenex.app.data.location.SafenexLocation
import com.safenex.app.domain.model.BleConnectionState
import com.safenex.app.domain.model.TriggerSource
import com.safenex.app.ui.components.PulseBeacon
import com.safenex.app.ui.theme.CyanAccent
import com.safenex.app.ui.theme.EmergencyRed
import com.safenex.app.ui.theme.SafenexBorder
import com.safenex.app.ui.theme.SafenexDarkBg
import com.safenex.app.ui.theme.SafenexSurface
import com.safenex.app.ui.theme.SafenexSurfaceVariant
import com.safenex.app.ui.theme.SafetyGreen
import com.safenex.app.ui.theme.TextMuted
import com.safenex.app.ui.theme.TextPrimary
import com.safenex.app.ui.theme.TextSecondary
import com.safenex.app.ui.theme.WarningOrange
import com.safenex.app.ui.viewmodel.MainViewModel

@Composable
fun EmergencyScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val emergencyEvent by viewModel.activeEmergencyEvent.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val locationState by viewModel.locationState.collectAsState()
    val guardianAlerts by viewModel.alerts.collectAsState()
    val guardians by viewModel.guardians.collectAsState()
    val scrollState = rememberScrollState()

    val isBandConnected = connectionState is BleConnectionState.Connected
    val triggerSource = emergencyEvent?.source ?: TriggerSource.BUTTON

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SafenexDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Animated Visual Emergency Beacon
            PulseBeacon(
                color = EmergencyRed,
                size = 80.dp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Emergency Banner Header
            Text(
                text = "!! EMERGENCY MODE !!",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = EmergencyRed
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SAFENEX BAND ACTIVATED",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Disconnection Warning Banner (if band disconnected while emergency active)
            if (!isBandConnected) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Band connection lost. Emergency session remains active.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = WarningOrange
                            )
                        )
                    }
                }
            }

            // 1. Trigger Card
            EmergencyInfoCard(
                icon = when (triggerSource) {
                    TriggerSource.BUTTON -> Icons.Default.ElectricBolt
                    TriggerSource.VOICE -> Icons.Default.SmartToy
                    TriggerSource.SCHEDULE_OVERDUE -> Icons.Default.Schedule
                    else -> Icons.Default.Vibration
                },
                label = "Trigger:",
                value = when (triggerSource) {
                    TriggerSource.BUTTON -> "Button"
                    TriggerSource.SHAKE -> "Vigorous Shake"
                    TriggerSource.VOICE -> "Voice (DITTU \"HELP!\")"
                    TriggerSource.SCHEDULE_OVERDUE -> "Commute Overdue (60s Timeout)"
                    TriggerSource.UNKNOWN -> "Band SOS Alert"
                },
                accentColor = EmergencyRed
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Band Connection Card
            EmergencyInfoCard(
                icon = Icons.Default.Security,
                label = "Band:",
                value = if (isBandConnected) "CONNECTED" else "DISCONNECTED",
                valueColor = if (isBandConnected) SafetyGreen else WarningOrange
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Tactical Deterrent Card (Strobe & Siren) - Security Locked
            val isDeterrentActive by viewModel.isDeterrentActive.collectAsState()
            val isDeterrentEnabled by viewModel.isDeterrentEnabled.collectAsState()
            TacticalDeterrentStatusCard(
                isActive = isDeterrentActive,
                isEnabled = isDeterrentEnabled
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Dynamic Live Google Maps & GPS Location Card with WhatsApp Share
            LiveLocationCard(
                locationState = locationState,
                onOpenMaps = { loc ->
                    val intent = viewModel.openMapsIntent(loc)
                    context.startActivity(intent)
                },
                onShareWhatsApp = {
                    viewModel.shareLocationViaWhatsApp()
                },
                onOpenSettings = {
                    val intent = viewModel.locationManager.getEnableLocationSettingsIntent()
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Dynamic Guardian Alerts Card & Direct Calling
            GuardianAlertsCard(
                alerts = guardianAlerts,
                totalGuardians = guardians.size,
                onCallPrimary = {
                    val intent = viewModel.callPrimaryGuardianIntent()
                    if (intent != null) {
                        context.startActivity(intent)
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 6. Audio Evidence Recording Card
            val audioRecordingState by viewModel.audioRecordingState.collectAsState()
            AudioEvidenceCard(state = audioRecordingState)

            Spacer(modifier = Modifier.height(10.dp))

            // 7. Camera Evidence Snapshots Card
            val cameraEvidenceState by viewModel.cameraEvidenceState.collectAsState()
            CameraEvidenceCard(state = cameraEvidenceState)

            Spacer(modifier = Modifier.height(10.dp))

            // 8. Video Evidence Recording Card
            val videoRecordingState by viewModel.videoRecordingState.collectAsState()
            VideoEvidenceCard(state = videoRecordingState)

            Spacer(modifier = Modifier.height(10.dp))

            // 9. Cloud Database & Real-Time GPS Breadcrumb Sync Card
            val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsState()
            val streamedFixes by viewModel.streamedFixesCount.collectAsState()
            val (cloudText, cloudColor) = when (cloudSyncStatus) {
                com.safenex.app.data.cloud.CloudSyncStatus.SYNCED -> {
                    if (streamedFixes > 0) Pair("SYNCED ($streamedFixes GPS fixes)", SafetyGreen)
                    else Pair("INCIDENT LOGGED", SafetyGreen)
                }
                com.safenex.app.data.cloud.CloudSyncStatus.SYNCING -> Pair("STREAMING GPS...", CyanAccent)
                com.safenex.app.data.cloud.CloudSyncStatus.ERROR -> Pair("SYNC RETRYING", WarningOrange)
                com.safenex.app.data.cloud.CloudSyncStatus.OFFLINE -> Pair("LOCAL ONLY", TextMuted)
                com.safenex.app.data.cloud.CloudSyncStatus.IDLE -> Pair("STANDBY", TextMuted)
            }
            EmergencyInfoCard(
                icon = Icons.Default.Security,
                label = "Cloud Database:",
                value = cloudText,
                valueColor = cloudColor
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 10. DITTU Voice Assistant Emergency Card
            val isDittuListening by viewModel.isDittuListening.collectAsState()
            val dittuLastHeard by viewModel.dittuLastHeardPhrase.collectAsState()
            val dittuResponse by viewModel.dittuAssistantResponse.collectAsState()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SafenexSurface),
                border = BorderStroke(1.dp, if (isDittuListening) CyanAccent.copy(alpha = 0.4f) else SafenexBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(CyanAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "DITTU Voice:",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = TextSecondary
                            )
                            Text(
                                text = if (dittuLastHeard.isNotBlank()) "Heard: \"$dittuLastHeard\"" else "Listening for \"Call Guardian\"...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 10.sp
                                ),
                                color = if (dittuLastHeard.isNotBlank()) CyanAccent else TextMuted
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { viewModel.activateDittuManually() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = CyanAccent
                            ),
                            border = BorderStroke(1.dp, CyanAccent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDittuListening) "SPEAK" else "TALK",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Prominent Main Action Button: VERIFY SAFETY
            Button(
                onClick = { viewModel.startVerification() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SafetyGreen,
                    contentColor = SafenexDarkBg
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Text(
                    text = "VERIFY SAFETY",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun LiveLocationCard(
    locationState: LocationState,
    onOpenMaps: (SafenexLocation) -> Unit,
    onShareWhatsApp: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SafenexSurface),
        border = BorderStroke(1.dp, SafenexBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CyanAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Live Google Map & GPS",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                }

                // Status chip
                when (locationState) {
                    is LocationState.Available -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(SafetyGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ACCURATE ±${locationState.location.accuracy.toInt()}m",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SafetyGreen
                                )
                            )
                        }
                    }
                    is LocationState.Fetching -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = CyanAccent
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LOCATING...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent
                                )
                            )
                        }
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (locationState) {
                is LocationState.Available -> {
                    val loc = locationState.location
                    val currentLatLng = LatLng(loc.latitude, loc.longitude)
                    val cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(currentLatLng, 16.5f)
                    }

                    LaunchedEffect(loc.latitude, loc.longitude) {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(currentLatLng, 16.5f)
                    }

                    // Embedded Google Map View
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, SafenexBorder), RoundedCornerShape(12.dp))
                    ) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(
                                isMyLocationEnabled = false,
                                mapType = MapType.NORMAL
                            ),
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                compassEnabled = true,
                                myLocationButtonEnabled = false,
                                mapToolbarEnabled = false
                            )
                        ) {
                            Marker(
                                state = MarkerState(position = currentLatLng),
                                title = "Emergency Location",
                                snippet = loc.address ?: loc.formattedCoordinates
                            )
                            Circle(
                                center = currentLatLng,
                                radius = loc.accuracy.toDouble().coerceAtLeast(5.0),
                                fillColor = Color(0x3300E5FF),
                                strokeColor = CyanAccent,
                                strokeWidth = 2f
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column {
                        if (!loc.address.isNullOrBlank()) {
                            Text(
                                text = loc.address,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Text(
                            text = loc.formattedCoordinates,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // WhatsApp Location Share Action Button
                        Button(
                            onClick = onShareWhatsApp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF25D366),
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SHARE LIVE LOCATION ON WHATSAPP",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { onOpenMaps(loc) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "EXPAND ON GOOGLE MAPS APP",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }
                }
                is LocationState.Fetching -> {
                    Text(
                        text = "Acquiring high-accuracy satellite & network location...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
                is LocationState.Unavailable -> {
                    Column {
                        Text(
                            text = locationState.reason,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = WarningOrange,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningOrange)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "OPEN LOCATION SETTINGS",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
                LocationState.Idle -> {
                    Text(
                        text = "Location tracking idle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun GuardianAlertsCard(
    alerts: List<GuardianAlert>,
    totalGuardians: Int,
    onCallPrimary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SafenexSurface),
        border = BorderStroke(1.dp, SafenexBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = EmergencyRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Guardian Emergency Alerts",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                }

                val allSent = alerts.isNotEmpty() && alerts.all { it.status == AlertStatus.SENT }
                if (allSent) {
                    Text(
                        text = "ALL ALERTS SENT",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = SafetyGreen
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (alerts.isEmpty()) {
                Text(
                    text = if (totalGuardians == 0) "No guardians configured." else "Dispatching alert messages...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    alerts.forEach { alert ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SafenexSurfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${alert.guardian.name} (${alert.guardian.relationship})",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                color = TextPrimary
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (alert.status) {
                                    AlertStatus.SENT -> {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = SafetyGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "ALERT SENT",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = SafetyGreen
                                            )
                                        )
                                    }
                                    AlertStatus.SENDING -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = CyanAccent
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "SENDING...",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = CyanAccent
                                            )
                                        )
                                    }
                                    AlertStatus.FAILED -> {
                                        Icon(
                                            imageVector = Icons.Default.Error,
                                            contentDescription = null,
                                            tint = EmergencyRed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "FAILED",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = EmergencyRed
                                            )
                                        )
                                    }
                                    AlertStatus.IDLE -> {}
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1-Tap Direct Call to Primary Guardian
            Button(
                onClick = onCallPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmergencyRed,
                    contentColor = TextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CALL PRIMARY GUARDIAN",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun EmergencyInfoCard(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color = CyanAccent,
    valueColor: Color = TextPrimary
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SafenexSurface),
        border = BorderStroke(1.dp, SafenexBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextSecondary
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = valueColor
            )
        }
    }
}

@Composable
fun AudioEvidenceCard(
    state: com.safenex.app.data.audio.AudioRecordingState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SafenexSurface),
        border = BorderStroke(1.dp, SafenexBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (state is com.safenex.app.data.audio.AudioRecordingState.Recording)
                                EmergencyRed.copy(alpha = 0.15f)
                            else
                                CyanAccent.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state is com.safenex.app.data.audio.AudioRecordingState.Recording)
                            Icons.Default.Mic
                        else
                            Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (state is com.safenex.app.data.audio.AudioRecordingState.Recording)
                            EmergencyRed
                        else
                            CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Audio Evidence:",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = TextSecondary
                    )
                    Text(
                        text = "AAC High-Clarity • 128 kbps",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 10.sp
                        ),
                        color = TextMuted
                    )
                }
            }

            when (state) {
                is com.safenex.app.data.audio.AudioRecordingState.Recording -> {
                    val minutes = state.durationSeconds / 60
                    val seconds = state.durationSeconds % 60
                    val timeStr = String.format("%02d:%02d", minutes, seconds)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(EmergencyRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "REC $timeStr",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = EmergencyRed
                            )
                        )
                    }
                }
                is com.safenex.app.data.audio.AudioRecordingState.Saved -> {
                    val minutes = state.durationSeconds / 60
                    val seconds = state.durationSeconds % 60
                    val timeStr = String.format("%02d:%02d", minutes, seconds)
                    Text(
                        text = "SAVED ($timeStr)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = SafetyGreen
                        )
                    )
                }
                is com.safenex.app.data.audio.AudioRecordingState.Error -> {
                    Text(
                        text = "AUDIO ERROR",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = WarningOrange
                        )
                    )
                }
                com.safenex.app.data.audio.AudioRecordingState.Idle -> {
                    Text(
                        text = "STANDBY",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun CameraEvidenceCard(
    state: com.safenex.app.data.camera.CameraEvidenceState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SafenexSurface),
        border = BorderStroke(1.dp, SafenexBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (state is com.safenex.app.data.camera.CameraEvidenceState.Captured)
                                SafetyGreen.copy(alpha = 0.15f)
                            else
                                CyanAccent.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = if (state is com.safenex.app.data.camera.CameraEvidenceState.Captured)
                            SafetyGreen
                        else
                            CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Camera Evidence:",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = TextSecondary
                    )
                    Text(
                        text = "Dual-Camera (Front & Rear)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 10.sp
                        ),
                        color = TextMuted
                    )
                }
            }

            when (state) {
                is com.safenex.app.data.camera.CameraEvidenceState.Capturing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = CyanAccent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CAPTURING...",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = CyanAccent
                            )
                        )
                    }
                }
                is com.safenex.app.data.camera.CameraEvidenceState.Captured -> {
                    Text(
                        text = "${state.count} PHOTOS CAPTURED ☁️",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = SafetyGreen
                        )
                    )
                }
                is com.safenex.app.data.camera.CameraEvidenceState.Error -> {
                    Text(
                        text = "CAMERA ERROR",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = WarningOrange
                        )
                    )
                }
                com.safenex.app.data.camera.CameraEvidenceState.Idle -> {
                    Text(
                        text = "STANDBY",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun VideoEvidenceCard(
    state: com.safenex.app.data.video.VideoRecordingState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SafenexSurface),
        border = BorderStroke(1.dp, SafenexBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (state is com.safenex.app.data.video.VideoRecordingState.Recording)
                                EmergencyRed.copy(alpha = 0.15f)
                            else
                                CyanAccent.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = if (state is com.safenex.app.data.video.VideoRecordingState.Recording)
                            EmergencyRed
                        else
                            CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Video Evidence:",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = TextSecondary
                    )
                    Text(
                        text = "MP4 720p • H.264 Audio/Video",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 10.sp
                        ),
                        color = TextMuted
                    )
                }
            }

            when (state) {
                is com.safenex.app.data.video.VideoRecordingState.Recording -> {
                    val minutes = state.durationSeconds / 60
                    val seconds = state.durationSeconds % 60
                    val timeStr = String.format("%02d:%02d", minutes, seconds)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(EmergencyRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "REC $timeStr",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = EmergencyRed
                            )
                        )
                    }
                }
                is com.safenex.app.data.video.VideoRecordingState.Saved -> {
                    val minutes = state.durationSeconds / 60
                    val seconds = state.durationSeconds % 60
                    val timeStr = String.format("%02d:%02d", minutes, seconds)
                    Text(
                        text = "SAVED ($timeStr)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = SafetyGreen
                        )
                    )
                }
                is com.safenex.app.data.video.VideoRecordingState.Error -> {
                    Text(
                        text = "VIDEO ERROR",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = WarningOrange
                        )
                    )
                }
                com.safenex.app.data.video.VideoRecordingState.Idle -> {
                    Text(
                        text = "STANDBY",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun TacticalDeterrentStatusCard(
    isActive: Boolean,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SafenexSurface),
        border = BorderStroke(1.dp, if (isActive) EmergencyRed.copy(alpha = 0.5f) else SafenexBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isActive) EmergencyRed.copy(alpha = 0.15f) else SafenexBorder.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = null,
                        tint = if (isActive) EmergencyRed else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Tactical Deterrent:",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = TextSecondary
                    )
                    Text(
                        text = if (isActive) "1200Hz Siren + Strobe LED Active" else if (!isEnabled) "Silent Stealth Mode Active" else "Deterrent Standby",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 10.sp
                        ),
                        color = if (isActive) EmergencyRed else TextMuted
                    )
                }
            }

            if (isActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmergencyRed.copy(alpha = 0.2f))
                        .border(BorderStroke(1.dp, EmergencyRed), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "LOCKED (PIN REQ)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = EmergencyRed
                        )
                    )
                }
            } else {
                Text(
                    text = if (isEnabled) "ARMED" else "STEALTH",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isEnabled) CyanAccent else TextMuted
                    )
                )
            }
        }
    }
}


