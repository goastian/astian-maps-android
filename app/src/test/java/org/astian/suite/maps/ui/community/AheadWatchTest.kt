package org.astian.suite.maps.ui.community

import org.astian.suite.maps.core.community.Incident
import org.astian.suite.maps.core.community.IncidentType
import org.astian.suite.maps.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixture: driving east along Covell Boulevard, Davis, California. */
class AheadWatchTest {
    private var now = 1_000_000L
    private val watch = AheadWatch { now }
    private val lat = 38.5449
    private val meterLng = 1.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(lat)))

    private fun east(m: Double) = LatLng(lat, -121.7600 + m * meterLng)

    private fun police(atM: Double, id: String = "inc_p", direction: Double? = null) = Incident(
        id = id, type = IncidentType.POLICE, subtype = null, lat = lat, lng = east(atM).lng, direction = direction,
        createdAtMs = now - 60_000, expiresAtMs = now + 3_600_000, confidence = 0.5, confirmations = 0, dismissals = 0, source = "android",
    )

    private fun at(m: Double, items: List<Incident>, speed: Float = 14f, course: Double? = 90.0) =
        watch.update(items, east(m), course, speed, emptySet())

    @Test fun `a report ahead is a heads-up, then a question from 200 m`() {
        val items = listOf(police(1_000.0))
        assertNull(at(0.0, items))
        val heads = at(450.0, items)
        assertTrue(heads is AheadWatch.Heads)
        assertEquals(550.0, heads!!.distanceM, 2.0)
        assertTrue(at(810.0, items) is AheadWatch.Ask)
    }

    @Test fun `the question stays while passing and goes a few seconds after`() {
        val items = listOf(police(1_000.0))
        assertTrue(at(850.0, items) is AheadWatch.Ask)
        now += 5_000
        assertTrue(at(1_000.0, items) is AheadWatch.Ask)
        now += 3_000
        val past = at(1_080.0, items)
        assertTrue(past is AheadWatch.Ask && past.passed)
        now += AheadWatch.LINGER_MS + 1
        assertNull(at(1_200.0, items))
        assertNull(at(1_200.0, listOf(police(1_300.0))), "a passed report is never raised again")
    }

    @Test fun `an answer ends the question and the report is not raised again`() {
        val items = listOf(police(1_000.0))
        assertTrue(at(900.0, items) is AheadWatch.Ask)
        watch.answered("inc_p")
        assertNull(at(920.0, items))
    }

    @Test fun `a question that nobody answers times out`() {
        val items = listOf(police(1_000.0))
        assertTrue(at(850.0, items) is AheadWatch.Ask)
        now += AheadWatch.ASK_MAX_MS + 1
        assertNull(at(860.0, items))
    }

    @Test fun `a car standing still raises nothing new`() {
        val items = listOf(police(1_000.0))
        assertNull(at(500.0, items, speed = 0.5f))
        assertNull(at(500.0, items, course = null))
    }

    @Test fun `a report behind or for the other direction is left alone`() {
        assertNull(at(500.0, listOf(police(200.0))))
        assertNull(at(500.0, listOf(police(800.0, direction = 270.0))))
        assertTrue(at(500.0, listOf(police(800.0, direction = 95.0))) is AheadWatch.Heads)
    }

    @Test fun `a jam is met at the start of its stretch`() {
        val jam = police(1_000.0).copy(
            id = "inc_j",
            type = IncidentType.CONGESTION,
            line = listOf(lat to east(700.0).lng, lat to east(1_100.0).lng),
        )
        val ask = at(550.0, listOf(jam))
        assertTrue(ask is AheadWatch.Ask)
        assertEquals(150.0, ask!!.distanceM, 2.0)
    }

    private fun assertNull(value: Any?, message: String) = org.junit.Assert.assertNull(message, value)
}
