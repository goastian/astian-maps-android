package org.astian.suite.maps.core.community

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentsTest {
    private fun wire(type: String = "police", subtype: String? = "checkpoint") = JSONObject(
        """
        {"id":"inc_1","type":"$type",${if (subtype != null) "\"subtype\":\"$subtype\"," else ""}
         "coordinates":{"latitude":38.5449,"longitude":-121.7405},"directionDegrees":92.5,
         "createdAt":"2026-10-26T12:00:00.123456Z","expiresAt":"2026-10-26T13:00:00Z",
         "confidence":0.667,"confirmations":1,"dismissals":0,"source":"web"}
        """.trimIndent(),
    )

    @Test fun `an API incident reads into the model`() {
        val incident = IncidentsClient.parseIncident(wire())!!
        assertEquals(IncidentType.POLICE, incident.type)
        assertEquals("checkpoint", incident.subtype)
        assertEquals(38.5449, incident.lat, 1e-9)
        assertEquals(92.5, incident.direction!!, 1e-9)
        assertEquals(3_600_000L - 123, incident.expiresAtMs - incident.createdAtMs)
        assertEquals(1, incident.confirmations)
        assertEquals("web", incident.source)
        assertFalse(incident.expired(incident.createdAtMs))
        assertTrue(incident.expired(incident.expiresAtMs))
    }

    @Test fun `a report on its road carries the road and the stretch it covers`() {
        val o = wire(type = "congestion", subtype = "heavy")
            .put("lastConfirmedAt", "2026-10-26T12:10:00Z")
            .put(
                "road",
                JSONObject("""{"name":"Covell Boulevard","line":[[-121.7450,38.5449],[-121.7420,38.5450],[-121.7400,38.5451]]}"""),
            )
        val incident = IncidentsClient.parseIncident(o)!!
        assertEquals("Covell Boulevard", incident.roadName)
        assertEquals(3, incident.line!!.size)
        assertEquals(38.5449, incident.line.first().first, 1e-9)
        assertEquals(-121.7450, incident.line.first().second, 1e-9)
        assertEquals(600_000L - 123, incident.lastConfirmedAtMs!! - incident.createdAtMs)
        val bare = IncidentsClient.parseIncident(wire())!!
        assertNull(bare.line)
        assertNull(bare.roadName)
        assertNull(bare.lastConfirmedAtMs)
    }

    @Test fun `an unknown type is skipped and a foreign subtype dropped`() {
        assertNull(IncidentsClient.parseIncident(wire(type = "meteor")))
        assertNull(IncidentsClient.parseIncident(wire(subtype = "fog"))!!.subtype)
        assertNull(IncidentsClient.parseIncident(wire(type = "pothole", subtype = null))!!.subtype)
    }

    @Test fun `every type speaks the API's names`() {
        val wireNames = IncidentType.entries.map { it.wire }
        assertEquals(
            listOf("congestion", "police", "collision", "hazard", "pothole", "roadworks", "closure", "weather", "camera"),
            wireNames,
        )
        IncidentType.entries.forEach { assertEquals(it, IncidentType.of(it.wire)) }
    }

    @Test fun `a box around a point stays square on the ground and inside the server's limit`() {
        val box = IncidentBox.around(38.5449, -121.7405, 0.12)
        assertEquals(0.24, box.north - box.south, 1e-9)
        assertTrue(box.east - box.west > 0.24 && box.east - box.west < 3.0)
        assertTrue(box.contains(38.5449, -121.7405))
        assertTrue(IncidentBox.around(38.5449, -121.7405, 0.3).contains(box))
        assertEquals("-121.89393,38.42490,-121.58707,38.66490", box.query())
    }
}
