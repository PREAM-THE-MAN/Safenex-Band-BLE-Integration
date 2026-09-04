package com.safenex.app.data.cloud

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Object matching the Supabase PostgreSQL 'emergency_events' table schema.
 */
data class SupabaseEmergencyEvent(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("trigger_source")
    val triggerSource: String,

    @SerializedName("device_name")
    val deviceName: String = "SAFENEX-BAND",

    @SerializedName("latitude")
    val latitude: Double? = null,

    @SerializedName("longitude")
    val longitude: Double? = null,

    @SerializedName("accuracy")
    val accuracy: Float? = null,

    @SerializedName("address")
    val address: String? = null,

    @SerializedName("maps_url")
    val mapsUrl: String? = null,

    @SerializedName("audio_url")
    val audioUrl: String? = null,

    @SerializedName("photo_front_url")
    val photoFrontUrl: String? = null,

    @SerializedName("photo_back_url")
    val photoBackUrl: String? = null,

    @SerializedName("video_url")
    val videoUrl: String? = null,

    @SerializedName("status")
    val status: String = "ACTIVE",

    @SerializedName("guardians_notified")
    val guardiansNotified: Int = 0,

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("resolved_at")
    val resolvedAt: String? = null
)

/**
 * Payload for updating resolution status and attaching audio & video evidence links.
 */
data class SupabaseResolveUpdate(
    @SerializedName("status")
    val status: String = "RESOLVED",

    @SerializedName("resolved_at")
    val resolvedAt: String,

    @SerializedName("audio_url")
    val audioUrl: String? = null,

    @SerializedName("video_url")
    val videoUrl: String? = null
)

/**
 * Payload for updating photo evidence links.
 */
data class SupabasePhotosUpdate(
    @SerializedName("photo_front_url")
    val photoFrontUrl: String? = null,

    @SerializedName("photo_back_url")
    val photoBackUrl: String? = null
)
