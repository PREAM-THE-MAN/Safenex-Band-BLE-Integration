package com.safenex.app.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safenex.app.data.guardian.Guardian
import com.safenex.app.domain.model.TriggerSource
import com.safenex.app.ui.components.BandStatusCard
import com.safenex.app.ui.components.DittuCard
import com.safenex.app.ui.components.SafenexHeader
import com.safenex.app.ui.components.StandbyProtectionCard
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
import com.safenex.app.ui.viewmodel.MainViewModel

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.safenex.app.data.schedule.SafetySchedule
import com.safenex.app.data.schedule.ScheduleStatus
import com.safenex.app.ui.theme.WarningOrange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val guardians by viewModel.guardians.collectAsState()
    val scheduleStatus by viewModel.scheduleStatus.collectAsState()
    val scrollState = rememberScrollState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showScheduleConfigDialog by remember { mutableStateOf(false) }

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
            Spacer(modifier = Modifier.height(8.dp))

            // App Brand Header
            SafenexHeader()

            Spacer(modifier = Modifier.height(16.dp))

            // 🚨 TOP BRIGHT RED ROUND SOS BUTTON 🚨
            TopRoundEmergencySosButton(
                onSosClick = {
                    viewModel.triggerSimulatedEmergency(TriggerSource.BUTTON)
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Primary SAFENEX Band Card
            BandStatusCard(
                connectionState = connectionState,
                scanState = scanState,
                onScanClick = { viewModel.startScan() },
                onDisconnectClick = { viewModel.disconnectBand() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Scheduled Commute Safety Card (DEAD-MAN'S SWITCH / COMMUTE GUARDIAN)
            CommuteSafetyScheduleCard(
                scheduleStatus = scheduleStatus,
                onConfigureClick = { showScheduleConfigDialog = true },
                onArriveClick = { viewModel.confirmSafeArrival() },
                onExtendClick = { viewModel.extendSafetySchedule(15) },
                onCancelClick = { viewModel.cancelSafetySchedule() },
                onTestCheckInClick = {
                    val current = scheduleStatus
                    if (current is ScheduleStatus.Active) {
                        viewModel.triggerCheckInNow(current.schedule)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // DITTU Status Card
            DittuCard(
                statusText = "Ready",
                isEmergency = false
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Emergency Protection Status Card
            StandbyProtectionCard()

            Spacer(modifier = Modifier.height(16.dp))

            // Guardians Management Card
            GuardiansCard(
                guardians = guardians,
                onAddClick = { showAddDialog = true },
                onDeleteClick = { viewModel.deleteGuardian(it) },
                onSetPrimary = { viewModel.setPrimaryGuardian(it) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Tactical Deterrent Mode Toggle Card
            val isDeterrentEnabled by viewModel.isDeterrentEnabled.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafenexSurface),
                border = BorderStroke(1.dp, if (isDeterrentEnabled) EmergencyRed.copy(alpha = 0.4f) else SafenexBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isDeterrentEnabled) EmergencyRed.copy(alpha = 0.15f) else SafenexBorder.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDeterrentEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null,
                                tint = if (isDeterrentEnabled) EmergencyRed else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (isDeterrentEnabled) "Audible Deterrent Mode" else "Silent Stealth Mode",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = if (isDeterrentEnabled) TextPrimary else TextSecondary
                            )
                            Text(
                                text = if (isDeterrentEnabled) "Strobe Torch LED & 1200Hz Siren ON" else "Silent covert recording & cloud sync",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 11.sp
                                ),
                                color = if (isDeterrentEnabled) EmergencyRed else TextMuted
                            )
                        }
                    }

                    Switch(
                        checked = isDeterrentEnabled,
                        onCheckedChange = { viewModel.setDeterrentMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextPrimary,
                            checkedTrackColor = EmergencyRed,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = SafenexSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DITTU AI Voice Assistant Card
            val isDittuListening by viewModel.isDittuListening.collectAsState()
            val isDittuVoiceWakeEnabled by viewModel.isDittuVoiceWakeEnabled.collectAsState()
            val dittuLastHeard by viewModel.dittuLastHeardPhrase.collectAsState()
            val dittuResponse by viewModel.dittuAssistantResponse.collectAsState()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafenexSurface),
                border = BorderStroke(1.dp, if (isDittuListening) CyanAccent.copy(alpha = 0.5f) else SafenexBorder)
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isDittuListening) CyanAccent.copy(alpha = 0.2f) else SafenexBorder.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isDittuListening) Icons.Default.Mic else Icons.Default.MicOff,
                                    contentDescription = null,
                                    tint = if (isDittuListening) CyanAccent else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "DITTU AI Assistant",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (isDittuListening) "LISTENING • Say \"HELP!\"" else "Voice Assistant Paused",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 11.sp
                                    ),
                                    color = if (isDittuListening) CyanAccent else TextMuted
                                )
                            }
                        }

                        Switch(
                            checked = isDittuVoiceWakeEnabled,
                            onCheckedChange = { viewModel.setDittuVoiceWake(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextPrimary,
                                checkedTrackColor = CyanAccent,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SafenexSurface
                            )
                        )
                    }

                    if (isDittuVoiceWakeEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Manual Push-to-Talk / Tap to Talk Action Button
                        Button(
                            onClick = { viewModel.activateDittuManually() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDittuListening) CyanAccent else SafenexDarkBg,
                                contentColor = if (isDittuListening) SafenexDarkBg else CyanAccent
                            ),
                            border = BorderStroke(1.dp, CyanAccent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isDittuListening) "LISTENING... SPEAK NOW" else "TAP TO TALK TO DITTU",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SafenexDarkBg.copy(alpha = 0.6f))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Column {
                                if (dittuLastHeard.isNotBlank()) {
                                    Text(
                                        text = "🎙️ Heard: \"$dittuLastHeard\"",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        ),
                                        color = CyanAccent
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Text(
                                    text = "🤖 DITTU: $dittuResponse",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 11.sp
                                    ),
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("\"HELP!\"", "\"Call Guardian\"", "\"Where am I?\"", "\"Share Location\"").forEach { cmd ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SafenexSurface)
                                        .border(BorderStroke(1.dp, SafenexBorder), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = cmd,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 9.sp),
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Test Simulation Panel (For testing SOS triggers without physical ESP32)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SafenexSurface.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TEST SIMULATION CONTROLS",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.triggerSimulatedEmergency(TriggerSource.BUTTON) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = EmergencyRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SOS Button",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = TextPrimary
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.triggerSimulatedEmergency(TriggerSource.SHAKE) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SOS Shake",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // 60-Second Check-In Prompt Modal Dialog (Overdue Dead-Man's Switch)
    if (scheduleStatus is ScheduleStatus.CheckInPrompt) {
        val prompt = scheduleStatus as ScheduleStatus.CheckInPrompt
        CheckInPromptModalDialog(
            schedule = prompt.schedule,
            secondsRemaining = prompt.secondsRemaining,
            onArriveClick = { viewModel.confirmSafeArrival() },
            onExtendClick = { viewModel.extendSafetySchedule(15) },
            onEmergencyClick = { viewModel.scheduleManager.triggerEmergencyNow() }
        )
    }

    if (showAddDialog) {
        AddGuardianDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, phone, relation, isPrimary ->
                viewModel.addGuardian(name, phone, relation, isPrimary)
                showAddDialog = false
            }
        )
    }

    if (showScheduleConfigDialog) {
        ScheduleConfigDialog(
            onDismiss = { showScheduleConfigDialog = false },
            onStartSchedule = { destination, arrivalMs, notifyGuardian ->
                viewModel.startSafetySchedule(
                    title = "Commute to $destination",
                    destination = destination,
                    arrivalEpochMs = arrivalMs,
                    notifyGuardian = notifyGuardian
                )
                showScheduleConfigDialog = false
            }
        )
    }
}

