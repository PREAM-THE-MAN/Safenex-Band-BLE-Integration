package com.safenex.app.data.dittu

/**
 * Parsed natural language user intents for DITTU Voice Assistant.
 */
sealed class DittuVoiceIntent {
    object TriggerEmergency : DittuVoiceIntent()
    object CallGuardian : DittuVoiceIntent()
    object ShareLocationToGuardian : DittuVoiceIntent()
    object QueryLocation : DittuVoiceIntent()
    object QueryBandStatus : DittuVoiceIntent()
    object MuteDeterrent : DittuVoiceIntent()
    data class Unknown(val rawText: String) : DittuVoiceIntent()
}
