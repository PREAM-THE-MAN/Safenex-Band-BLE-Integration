package com.safenex.app.data.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SecurityManagerTest {

    private lateinit var securityManager: SecurityManager

    @Before
    fun setUp() {
        securityManager = SecurityManager(prefs = null)
    }

    @Test
    fun testDefaultPinVerification() {
        assertTrue("Default PIN 1234 should verify successfully", securityManager.verifyPin("1234"))
        assertFalse("Incorrect PIN should fail", securityManager.verifyPin("0000"))
        assertFalse("Incorrect PIN should fail", securityManager.verifyPin("12345"))
        assertFalse("Empty PIN should fail", securityManager.verifyPin(""))
    }

    @Test
    fun testUpdatePinVerification() {
        securityManager.setPin("9876")

        assertTrue("New PIN 9876 should verify successfully", securityManager.verifyPin("9876"))
        assertFalse("Old PIN 1234 should no longer verify", securityManager.verifyPin("1234"))
    }
}
