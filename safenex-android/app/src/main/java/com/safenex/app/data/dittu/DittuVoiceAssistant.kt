package com.safenex.app.data.dittu

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * DITTU AI Voice Assistant:
 * - Listens for emergency commands like "HELP!", "Call Guardian", "Where am I?".
 * - Speaks vocal confirmations and queries via Android Text-To-Speech (TTS).
 * - Automatic background microphone loop with timeout recovery.
 */
class DittuVoiceAssistant(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    companion object {
        private const val TAG = "SAFENEX_DITTU"
        private const val PREFS_NAME = "safenex_dittu_prefs"
        private const val KEY_VOICE_ENABLED = "key_voice_enabled"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isVoiceWakeEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_VOICE_ENABLED, true)
    )
    val isVoiceWakeEnabled: StateFlow<Boolean> = _isVoiceWakeEnabled.asStateFlow()

    private val _lastHeardPhrase = MutableStateFlow("")
    val lastHeardPhrase: StateFlow<String> = _lastHeardPhrase.asStateFlow()

    private val _assistantResponse = MutableStateFlow("Ready. Say \"HELP!\" or \"Call Guardian\"")
    val assistantResponse: StateFlow<String> = _assistantResponse.asStateFlow()

    var onIntentRecognized: ((DittuVoiceIntent) -> Unit)? = null

    init {
        initTts()
        if (_isVoiceWakeEnabled.value) {
            startListening()
        }
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                isTtsReady = true
                Log.d(TAG, "DITTU TTS initialized successfully.")
            } else {
                Log.w(TAG, "DITTU TTS initialization failed.")
            }
        }
    }

    fun setVoiceWakeEnabled(enabled: Boolean) {
        _isVoiceWakeEnabled.value = enabled
        prefs.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
        if (enabled) {
            startListening()
            speak("DITTU voice assistant is active and listening.")
        } else {
            stopListening()
            speak("DITTU voice assistant paused.")
        }
    }

    fun toggleVoiceWake(): Boolean {
        val newState = !_isVoiceWakeEnabled.value
        setVoiceWakeEnabled(newState)
        return newState
    }

    /**
     * Speaks text aloud using Text-To-Speech.
     */
    fun speak(text: String) {
        _assistantResponse.value = text
        if (isTtsReady) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "DITTU_UTTERANCE")
        }
        Log.i(TAG, "DITTU Voice Response: $text")
    }

    /**
     * Explicitly activates DITTU manual listening.
     */
    fun startManualListening() {
        stopListening()
        _assistantResponse.value = "Listening... Speak your command now!"
        handler.postDelayed({
            startListening()
        }, 200L)
    }

    /**
     * Starts continuous speech recognition loop on Main thread.
     */
    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer is not available on this device.")
            _assistantResponse.value = "Speech recognition unavailable on this device."
            return
        }

        handler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(dittuRecognitionListener)
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                speechRecognizer?.startListening(intent)
                _isListening.value = true
                Log.d(TAG, "DITTU listening loop started.")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting SpeechRecognizer: ${e.message}", e)
                _isListening.value = false
            }
        }
    }

    /**
     * Pauses listening loop.
     */
    fun stopListening() {
        handler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
                _isListening.value = false
                Log.d(TAG, "DITTU listening loop stopped.")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping SpeechRecognizer: ${e.message}")
            }
        }
    }

    private fun restartListeningDelayed(delayMs: Long = 600L) {
        if (!_isVoiceWakeEnabled.value) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (_isVoiceWakeEnabled.value) {
                startListening()
            }
        }, delayMs)
    }

    private val dittuRecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            _isListening.value = false
        }

        override fun onError(error: Int) {
            _isListening.value = false
            Log.d(TAG, "SpeechRecognizer status code: $error")
            // Automatically re-arm for continuous background command detection
            restartListeningDelayed(800L)
        }

        override fun onResults(results: Bundle?) {
            _isListening.value = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val phrase = matches[0]
                Log.i(TAG, "DITTU Recognized: \"$phrase\"")
                _lastHeardPhrase.value = phrase
                processSpokenText(phrase)
            }
            restartListeningDelayed(500L)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val phrase = matches[0]
                _lastHeardPhrase.value = phrase
                // Quick-trigger for urgent SOS keyword in partial stream
                val lower = phrase.lowercase(Locale.US)
                if (lower == "help" || lower == "help me" || lower == "emergency" || lower == "save me" || lower.startsWith("help")) {
                    processSpokenText(phrase)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Natural language intent parsing.
     */
    private fun processSpokenText(text: String) {
        val lower = text.lowercase(Locale.US).trim()

        when {
            // 1. Emergency Trigger Intent: "HELP!", "Emergency", "Save me"
            lower.contains("help") || lower.contains("emergency") || lower.contains("save me") ||
                    lower.contains("danger") || lower.contains("sos") -> {
                Log.w(TAG, "DITTU INTENT DETECTED: TriggerEmergency! Phrase: \"$text\"")
                speak("Emergency mode activated! Alerting your guardians and starting recording.")
                onIntentRecognized?.invoke(DittuVoiceIntent.TriggerEmergency)
            }

            // 2. Call Guardian Intent: "Call guardian", "Call mom", "Call for help"
            lower.contains("call guardian") || lower.contains("call my guardian") ||
                    lower.contains("call primary") || lower.contains("call for help") ||
                    lower.contains("call mom") || lower.contains("call dad") ||
                    lower.contains("dial guardian") || lower.contains("call contact") -> {
                Log.i(TAG, "DITTU INTENT DETECTED: CallGuardian! Phrase: \"$text\"")
                speak("Calling your primary guardian now.")
                onIntentRecognized?.invoke(DittuVoiceIntent.CallGuardian)
            }

            // 3. Share Location to Guardian Intent: "Share location", "Send location to guardian", "WhatsApp location"
            lower.contains("share location") || lower.contains("send location") ||
                    lower.contains("whatsapp location") || lower.contains("share my location") ||
                    lower.contains("send my location") || lower.contains("share live location") -> {
                Log.i(TAG, "DITTU INTENT DETECTED: ShareLocationToGuardian! Phrase: \"$text\"")
                speak("Sharing your live GPS location to your primary guardian on WhatsApp.")
                onIntentRecognized?.invoke(DittuVoiceIntent.ShareLocationToGuardian)
            }

            // 4. Location Query Intent: "Where am I?", "What is my location?"
            lower.contains("where am i") || lower.contains("my location") ||
                    lower.contains("what is my location") || lower.contains("current location") -> {
                Log.i(TAG, "DITTU INTENT DETECTED: QueryLocation! Phrase: \"$text\"")
                onIntentRecognized?.invoke(DittuVoiceIntent.QueryLocation)
            }

            // 5. Band Status Query Intent: "Band status", "Is band connected?"
            lower.contains("band status") || lower.contains("is band connected") ||
                    lower.contains("device status") || lower.contains("watch status") -> {
                Log.i(TAG, "DITTU INTENT DETECTED: QueryBandStatus! Phrase: \"$text\"")
                onIntentRecognized?.invoke(DittuVoiceIntent.QueryBandStatus)
            }

            // 6. Mute Alarm Attempt: Locked until PIN is verified
            lower.contains("stop alarm") || lower.contains("mute") || lower.contains("silence") -> {
                Log.i(TAG, "DITTU: Alarm mute attempt rejected (Security Lock Active)")
                speak("The emergency alarm is locked active for safety. Please verify your PIN on screen to disarm.")
            }

            else -> {
                Log.d(TAG, "DITTU: Non-actionable speech recognized: \"$text\"")
                onIntentRecognized?.invoke(DittuVoiceIntent.Unknown(text))
            }
        }
    }

    fun release() {
        stopListening()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
