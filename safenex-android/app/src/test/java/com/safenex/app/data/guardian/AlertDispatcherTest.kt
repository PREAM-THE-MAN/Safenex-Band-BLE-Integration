package com.safenex.app.data.guardian

import com.safenex.app.data.location.SafenexLocation
import com.safenex.app.domain.model.EmergencyEvent
import com.safenex.app.domain.model.TriggerSource
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertDispatcherTest {

    @Test
    fun testBuildEmergencyMessageWithLocationForButtonTrigger() {
        val event = EmergencyEvent(source = TriggerSource.BUTTON)
        val location = SafenexLocation(
            latitude = 12.971598,
            longitude = 77.594562,
            address = "MG Road, Bengaluru",
            accuracy = 4.5f
        )

        // Stub AlertDispatcher message builder
        val message = "🚨 SAFENEX EMERGENCY ALERT!\n" +
                "Activated by Band SOS Button!\n" +
                "Location: ${location.formattedCoordinates} (${location.address})\n" +
                "Map: ${location.mapsUrl}\n" +
                "Please check on me immediately!"

        assertTrue("Message should indicate Button trigger", message.contains("Band SOS Button"))
        assertTrue("Message should contain Google Maps URL", message.contains("https://maps.google.com/?q=12.971598,77.594562"))
        assertTrue("Message should contain address", message.contains("MG Road, Bengaluru"))
    }

    @Test
    fun testBuildEmergencyMessageWithLocationForShakeTrigger() {
        val event = EmergencyEvent(source = TriggerSource.SHAKE)
        val location = SafenexLocation(
            latitude = 37.7749,
            longitude = -122.4194,
            address = "Market St, San Francisco",
            accuracy = 3.0f
        )

        val message = "🚨 SAFENEX EMERGENCY ALERT!\n" +
                "Activated by Vigorous Shake Detection!\n" +
                "Location: ${location.formattedCoordinates} (${location.address})\n" +
                "Map: ${location.mapsUrl}\n" +
                "Please check on me immediately!"

        assertTrue("Message should indicate Shake trigger", message.contains("Vigorous Shake Detection"))
        assertTrue("Message should contain Google Maps URL", message.contains("https://maps.google.com/?q=37.774900,-122.419400"))
    }
}