@Composable
fun GuardiansCard(
    guardians: List<Guardian>,
    onAddClick: () -> Unit,
    onDeleteClick: (String) -> Unit,
    onSetPrimary: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SafenexSurface),
        border = BorderStroke(1.dp, SafenexBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyanAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Emergency Guardians",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = TextPrimary
                        )
                        Text(
                            text = "${guardians.size} Contact${if (guardians.size != 1) "s" else ""} Configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }

                Button(
                    onClick = onAddClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent.copy(alpha = 0.15f),
                        contentColor = CyanAccent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ADD",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (guardians.isEmpty()) {
                Text(
                    text = "No emergency guardians added yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    guardians.forEach { guardian ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SafenexSurfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (guardian.isPrimary) Icons.Default.Star else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (guardian.isPrimary) SafetyGreen else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = guardian.name,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            ),
                                            color = TextPrimary
                                        )
                                        if (guardian.isPrimary) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "PRIMARY",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    color = SafetyGreen
                                                )
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${guardian.relationship} • ${guardian.phoneNumber}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 12.sp
                                        ),
                                        color = TextMuted
                                    )
                                }
                            }

                            Row {
                                if (!guardian.isPrimary) {
                                    TextButton(
                                        onClick = { onSetPrimary(guardian.id) }
                                    ) {
                                        Text(
                                            text = "Set Primary",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 11.sp,
                                                color = CyanAccent
                                            )
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteClick(guardian.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = EmergencyRed.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddGuardianDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, relation: String, isPrimary: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Family") }
    var isPrimary by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SafenexSurface,
        title = {
            Text(
                text = "Add Emergency Guardian",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Guardian Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = SafenexBorder
                    )
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = SafenexBorder
                    )
                )

                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("Relationship (e.g. Mom, Friend)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = SafenexBorder
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Checkbox(
                        checked = isPrimary,
                        onCheckedChange = { isPrimary = it },
                        colors = CheckboxDefaults.colors(checkedColor = SafetyGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Set as Primary Guardian (1-Tap Call)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        onConfirm(name, phone, relationship, isPrimary)
                    }
                },
                enabled = name.isNotBlank() && phone.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = SafenexDarkBg)
            ) {
                Text("SAVE", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextMuted)
            }
        }
    )
}

