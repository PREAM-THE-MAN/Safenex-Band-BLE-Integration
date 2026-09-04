package com.safenex.app.domain.model

/**
 * Represents the high-level emergency state machine of the SAFENEX system.
 * Flow: SAFE -> EMERGENCY_ACTIVE -> VERIFYING -> SAFETY_VERIFIED -> SAFE
 */
enum class EmergencyState {
    SAFE,
    EMERGENCY_ACTIVE,
    VERIFYING,
    SAFETY_VERIFIED
}
