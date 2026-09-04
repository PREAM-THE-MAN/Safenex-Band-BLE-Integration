package com.safenex.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safenex.app.domain.model.BleConnectionState
import com.safenex.app.domain.model.ScanState
import com.safenex.app.ui.theme.CyanAccent
import com.safenex.app.ui.theme.EmergencyRed
import com.safenex.app.ui.theme.SafenexBorder
import com.safenex.app.ui.theme.SafenexSurface
import com.safenex.app.ui.theme.SafenexSurfaceVariant
import com.safenex.app.ui.theme.SafetyGreen
import com.safenex.app.ui.theme.TextMuted
import com.safenex.app.ui.theme.TextPrimary
import com.safenex.app.ui.theme.TextSecondary
import com.safenex.app.ui.theme.WarningOrange

@Composable
fun BandStatusCard(
    connectionState: BleConnectionState,
    scanState: ScanState,
    onScanClick: () -> Unit,
    onDisconnectClick: () -> Unit,
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
                    val icon: ImageVector = when (connectionState) {
                        is BleConnectionState.Connected -> Icons.Default.BluetoothConnected
                        is BleConnectionState.Connecting -> Icons.Default.Bluetooth
                        is BleConnectionState.Disconnecting -> Icons.Default.Bluetooth
                        is BleConnectionState.Error -> Icons.Default.BluetoothDisabled
                        is BleConnectionState.Disconnected -> Icons.Default.Bluetooth
                    }
                    val iconColor: Color = when (connectionState) {
                        is BleConnectionState.Connected -> SafetyGreen
                        is BleConnectionState.Connecting -> CyanAccent
                        is BleConnectionState.Error -> EmergencyRed
                        else -> TextMuted
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "SAFENEX BAND",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = TextPrimary
                    )
                }

                // Status Indicator Chip
                val statusText: String = when {
                    connectionState is BleConnectionState.Connected -> "CONNECTED"
                    connectionState is BleConnectionState.Connecting -> "CONNECTING"
                    scanState is ScanState.Scanning -> "SCANNING"
                    connectionState is BleConnectionState.Error -> "ERROR"
                    else -> "DISCONNECTED"
                }

                val statusColor: Color = when {
                    connectionState is BleConnectionState.Connected -> SafetyGreen
                    connectionState is BleConnectionState.Connecting || scanState is ScanState.Scanning -> CyanAccent
                    connectionState is BleConnectionState.Error -> EmergencyRed
                    else -> TextMuted
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Body info
            when (connectionState) {
                is BleConnectionState.Connected -> {
                    Column {
                        Text(
                            text = "Status:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Text(
                            text = "CONNECTED",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SafetyGreen
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Device:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Text(
                            text = connectionState.deviceName.ifBlank { "SAFENEX-BAND" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        OutlinedButton(
                            onClick = onDisconnectClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = EmergencyRed
                            )
                        ) {
                            Text(
                                text = "DISCONNECT",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }
                }

                is BleConnectionState.Connecting -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = CyanAccent
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Connecting to SAFENEX Band...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                }

                else -> {
                    // Disconnected or Scanning
                    Column {
                        Text(
                            text = "Status:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Text(
                            text = "DISCONNECTED",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Dynamic Scan Subtext
                        when (scanState) {
                            is ScanState.Scanning -> {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = CyanAccent
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Searching for SAFENEX Band...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CyanAccent
                                    )
                                }
                            }
                            is ScanState.NotFound -> {
                                Text(
                                    text = "SAFENEX Band not found.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = WarningOrange,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            is ScanState.Failed -> {
                                Text(
                                    text = scanState.reason,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = EmergencyRed,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            else -> {}
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val buttonText = when (scanState) {
                            is ScanState.Scanning -> "SEARCHING..."
                            is ScanState.NotFound -> "TRY AGAIN"
                            else -> "SCAN FOR BAND"
                        }

                        Button(
                            onClick = onScanClick,
                            enabled = scanState !is ScanState.Scanning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanAccent,
                                contentColor = SafenexSurface
                            )
                        ) {
                            Text(
                                text = buttonText,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DittuCard(
    statusText: String = "Ready",
    isEmergency: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SafenexSurface),
        border = BorderStroke(1.dp, SafenexBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            (if (isEmergency) EmergencyRed else CyanAccent).copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = if (isEmergency) EmergencyRed else CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "DITTU",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = if (isEmergency) "Tactical Assistant" else "Guardian Assistant",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isEmergency) EmergencyRed else SafetyGreen
                )
            )
        }
    }
}

@Composable
fun StandbyProtectionCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SafenexSurface),
        border = BorderStroke(1.dp, SafenexBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SafetyGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = SafetyGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Emergency Protection",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "Continuous Band Monitoring",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }

            Text(
                text = "STANDBY",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = SafetyGreen,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}
