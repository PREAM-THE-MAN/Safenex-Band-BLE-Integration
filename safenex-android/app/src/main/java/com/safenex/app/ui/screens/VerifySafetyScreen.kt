package com.safenex.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safenex.app.domain.model.EmergencyState
import com.safenex.app.ui.components.PinIndicatorDots
import com.safenex.app.ui.components.PinKeypad
import com.safenex.app.ui.theme.CyanAccent
import com.safenex.app.ui.theme.EmergencyRed
import com.safenex.app.ui.theme.SafenexDarkBg
import com.safenex.app.ui.theme.SafenexSurface
import com.safenex.app.ui.theme.SafetyGreen
import com.safenex.app.ui.theme.TextMuted
import com.safenex.app.ui.theme.TextPrimary
import com.safenex.app.ui.theme.TextSecondary
import com.safenex.app.ui.viewmodel.MainViewModel

@Composable
fun VerifySafetyScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val emergencyState by viewModel.emergencyState.collectAsState()
    val verificationError by viewModel.verificationError.collectAsState()
    var enteredPin by remember { mutableStateOf("") }

    val isSafetyVerified = emergencyState == EmergencyState.SAFETY_VERIFIED

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SafenexDarkBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSafetyVerified) {
                // Safety Verified Success Feedback
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(SafetyGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = SafetyGreen,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "SAFETY VERIFIED",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = SafetyGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Returning to Standby...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            } else {
                // Main PIN Entry Screen
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Lock Shield Icon
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(CyanAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "VERIFY SAFETY",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Enter your SAFENEX PIN",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Masked PIN dots
                    PinIndicatorDots(
                        pinLength = enteredPin.length,
                        maxDigits = 4
                    )

                    // Error Message
                    AnimatedVisibility(
                        visible = verificationError != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = verificationError ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = EmergencyRed,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Numeric Keypad
                    PinKeypad(
                        onDigitClick = { digit ->
                            if (enteredPin.length < 6) {
                                enteredPin += digit
                            }
                        },
                        onBackspaceClick = {
                            if (enteredPin.isNotEmpty()) {
                                enteredPin = enteredPin.dropLast(1)
                            }
                        },
                        onClearClick = {
                            enteredPin = ""
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // VERIFY Button
                    Button(
                        onClick = {
                            if (enteredPin.isNotEmpty()) {
                                val success = viewModel.submitPin(enteredPin)
                                if (!success) {
                                    enteredPin = ""
                                }
                            }
                        },
                        enabled = enteredPin.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SafetyGreen,
                            contentColor = SafenexDarkBg
                        )
                    ) {
                        Text(
                            text = "VERIFY",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Return to Emergency mode (does NOT clear emergency)
                    OutlinedButton(
                        onClick = {
                            viewModel.cancelVerification()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, TextMuted.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        )
                    ) {
                        Text(
                            text = "BACK TO EMERGENCY",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
