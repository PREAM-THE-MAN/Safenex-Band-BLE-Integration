package com.safenex.app.data.security

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Secure local authentication architecture for SAFENEX safety verification.
 * Employs salted cryptographic hashing (SHA-256 + 16-byte random salt + constant-time comparison)
 * ensuring the PIN is NEVER stored in plain text.
 */
class SecurityManager(
    private val prefs: SharedPreferences? = null
) {
    companion object {
        const val PREFS_NAME = "safenex_secure_auth"
        const val KEY_PIN_HASH = "auth_pin_hash"
        const val KEY_PIN_SALT = "auth_pin_salt"
        const val DEFAULT_PIN = "1234"
    }

    // Secondary constructor for Android Context
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    // In-memory fallback for testing environments where SharedPreferences is null
    private var inMemoryHash: String? = null
    private var inMemorySalt: String? = null

    init {
        if (prefs != null) {
            if (!prefs.contains(KEY_PIN_HASH)) {
                setPin(DEFAULT_PIN)
            }
        } else {
            setPin(DEFAULT_PIN)
        }
    }

    /**
     * Verifies the user-entered PIN against the stored cryptographic hash.
     * Uses constant-time comparison (MessageDigest.isEqual) to mitigate timing attacks.
     */
    fun verifyPin(candidatePin: String): Boolean {
        val storedHashBase64 = prefs?.getString(KEY_PIN_HASH, null) ?: inMemoryHash ?: return false
        val storedSaltBase64 = prefs?.getString(KEY_PIN_SALT, null) ?: inMemorySalt ?: return false

        val salt = Base64.getDecoder().decode(storedSaltBase64)
        val expectedHash = Base64.getDecoder().decode(storedHashBase64)
        val candidateHash = hashWithSalt(candidatePin, salt)

        return MessageDigest.isEqual(expectedHash, candidateHash)
    }

    /**
     * Sets or updates the PIN by generating a fresh cryptographic salt and SHA-256 hash.
     */
    fun setPin(pin: String) {
        val salt = ByteArray(16).apply {
            SecureRandom().nextBytes(this)
        }
        val hash = hashWithSalt(pin, salt)

        val saltBase64 = Base64.getEncoder().encodeToString(salt)
        val hashBase64 = Base64.getEncoder().encodeToString(hash)

        if (prefs != null) {
            prefs.edit()
                .putString(KEY_PIN_SALT, saltBase64)
                .putString(KEY_PIN_HASH, hashBase64)
                .apply()
        } else {
            inMemorySalt = saltBase64
            inMemoryHash = hashBase64
        }
    }

    /**
     * Hashes the PIN combined with a cryptographic salt using SHA-256.
     */
    private fun hashWithSalt(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
    }
}
