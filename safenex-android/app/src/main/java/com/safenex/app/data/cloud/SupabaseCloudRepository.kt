package com.safenex.app.data.cloud

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.safenex.app.data.location.SafenexLocation
import com.safenex.app.domain.model.EmergencyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class CloudSyncStatus {
    IDLE,
    SYNCING,
    SYNCED,
    OFFLINE,
    ERROR
}

/**
 * Handles asynchronous PostgREST & Storage communication with Supabase,
 * including real-time GPS breadcrumb streaming.
 */
class SupabaseCloudRepository(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "SAFENEX_Supabase"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val AUDIO_MEDIA_TYPE = "audio/mp4".toMediaType()
        private val VIDEO_MEDIA_TYPE = "video/mp4".toMediaType()
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
        private const val TABLE_EVENTS = "emergency_events"
        private const val TABLE_BREADCRUMBS = "emergency_gps_breadcrumbs"
        private const val AUDIO_BUCKET = "emergency-audio"
        private const val PHOTOS_BUCKET = "emergency-photos"
        private const val VIDEOS_BUCKET = "emergency-videos"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val _syncStatus = MutableStateFlow(CloudSyncStatus.IDLE)
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    private val _streamedFixesCount = MutableStateFlow(0)
    val streamedFixesCount: StateFlow<Int> = _streamedFixesCount.asStateFlow()

    private var activeEventId: String? = null

    fun getActiveEventId(): String? = activeEventId

    private fun getCurrentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    /**
     * Posts a new emergency incident to the Supabase database.
     */
    suspend fun insertEmergencyEvent(
        event: EmergencyEvent,
        location: SafenexLocation?,
        guardiansCount: Int
    ): String? = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            Log.w(TAG, "Supabase credentials not configured yet. Skipping cloud upload.")
            _syncStatus.value = CloudSyncStatus.OFFLINE
            return@withContext null
        }

        _syncStatus.value = CloudSyncStatus.SYNCING

        val payload = SupabaseEmergencyEvent(
            triggerSource = event.source.name,
            deviceName = "SAFENEX-BAND",
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracy = location?.accuracy,
            address = location?.address,
            mapsUrl = location?.mapsUrl,
            status = "ACTIVE",
            guardiansNotified = guardiansCount,
            createdAt = getCurrentIsoTimestamp()
        )

        val jsonBody = gson.toJson(payload)
        val url = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/$TABLE_EVENTS"

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.i(TAG, "Successfully inserted emergency event to Supabase: $responseBody")

                    val type = object : TypeToken<List<SupabaseEmergencyEvent>>() {}.type
                    val createdEvents: List<SupabaseEmergencyEvent>? = gson.fromJson(responseBody, type)
                    val insertedId = createdEvents?.firstOrNull()?.id
                    activeEventId = insertedId
                    _syncStatus.value = CloudSyncStatus.SYNCED
                    return@withContext insertedId
                } else {
                    val err = response.body?.string() ?: response.message
                    Log.e(TAG, "Supabase insert error (code ${response.code}): $err")
                    _syncStatus.value = CloudSyncStatus.ERROR
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception connecting to Supabase", e)
            _syncStatus.value = CloudSyncStatus.ERROR
            return@withContext null
        }
    }

    /**
     * Streams real-time GPS coordinate fixes to Supabase:
     * 1. Inserts a row into 'emergency_gps_breadcrumbs'
     * 2. Updates current latitude, longitude, and maps_url on 'emergency_events'
     */
    suspend fun streamGpsBreadcrumb(
        eventId: String? = activeEventId,
        location: SafenexLocation
    ) = withContext(Dispatchers.IO) {
        val targetId = eventId ?: activeEventId ?: return@withContext
        if (!SupabaseConfig.isConfigured()) return@withContext

        // 1. Insert Breadcrumb
        val breadcrumb = SupabaseGpsBreadcrumb(
            eventId = targetId,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            timestamp = getCurrentIsoTimestamp()
        )

        val breadcrumbJson = gson.toJson(breadcrumb)
        val breadcrumbUrl = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/$TABLE_BREADCRUMBS"

        val breadcrumbRequest = Request.Builder()
            .url(breadcrumbUrl)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(breadcrumbJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(breadcrumbRequest).execute().use { response ->
                if (response.isSuccessful) {
                    _streamedFixesCount.value += 1
                    Log.d(TAG, "GPS breadcrumb #${_streamedFixesCount.value} streamed: (${location.latitude}, ${location.longitude})")
                } else {
                    Log.w(TAG, "Breadcrumb insert error: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error streaming GPS breadcrumb: ${e.message}")
        }

        // 2. Update main emergency_events row with latest position
        val locUpdate = SupabaseLocationUpdate(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            address = location.address,
            mapsUrl = location.mapsUrl
        )

        val locUpdateJson = gson.toJson(locUpdate)
        val eventUpdateUrl = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/$TABLE_EVENTS?id=eq.$targetId"

        val eventUpdateRequest = Request.Builder()
            .url(eventUpdateUrl)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("Content-Type", "application/json")
            .patch(locUpdateJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(eventUpdateRequest).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "Error updating event latest location: ${e.message}")
        }
    }

    /**
     * Uploads a photo evidence file (.jpg) to the Supabase Storage Bucket ('emergency-photos').
     */
    suspend fun uploadPhotoEvidence(file: File): String? = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured() || !file.exists() || file.length() == 0L) return@withContext null

        val filename = file.name
        val url = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/$PHOTOS_BUCKET/$filename"

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("Content-Type", "image/jpeg")
            .addHeader("x-upsert", "true")
            .post(file.asRequestBody(JPEG_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val publicUrl = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/$PHOTOS_BUCKET/$filename"
                    Log.i(TAG, "Photo evidence uploaded: $publicUrl")
                    return@withContext publicUrl
                }
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading photo", e)
            return@withContext null
        }
    }

    /**
     * Updates an existing incident row with the uploaded front and rear photo URLs.
     */
    suspend fun updateIncidentPhotos(
        eventId: String? = activeEventId,
        frontUrl: String?,
        backUrl: String?
    ) = withContext(Dispatchers.IO) {
        val targetId = eventId ?: activeEventId ?: return@withContext
        if (!SupabaseConfig.isConfigured() || (frontUrl == null && backUrl == null)) return@withContext

        val payload = SupabasePhotosUpdate(
            photoFrontUrl = frontUrl,
            photoBackUrl = backUrl
        )

        val jsonBody = gson.toJson(payload)
        val url = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/$TABLE_EVENTS?id=eq.$targetId"

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("Content-Type", "application/json")
            .patch(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Exception updating incident photos", e)
        }
    }

    /**
     * Uploads the recorded audio evidence file (.m4a) to the Supabase Storage Bucket ('emergency-audio').
     */
    suspend fun uploadAudioEvidence(file: File): String? = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured() || !file.exists() || file.length() == 0L) return@withContext null

        val filename = file.name
        val url = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/$AUDIO_BUCKET/$filename"

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("Content-Type", "audio/mp4")
            .addHeader("x-upsert", "true")
            .post(file.asRequestBody(AUDIO_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val publicUrl = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/$AUDIO_BUCKET/$filename"
                    Log.i(TAG, "Audio evidence uploaded: $publicUrl")
                    return@withContext publicUrl
                }
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading audio", e)
            return@withContext null
        }
    }

    /**
     * Uploads a video evidence file (.mp4) to the Supabase Storage Bucket ('emergency-videos').
     */
    suspend fun uploadVideoEvidence(file: File): String? = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured() || !file.exists() || file.length() == 0L) return@withContext null

        val filename = file.name
        val url = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/$VIDEOS_BUCKET/$filename"

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("Content-Type", "video/mp4")
            .addHeader("x-upsert", "true")
            .post(file.asRequestBody(VIDEO_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val publicUrl = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/$VIDEOS_BUCKET/$filename"
                    Log.i(TAG, "Video evidence uploaded: $publicUrl")
                    return@withContext publicUrl
                }
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading video", e)
            return@withContext null
        }
    }

    /**
     * Updates an existing incident record marking it as RESOLVED.
     */
    suspend fun markEmergencyResolved(
        eventId: String? = activeEventId,
        audioUrl: String? = null,
        videoUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        val targetId = eventId ?: activeEventId ?: return@withContext
        if (!SupabaseConfig.isConfigured()) return@withContext

        val updatePayload = SupabaseResolveUpdate(
            status = "RESOLVED",
            resolvedAt = getCurrentIsoTimestamp(),
            audioUrl = audioUrl,
            videoUrl = videoUrl
        )

        val jsonBody = gson.toJson(updatePayload)
        val url = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/$TABLE_EVENTS?id=eq.$targetId"

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("Content-Type", "application/json")
            .patch(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().close()
            Log.i(TAG, "Successfully marked emergency $targetId as RESOLVED")
        } catch (e: Exception) {
            Log.e(TAG, "Exception updating Supabase event $targetId", e)
        } finally {
            activeEventId = null
        }
    }

    fun reset() {
        _syncStatus.value = CloudSyncStatus.IDLE
        _streamedFixesCount.value = 0
        activeEventId = null
    }
}
