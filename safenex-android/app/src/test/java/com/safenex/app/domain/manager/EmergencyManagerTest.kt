package com.safenex.app.domain.manager

import com.safenex.app.data.security.SecurityManager
import com.safenex.app.domain.model.EmergencyEvent
import com.safenex.app.domain.model.EmergencyState
import com.safenex.app.domain.model.TriggerSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var securityManager: SecurityManager
    private lateinit var emergencyManager: EmergencyManager

    @Before
    fun setUp() {
        securityManager = SecurityManager(prefs = null)
        emergencyManager = EmergencyManager(securityManager, testScope)
    }

    @Test
    fun testInitialStateIsSafe() {
        assertEquals(EmergencyState.SAFE, emergencyManager.emergencyState.value)
        assertNull(emergencyManager.activeEmergencyEvent.value)
    }

    @Test
    fun testTriggerEmergencyTransitionsToActive() {
        val event = EmergencyEvent(source = TriggerSource.BUTTON)
        emergencyManager.triggerEmergency(event)

        assertEquals(EmergencyState.EMERGENCY_ACTIVE, emergencyManager.emergencyState.value)
        assertNotNull(emergencyManager.activeEmergencyEvent.value)
        assertEquals(TriggerSource.BUTTON, emergencyManager.activeEmergencyEvent.value?.source)
    }

    @Test
    fun testVerificationLifecycle() = testScope.runTest {
        // Trigger SOS
        emergencyManager.triggerEmergency(EmergencyEvent(source = TriggerSource.SHAKE))
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, emergencyManager.emergencyState.value)

        // Start Verification
        emergencyManager.startVerification()
        assertEquals(EmergencyState.VERIFYING, emergencyManager.emergencyState.value)

        // Invalid PIN attempt
        val invalidResult = emergencyManager.submitPinVerification("0000")
        assertFalse(invalidResult)
        assertEquals(EmergencyState.VERIFYING, emergencyManager.emergencyState.value)
        assertNotNull(emergencyManager.verificationError.value)

        // Valid PIN attempt (default 1234)
        val validResult = emergencyManager.submitPinVerification("1234")
        assertTrue(validResult)
        assertEquals(EmergencyState.SAFETY_VERIFIED, emergencyManager.emergencyState.value)

        // Advance delay to complete transition to SAFE
        advanceTimeBy(1500)
        advanceUntilIdle()

        assertEquals(EmergencyState.SAFE, emergencyManager.emergencyState.value)
        assertNull(emergencyManager.activeEmergencyEvent.value)
    }

    @Test
    fun testCancelVerificationReturnsToActive() {
        emergencyManager.triggerEmergency(EmergencyEvent(source = TriggerSource.BUTTON))
        emergencyManager.startVerification()
        assertEquals(EmergencyState.VERIFYING, emergencyManager.emergencyState.value)

        emergencyManager.cancelVerification()
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, emergencyManager.emergencyState.value)
    }
}
