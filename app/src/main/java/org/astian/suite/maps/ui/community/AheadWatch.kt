package org.astian.suite.maps.ui.community

import org.astian.suite.maps.core.community.Incident
import org.astian.suite.maps.core.model.LatLng
import org.astian.suite.maps.core.model.distanceTo

/**
 * Which community report the car is heading for, and when to ask whether it is still there
 * (SPEC 4.12, Ahead alert). Pure and clock-driven, so it is tested without a phone.
 *
 * A report on the car's way between [ASK_M] and [ANNOUNCE_M] is a heads-up. From [ASK_M] the
 * card asks "Still there?" and keeps asking while the car passes it, then [LINGER_MS] more, and
 * never longer than [ASK_MAX_MS]. An answered, passed or timed-out report is never raised again
 * in this process. A new alert needs a trusted course: a car standing still raises nothing.
 */
internal class AheadWatch(private val clock: () -> Long = System::currentTimeMillis) {

    sealed interface Alert {
        val incident: Incident
        val distanceM: Double
    }

    data class Heads(override val incident: Incident, override val distanceM: Double) : Alert

    /** [shareLeft] runs from 1 to 0 over the time left to answer; [passed] once the car is past it. */
    data class Ask(
        override val incident: Incident,
        override val distanceM: Double,
        val shareLeft: Float,
        val passed: Boolean = false,
    ) : Alert

    private val done = LinkedHashSet<String>()
    private val announced = LinkedHashSet<String>()
    private var asking: String? = null
    private var askStartedMs = 0L
    private var closestM = Double.MAX_VALUE
    private var passedAtMs: Long? = null

    fun update(items: List<Incident>, me: LatLng?, course: Double?, speedMps: Float?, mine: Set<String>): Alert? {
        val now = clock()
        asking?.let { id ->
            val incident = items.firstOrNull { it.id == id && !it.expired(now) }
            if (incident == null || me == null) {
                finish(id)
            } else {
                val target = target(incident, me)
                val d = me.distanceTo(target)
                closestM = minOf(closestM, d)
                val behind = course != null && d > PASSED_MIN_M && angleGap(course, bearing(me, target)) > BEHIND_DEG
                if (passedAtMs == null && (behind || d > closestM + DRIFT_M)) passedAtMs = now
                val passed = passedAtMs
                val left = when {
                    passed != null -> LINGER_MS - (now - passed)
                    else -> ASK_MAX_MS - (now - askStartedMs)
                }
                if (left <= 0) finish(id) else {
                    val total = if (passed != null) LINGER_MS else ASK_MAX_MS
                    return Ask(incident, d, (left.toFloat() / total).coerceIn(0f, 1f), passed = passed != null)
                }
            }
        }
        if (me == null || course == null || (speedMps ?: 0f) < MIN_SPEED_MPS) return null
        val next = items.asSequence()
            .filter { it.id !in done && it.id !in mine && !it.expired(now) }
            .filter { it.direction == null || angleGap(it.direction!!, course) <= SAME_WAY_DEG }
            .map { it to target(it, me) }
            .map { (incident, at) -> Triple(incident, at, me.distanceTo(at)) }
            .filter { (_, at, d) -> d <= ANNOUNCE_M && (d < ON_STRETCH_M || angleGap(course, bearing(me, at)) <= coneFor(d)) }
            .minByOrNull { it.third }
            ?: return null
        val (incident, _, d) = next
        if (d <= ASK_M) {
            asking = incident.id
            askStartedMs = now
            closestM = d
            passedAtMs = null
            return Ask(incident, d, 1f)
        }
        return Heads(incident, d)
    }

    /** True the first time a report is to be spoken. */
    fun firstAnnouncement(id: String): Boolean = remember(announced, id)

    fun answered(id: String) = finish(id)

    private fun finish(id: String) {
        remember(done, id)
        if (asking == id) asking = null
        passedAtMs = null
    }

    private fun remember(set: LinkedHashSet<String>, id: String): Boolean {
        val added = set.add(id)
        while (set.size > KEPT) set.remove(set.first())
        return added
    }

    companion object {
        const val ANNOUNCE_M = 700.0
        const val ASK_M = 200.0
        const val ASK_MAX_MS = 40_000L
        const val LINGER_MS = 8_000L
        private const val MIN_SPEED_MPS = 2f
        private const val CONE_DEG = 35.0
        private const val NEAR_CONE_DEG = 60.0
        private const val NEAR_M = 60.0
        private const val SAME_WAY_DEG = 60.0
        private const val BEHIND_DEG = 100.0
        private const val PASSED_MIN_M = 25.0
        private const val DRIFT_M = 60.0
        private const val KEPT = 300

        private fun coneFor(d: Double) = if (d < NEAR_M) NEAR_CONE_DEG else CONE_DEG

        /** Where the car meets the report: the start of a stretch (the tail of a jam) unless the
         *  car is already on it, then the closest of its points; the report's spot otherwise. */
        fun target(incident: Incident, me: LatLng): LatLng {
            val line = incident.line ?: return LatLng(incident.lat, incident.lng)
            val start = LatLng(line.first().first, line.first().second)
            val nearest = line.map { LatLng(it.first, it.second) }.minBy { me.distanceTo(it) }
            return if (me.distanceTo(nearest) < ON_STRETCH_M) nearest else start
        }

        private const val ON_STRETCH_M = 30.0
    }
}

/** The initial compass bearing from [from] to [to], in degrees. */
internal fun bearing(from: LatLng, to: LatLng): Double {
    val p1 = Math.toRadians(from.lat)
    val p2 = Math.toRadians(to.lat)
    val dl = Math.toRadians(to.lng - from.lng)
    val y = kotlin.math.sin(dl) * kotlin.math.cos(p2)
    val x = kotlin.math.cos(p1) * kotlin.math.sin(p2) - kotlin.math.sin(p1) * kotlin.math.cos(p2) * kotlin.math.cos(dl)
    return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
}

internal fun angleGap(a: Double, b: Double): Double {
    val gap = kotlin.math.abs(a - b) % 360.0
    return if (gap > 180.0) 360.0 - gap else gap
}
