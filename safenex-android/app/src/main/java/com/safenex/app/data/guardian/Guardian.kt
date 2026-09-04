package com.safenex.app.data.guardian

import java.util.UUID

/**
 * Domain model for a trusted emergency contact.
 */
data class Guardian(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String,
    val relationship: String,
    val isPrimary: Boolean = false
)

enum class AlertStatus {
    IDLE,
    SENDING,
    SENT,
    FAILED
}

data class GuardianAlert(
    val guardian: Guardian,
    val status: AlertStatus = AlertStatus.IDLE,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)
