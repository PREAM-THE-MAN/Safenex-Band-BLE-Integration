package com.safenex.app.data.guardian

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Manages trusted emergency contacts with persistent local JSON storage.
 */
class GuardianRepository(
    private val context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
) {
    companion object {
        private const val PREFS_NAME = "safenex_guardians_pref"
        private const val KEY_GUARDIANS_JSON = "guardians_list_json"
    }

    private val gson = Gson()
    private val _guardians = MutableStateFlow<List<Guardian>>(emptyList())
    val guardians: StateFlow<List<Guardian>> = _guardians.asStateFlow()

    init {
        loadGuardians()
    }

    private fun loadGuardians() {
        val json = prefs.getString(KEY_GUARDIANS_JSON, null)
        if (json.isNullOrBlank()) {
            // Seed with sample initial guardian for testing
            val initial = listOf(
                Guardian(
                    id = UUID.randomUUID().toString(),
                    name = "Primary Guardian",
                    phoneNumber = "+1234567890",
                    relationship = "Family",
                    isPrimary = true
                )
            )
            saveGuardians(initial)
        } else {
            try {
                val type = object : TypeToken<List<Guardian>>() {}.type
                val list: List<Guardian> = gson.fromJson(json, type) ?: emptyList()
                _guardians.value = list
            } catch (e: Exception) {
                _guardians.value = emptyList()
            }
        }
    }

    private fun saveGuardians(list: List<Guardian>) {
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_GUARDIANS_JSON, json).apply()
        _guardians.value = list
    }

    fun addGuardian(name: String, phoneNumber: String, relationship: String, isPrimary: Boolean = false) {
        val current = _guardians.value.toMutableList()
        val makePrimary = isPrimary || current.isEmpty()

        // If newly added is primary, demote existing primary
        val updated = current.map {
            if (makePrimary) it.copy(isPrimary = false) else it
        }.toMutableList()

        val newGuardian = Guardian(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            phoneNumber = phoneNumber.trim(),
            relationship = relationship.trim(),
            isPrimary = makePrimary
        )
        updated.add(newGuardian)
        saveGuardians(updated)
    }

    fun updateGuardian(guardian: Guardian) {
        val current = _guardians.value
        val updated = current.map {
            if (it.id == guardian.id) guardian else it
        }
        saveGuardians(updated)
    }

    fun deleteGuardian(guardianId: String) {
        val current = _guardians.value.filter { it.id != guardianId }
        // Ensure at least one primary remains if list not empty
        val updated = if (current.isNotEmpty() && current.none { it.isPrimary }) {
            current.mapIndexed { idx, g -> if (idx == 0) g.copy(isPrimary = true) else g }
        } else {
            current
        }
        saveGuardians(updated)
    }

    fun setPrimaryGuardian(guardianId: String) {
        val updated = _guardians.value.map {
            it.copy(isPrimary = it.id == guardianId)
        }
        saveGuardians(updated)
    }

    fun getPrimaryGuardian(): Guardian? = _guardians.value.firstOrNull { it.isPrimary } ?: _guardians.value.firstOrNull()
}