/**
 * Prominent Bright Red Round SOS Emergency Button at the top of the Home Screen.
 */
@Composable
fun TopRoundEmergencySosButton(
    onSosClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Outer pulsing aura rings with central round bright red SOS button
        Box(
            modifier = Modifier.size(164.dp),
            contentAlignment = Alignment.Center
        ) {
            // Halo Ring 1 (Soft glow)
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .clip(CircleShape)
                    .background(Color(0x1FFF1744))
            )

            // Halo Ring 2 (Medium pulse ring)
            Box(
                modifier = Modifier
                    .size(146.dp)
                    .clip(CircleShape)
                    .background(Color(0x38FF1744))
                    .border(BorderStroke(1.5.dp, Color(0x66FF1744)), CircleShape)
            )

            // Core Bright Red Round SOS Button
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = CircleShape,
                        spotColor = Color(0xFFFF1744),
                        ambientColor = Color(0xFFFF1744)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF334B),
                                Color(0xFFFF0033),
                                Color(0xFFD50000),
                                Color(0xFF990000)
                            )
                        )
                    )
                    .border(BorderStroke(3.5.dp, Color(0xFFFF9EAA)), CircleShape)
                    .clickable { onSosClick() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ElectricBolt,
                        contentDescription = "SOS Alert",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "SOS",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp,
                            letterSpacing = 2.5.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "PRESS FOR HELP",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 8.5.sp,
                            letterSpacing = 1.2.sp
                        ),
                        color = Color.White.copy(alpha = 0.95f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(EmergencyRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "EMERGENCY SOS • TAP TO ACTIVATE PROTOCOL",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                ),
                color = EmergencyRed
            )
        }
        Text(
            text = "Activates siren, strobe, live satellite GPS, video evidence & alerts",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp
            ),
            color = TextMuted
        )
    }
}

/**
 * Scheduled Commute Safety Card (Dead-Man's Switch Guardian)
 */
