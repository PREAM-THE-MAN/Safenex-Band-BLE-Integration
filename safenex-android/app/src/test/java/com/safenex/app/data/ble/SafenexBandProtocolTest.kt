package com.safenex.app.data.ble

import com.safenex.app.domain.model.TriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafenexBandProtocolTest {

    private val sosUuid = BleConstants.SOS_CHAR_UUID.toString()
    private val statusUuid = BleConstants.STATUS_CHAR_UUID.toString()

    @Test
    fun testParseSosButtonPacket() {
        val payload = "SOS:BUTTON".toByteArray(Charsets.UTF_8)
        val result = SafenexBandProtocol.parseNotification(sosUuid, payload)

        assertTrue("Expected SosEvent result", result is ProtocolResult.SosEvent)
        val event = (result as ProtocolResult.SosEvent).event
        assertEquals(TriggerSource.BUTTON, event.source)
    }

    @Test
    fun testParseSosShakePacket() {
        val payload = "SOS:SHAKE".toByteArray(Charsets.UTF_8)
        val result = SafenexBandProtocol.parseNotification(sosUuid, payload)

        assertTrue("Expected SosEvent result", result is ProtocolResult.SosEvent)
        val event = (result as ProtocolResult.SosEvent).event
        assertEquals(TriggerSource.SHAKE, event.source)
    }

    @Test
    fun testParseSosPacketWithWhitespaceAndCaseInsensitivity() {
        val payload = "  sos:button\n".toByteArray(Charsets.UTF_8)
        val result = SafenexBandProtocol.parseNotification(sosUuid, payload)

        assertTrue("Expected SosEvent result", result is ProtocolResult.SosEvent)
        val event = (result as ProtocolResult.SosEvent).event
        assertEquals(TriggerSource.BUTTON, event.source)
    }

    @Test
    fun testParseStatusPacket() {
        val payload = "BATTERY:95%".toByteArray(Charsets.UTF_8)
        val result = SafenexBandProtocol.parseNotification(statusUuid, payload)

        assertTrue("Expected StatusUpdate result", result is ProtocolResult.StatusUpdate)
        val status = (result as ProtocolResult.StatusUpdate).status
        assertEquals("BATTERY:95%", status)
    }

    @Test
    fun testParseEmptyPayloadReturnsIgnored() {
        val result = SafenexBandProtocol.parseNotification(sosUuid, ByteArray(0))
        assertTrue("Expected Ignored result for empty payload", result is ProtocolResult.Ignored)
    }
}
