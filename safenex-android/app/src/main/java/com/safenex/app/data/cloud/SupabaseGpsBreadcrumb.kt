package com.safenex.app.data.cloud

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Object for continuous live GPS coordinate streaming to Supabase.
 */
data class SupabaseGpsBreadcrumb(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("event_id")
    val eventId: String,

    @SerializedName("latitude")
    val latitude: Double,

    @SerializedName("longitude")
    val longitude: Double,

    @SerializedName("accuracy")
    val accuracy: Float? = null,

    @SerializedName("timestamp")
    val timestamp: String? = null
)

/**
 * Update payload for latest location on the main emergency_events row.
 */
data class SupabaseLocationUpdate(
    @SerializedName("latitude")
    val latitude: Double,

    @SerializedName("longitude")
    val longitude: Double,

    @SerializedName("accuracy")
    val accuracy: Float? = null,

    @SerializedName("address")
    val address: String? = null,

    @SerializedName("maps_url")
    val mapsUrl: String? = null
)