@Composable
fun CommuteSafetyScheduleCard(
    scheduleStatus: ScheduleStatus,
    onConfigureClick: () -> Unit,
    onArriveClick: () -> Unit,
    onExtendClick: () -> Unit,
    onCancelClick: () -> Unit,
    onTestCheckInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SafenexSurface),
        border = BorderStroke(
            1.dp,
            when (scheduleStatus) {
                is ScheduleStatus.Active -> CyanAccent.copy(alpha = 0.6f)
                is ScheduleStatus.CheckInPrompt -> WarningOrange
                is ScheduleStatus.Completed -> SafetyGreen
                ScheduleStatus.Idle -> SafenexBorder
            }
        )
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                when (scheduleStatus) {
                                    is ScheduleStatus.Active -> CyanAccent.copy(alpha = 0.15f)
                                    is ScheduleStatus.CheckInPrompt -> WarningOrange.copy(alpha = 0.2f)
                                    is ScheduleStatus.Completed -> SafetyGreen.copy(alpha = 0.15f)
                                    ScheduleStatus.Idle -> SafenexBorder.copy(alpha = 0.3f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (scheduleStatus) {
                                is ScheduleStatus.Active -> Icons.Default.Schedule
                                is ScheduleStatus.CheckInPrompt -> Icons.Default.Warning
                                is ScheduleStatus.Completed -> Icons.Default.CheckCircle
                                ScheduleStatus.Idle -> Icons.Default.Schedule
                            },
                            contentDescription = null,
                            tint = when (scheduleStatus) {
                                is ScheduleStatus.Active -> CyanAccent
                                is ScheduleStatus.CheckInPrompt -> WarningOrange
                                is ScheduleStatus.Completed -> SafetyGreen
                                ScheduleStatus.Idle -> TextMuted
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Commute Safety Mode",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = TextPrimary
                        )
                        Text(
                            text = when (scheduleStatus) {
                                is ScheduleStatus.Active -> "Tracking active commute..."
                                is ScheduleStatus.CheckInPrompt -> "⚠️ Overdue! Check-in pending"
                                is ScheduleStatus.Completed -> "Safe arrival confirmed"
                                ScheduleStatus.Idle -> "Dead-man's arrival safety switch"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            color = when (scheduleStatus) {
                                is ScheduleStatus.Active -> CyanAccent
                                is ScheduleStatus.CheckInPrompt -> WarningOrange
                                is ScheduleStatus.Completed -> SafetyGreen
                                ScheduleStatus.Idle -> TextMuted
                            }
                        )
                    }
                }

                // Status chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (scheduleStatus) {
                                is ScheduleStatus.Active -> CyanAccent.copy(alpha = 0.15f)
                                is ScheduleStatus.CheckInPrompt -> WarningOrange.copy(alpha = 0.15f)
                                is ScheduleStatus.Completed -> SafetyGreen.copy(alpha = 0.15f)
                                ScheduleStatus.Idle -> SafenexBorder.copy(alpha = 0.3f)
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = when (scheduleStatus) {
                            is ScheduleStatus.Active -> "MONITORING"
                            is ScheduleStatus.CheckInPrompt -> "CHECK-IN"
                            is ScheduleStatus.Completed -> "ARRIVED"
                            ScheduleStatus.Idle -> "STANDBY"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (scheduleStatus) {
                                is ScheduleStatus.Active -> CyanAccent
                                is ScheduleStatus.CheckInPrompt -> WarningOrange
                                is ScheduleStatus.Completed -> SafetyGreen
                                ScheduleStatus.Idle -> TextMuted
                            }
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (scheduleStatus) {
                is ScheduleStatus.Active -> {
                    val schedule = scheduleStatus.schedule
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SafenexSurfaceVariant)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "DESTINATION: ${schedule.destination.uppercase()}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Expected by: ${schedule.formattedArrivalTime}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                    color = TextSecondary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = schedule.formatRemainingTime(),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    ),
                                    color = CyanAccent
                                )
                                Text(
                                    text = "REMAINING",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 9.sp),
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. I've Arrived Button
                        Button(
                            onClick = onArriveClick,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SafetyGreen,
                                contentColor = SafenexDarkBg
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "I'VE ARRIVED",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        // 2. Extend +15m Button
                        OutlinedButton(
                            onClick = onExtendClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CyanAccent),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                        ) {
                            Text(
                                text = "+15 MINS",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        // 3. Cancel Button
                        OutlinedButton(
                            onClick = onCancelClick,
                            modifier = Modifier
                                .weight(0.8f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SafenexBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                        ) {
                            Text(
                                text = "CANCEL",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = onTestCheckInClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚡ Simulate Arrival Exceeded (Test 60s Check-In)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        )
                    }
                }
                is ScheduleStatus.Completed -> {
                    val schedule = scheduleStatus.schedule
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SafetyGreen.copy(alpha = 0.1f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "✅ Safe arrival at ${schedule.destination} confirmed!",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SafetyGreen
                            )
                        )
                        Text(
                            text = "Primary guardian notified via SMS. Commute completed.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onConfigureClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafenexSurfaceVariant)
                    ) {
                        Text(
                            text = "+ SET NEW COMMUTE SCHEDULE",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        )
                    }
                }
                else -> {
                    Text(
                        text = "Set your travel start and expected return time (e.g. 8:00 AM – 8:00 PM). If you don't check in within 60s of arrival time, SAFENEX automatically activates Emergency SOS!",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onConfigureClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanAccent,
                            contentColor = SafenexDarkBg
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SET COMMUTE SAFETY SCHEDULE",
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
    }
}

/**
 * 60-Second Overdue Check-In Modal Alert Dialog
 */
@Composable
fun CheckInPromptModalDialog(
    schedule: SafetySchedule,
    secondsRemaining: Int,
    onArriveClick: () -> Unit,
    onExtendClick: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* Cannot dismiss without action */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SafenexSurface),
            border = BorderStroke(2.dp, WarningOrange)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(WarningOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarningOrange,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "COMMUTE SAFETY CHECK-IN",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = WarningOrange
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Expected at ${schedule.destination} by ${schedule.formattedArrivalTime}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Pulsing Countdown Box
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmergencyRed.copy(alpha = 0.15f))
                        .border(BorderStroke(1.dp, EmergencyRed), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "00:${String.format("%02d", secondsRemaining)}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            ),
                            color = EmergencyRed
                        )
                        Text(
                            text = "AUTO-EMERGENCY IN ${secondsRemaining}s",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = EmergencyRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action 1: Arrived Safely
                Button(
                    onClick = onArriveClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafetyGreen,
                        contentColor = SafenexDarkBg
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I'VE ARRIVED SAFELY",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action 2: Extend +15 Mins
                OutlinedButton(
                    onClick = onExtendClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CyanAccent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EXTEND +15 MINUTES",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action 3: Trigger SOS Now
                Button(
                    onClick = onEmergencyClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmergencyRed,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ElectricBolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TRIGGER EMERGENCY SOS NOW",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Schedule Configuration Dialog
 */
@Composable
fun ScheduleConfigDialog(
    onDismiss: () -> Unit,
    onStartSchedule: (destination: String, arrivalEpochMs: Long, notifyGuardian: Boolean) -> Unit
) {
    var destination by remember { mutableStateOf("Home") }
    var selectedPresetMinutes by remember { mutableStateOf(60) } // Default 1 hour
    var notifyGuardianOnArrival by remember { mutableStateOf(true) }

    val presetOptions = listOf(
        "15m (Test)" to 15,
        "30 Mins" to 30,
        "1 Hour" to 60,
        "2 Hours" to 120,
        "4 Hours" to 240,
        "8:00 PM" to getMinutesUntil8PM()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SafenexSurface,
        title = {
            Text(
                text = "Set Commute Safety Schedule",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Destination:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    placeholder = { Text("e.g. Home, Office, Gym", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = SafenexBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Home", "Work", "Gym", "College").forEach { preset ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (destination == preset) CyanAccent.copy(alpha = 0.2f) else SafenexSurfaceVariant)
                                .border(BorderStroke(1.dp, if (destination == preset) CyanAccent else SafenexBorder), RoundedCornerShape(6.dp))
                                .clickable { destination = preset }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                color = if (destination == preset) CyanAccent else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Expected Arrival Time:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presetOptions.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { (label, minutes) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedPresetMinutes == minutes) CyanAccent.copy(alpha = 0.2f) else SafenexSurfaceVariant)
                                        .border(BorderStroke(1.dp, if (selectedPresetMinutes == minutes) CyanAccent else SafenexBorder), RoundedCornerShape(8.dp))
                                        .clickable { selectedPresetMinutes = minutes }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (selectedPresetMinutes == minutes) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (selectedPresetMinutes == minutes) CyanAccent else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                val calculatedArrival = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(
                    System.currentTimeMillis() + (selectedPresetMinutes * 60 * 1000L)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Expected Arrival: $calculatedArrival (in $selectedPresetMinutes mins)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = CyanAccent
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = notifyGuardianOnArrival,
                        onCheckedChange = { notifyGuardianOnArrival = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = SafetyGreen,
                            checkmarkColor = SafenexDarkBg,
                            uncheckedColor = TextMuted
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Send Safe Arrival SMS to Guardian",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextPrimary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val arrivalEpoch = System.currentTimeMillis() + (selectedPresetMinutes * 60 * 1000L)
                    onStartSchedule(destination, arrivalEpoch, notifyGuardianOnArrival)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanAccent,
                    contentColor = SafenexDarkBg
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "START SCHEDULE",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextMuted)
            }
        }
    )
}

fun getMinutesUntil8PM(): Int {
    val cal = Calendar.getInstance()
    val now = cal.timeInMillis
    cal.set(Calendar.HOUR_OF_DAY, 20)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    if (cal.timeInMillis <= now) {
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    val diffMs = cal.timeInMillis - now
    return (diffMs / (60 * 1000)).toInt().coerceAtLeast(15)
}
