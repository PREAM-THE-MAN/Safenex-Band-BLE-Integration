package com.safenex.app.data.video

import java.io.File

/**
 * State representing background video evidence recording lifecycle.
 */
sealed class VideoRecordingState {
    object Idle : VideoRecordingState()
    data class Recording(
        val startTime: Long,
        val durationSeconds: Int = 0,
        val filePath: String
    ) : VideoRecordingState()
    data class Saved(
        val filePath: String,
        val durationSeconds: Int,
        val sizeBytes: Long
    ) : VideoRecordingState()
    data class Error(val reason: String) : VideoRecordingState()
}
