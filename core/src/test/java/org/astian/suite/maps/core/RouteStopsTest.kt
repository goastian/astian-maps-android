package org.astian.suite.maps.core

import org.astian.suite.maps.core.model.LatLng
import org.astian.suite.maps.core.model.Maneuver
import org.astian.suite.maps.core.model.ManeuverType
import org.astian.suite.maps.core.model.Route
import org.astian.suite.maps.core.model.RouteLeg
import org.astian.suite.maps.core.nav.RouteStops
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteStopsTest {
    // A straight route east along one latitude, 1 km per point, Davis-ish.
    private val poly = (0..10).map { LatLng(38.55, -121.74 + it * 0.0115) }
    private fun m(type: ManeuverType, i: Int) = Maneuver(type, "", poly[i], 1000.0, 60.0)
    private val route = Route(
        polyline = poly, distanceMeters = 10_000.0, durationSeconds = 600.0, durationInTrafficSeconds = null,
        legs = listOf(RouteLeg(10_000.0, 600.0, null, listOf(m(ManeuverType.DEPART, 0), m(ManeuverType.TURN_LEFT, 3), m(ManeuverType.TURN_RIGHT, 6), m(ManeuverType.ARRIVE, 10)))),
    )

    @Test fun stopBetweenTurnsStartsTheNextLegAtTheFollowingTurn() {
        val stops = listOf(poly[4].copy(lat = 38.5501) to "Co-op") // 11 m off the road, between turns at 3 and 6
        assertEquals(listOf(2 to "Co-op"), RouteStops.legStarts(route, stops))
    }

    @Test fun stopOnATurnCountsThatTurnAsTheLegStart() {
        assertEquals(listOf(1 to "A"), RouteStops.legStarts(route, listOf(poly[3] to "A")))
    }

    @Test fun twoStopsInOrder() {
        assertEquals(listOf(1 to "A", 2 to "B"), RouteStops.legStarts(route, listOf(poly[2] to "A", poly[5] to "B")))
    }

    @Test fun farStopsAndStopsPastTheEndAreSkipped() {
        assertEquals(emptyList<Pair<Int, String>>(), RouteStops.legStarts(route, listOf(LatLng(38.60, -121.74) to "far")))
        assertEquals(emptyList<Pair<Int, String>>(), RouteStops.legStarts(route, listOf(poly[10] to "end")))
    }
}
