package org.astian.suite.maps.ui.community

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.astian.suite.maps.R
import org.astian.suite.maps.core.community.Incident
import org.astian.suite.maps.core.community.IncidentApiException
import org.astian.suite.maps.core.community.IncidentBox
import org.astian.suite.maps.core.community.IncidentType
import org.astian.suite.maps.core.model.LatLng
import org.astian.suite.maps.core.model.distanceTo
import org.astian.suite.maps.ui.map.MapViewModel

/**
 * Community reports (police, crashes, hazards...) shared through Astian's API with every phone
 * and the web map. Process-wide holder, started in `AstianApp`, persisted to `astian_community`.
 *
 * What leaves the phone, and only while [enabled]: the area around the map when reports are
 * fetched, and a report's type, spot and direction when the user sends one. Each request carries
 * a random key that is replaced every day ([reporterKey]); there is no account and no device id.
 */
object CommunityReports {
    /** Settings > Navigation > "Community reports": the layer, the alerts and the report button. */
    val enabled = mutableStateOf(true)

    /** The one-time explanation before the first report has been read. */
    val consented = mutableStateOf(false)

    val items = mutableStateOf<List<Incident>>(emptyList())
    val selected = mutableStateOf<Incident?>(null)
    val pickerOpen = mutableStateOf(false)
    val consentAsk = mutableStateOf(false)

    /** The report just sent, for its Undo; null once the window has passed or it merged. */
    val lastSent = mutableStateOf<Sent?>(null)

    enum class Phase { SENDING, CONFIRMED, ERROR }

    enum class Failure(@StringRes val message: Int, val retryable: Boolean) {
        CONNECTION(R.string.community_connection_failed, true),
        RATE_LIMIT(R.string.community_rate_limited, true),
        EXPIRED(R.string.community_expired, false),
        FORBIDDEN(R.string.community_forbidden, false),
        OTHER(R.string.community_failed, true),
    }

    sealed interface Action {
        val key: String
        data class Report(val type: IncidentType, val subtype: String?, val at: LatLng, val heading: Float?, val reporter: String) : Action {
            override val key = "report"
        }
        data class Vote(val incident: Incident, val present: Boolean, val reporter: String) : Action {
            override val key = "vote:${incident.id}"
        }
        data class Withdraw(val sent: Sent) : Action {
            override val key = "withdraw:${sent.incident.id}"
        }
    }

    data class Operation(
        val action: Action,
        val phase: Phase,
        val failure: Failure? = null,
        val retryAtMs: Long = 0,
        val visible: Boolean = true,
    )

    val operations = mutableStateOf<Map<String, Operation>>(emptyMap())

    fun voting(id: String) = operations.value["vote:$id"]
    fun withdrawing(id: String) = operations.value["withdraw:$id"]

    private fun put(operation: Operation) {
        val kept = operations.value.toMutableMap()
        kept[operation.action.key] = operation
        while (kept.size > MINE_KEPT) {
            val old = kept.entries.firstOrNull { it.value.phase == Phase.CONFIRMED } ?: break
            kept.remove(old.key)
        }
        operations.value = kept
    }

    fun dismissOperation(key: String) {
        val operation = operations.value[key] ?: return
        if (operation.phase != Phase.SENDING) put(operation.copy(visible = false))
    }

    /** The heads-up or the "Still there?" question on screen while driving, from [watch]. */
    internal val alert = mutableStateOf<AheadWatch.Alert?>(null)
    internal val watch = AheadWatch()

    /** The last course the car held while moving, so a report made while stopped in a jam still
     *  says which way the traffic runs. Kept in memory only. */
    private var lastCourse: Pair<Float, Long>? = null
    private const val COURSE_KEPT_MS = 180_000L

    fun noteCourse(bearing: Float?, speedMps: Float?) {
        if (bearing != null && (speedMps ?: 0f) > 2f) lastCourse = bearing to System.currentTimeMillis()
    }

    /** The heading a report is sent with: the live course while moving, else a recent one. */
    fun reportHeading(bearing: Float?, speedMps: Float?): Float? {
        if (bearing != null && (speedMps ?: 0f) > 2f) return bearing
        return lastCourse?.takeIf { System.currentTimeMillis() - it.second < COURSE_KEPT_MS }?.first
    }

    /** Drops reports whose time ran out, so nothing stays on the map or in the alerts when the
     *  network is gone and no update arrives. */
    fun pruneExpired(now: Long = System.currentTimeMillis()) {
        val list = items.value
        if (list.none { it.expired(now) }) return
        items.value = list.filterNot { it.expired(now) }
        if (selected.value?.expired(now) == true) selected.value = null
    }

    /** Reports this phone sent, or joined by reporting the same thing: never asked about ahead.
     *  Kept as report ids only, the last [MINE_KEPT], which expire within a day. */
    val mine = LinkedHashSet<String>()
    private const val MINE_KEPT = 60

    class Sent(val incident: Incident, val withdrawToken: String?, val merged: Boolean, val atMs: Long)

    private const val PREFS = "astian_community"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CONSENT = "consent_v1"
    private const val KEY_REPORTER = "reporter_key"
    private const val KEY_REPORTER_DAY = "reporter_day"
    private const val KEY_MINE = "mine"

    fun init(context: Context) {
        val p = prefs(context)
        enabled.value = p.getBoolean(KEY_ENABLED, true)
        consented.value = p.getBoolean(KEY_CONSENT, false)
        mine.clear()
        p.getString(KEY_MINE, null)?.split(',')?.filter { it.isNotBlank() }?.let { mine.addAll(it) }
    }

    private fun rememberMine(context: Context, id: String) {
        mine.remove(id)
        mine.add(id)
        while (mine.size > MINE_KEPT) mine.remove(mine.first())
        prefs(context).edit().putString(KEY_MINE, mine.joinToString(",")).apply()
    }

    fun setEnabled(context: Context, on: Boolean) {
        enabled.value = on
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        if (!on) {
            items.value = emptyList()
            selected.value = null
            pickerOpen.value = false
            alert.value = null
            lastFetch = null
        }
    }

    fun openPicker() {
        if (operations.value["report"]?.phase == Phase.SENDING) return
        if (consented.value) pickerOpen.value = true else consentAsk.value = true
    }

    fun consent(context: Context) {
        consented.value = true
        consentAsk.value = false
        prefs(context).edit().putBoolean(KEY_CONSENT, true).apply()
        pickerOpen.value = true
    }

    /** A random key, replaced on the first use of each day (UTC), so a day's reports cannot be
     *  linked to the next day's. The server keeps only a salted hash of it. */
    fun reporterKey(context: Context): String {
        val p = prefs(context)
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
        p.getString(KEY_REPORTER, null)?.takeIf { p.getString(KEY_REPORTER_DAY, null) == today }?.let { return it }
        val bytes = ByteArray(18).also { java.security.SecureRandom().nextBytes(it) }
        val key = bytes.joinToString("") { "%02x".format(it) }
        p.edit().putString(KEY_REPORTER, key).putString(KEY_REPORTER_DAY, today).apply()
        return key
    }

    // --- network ----------------------------------------------------------------------------

    private var lastFetch: Pair<LatLng, Long>? = null

    /** A fetched area this far around the center, and refetched once the center moves past half of it. */
    const val FETCH_HALF_DEG = 0.12
    private const val REFETCH_MS = 60_000L

    /** The reports around [center], unless a fetch for nearly the same area is recent. */
    suspend fun refresh(vm: MapViewModel, center: LatLng, force: Boolean = false) {
        if (!enabled.value) return
        val last = lastFetch
        val now = System.currentTimeMillis()
        if (!force && last != null && now - last.second < REFETCH_MS && last.first.distanceTo(center) < 4_000.0) return
        lastFetch = center to now
        runCatching { vm.incidents.list(IncidentBox.around(center.lat, center.lng, FETCH_HALF_DEG)) }
            .onSuccess { list ->
                val kept = list.filterNot { it.expired(now) }
                // A report sent a moment ago and not listed yet stays.
                val mine = lastSent.value?.incident?.takeIf { s -> kept.none { it.id == s.id } && !s.expired(now) }
                items.value = kept + listOfNotNull(mine)
                selected.value = selected.value?.let { s -> items.value.firstOrNull { it.id == s.id } }
            }
            .onFailure { android.util.Log.w("AstianCommunity", "list failed: ${it.javaClass.simpleName} ${(it as? IncidentApiException)?.status ?: ""}") }
    }

    fun upsert(incident: Incident) {
        if (incident.expired()) return remove(incident.id)
        items.value = items.value.filterNot { it.id == incident.id } + incident
        if (selected.value?.id == incident.id) selected.value = incident
    }

    fun remove(id: String) {
        items.value = items.value.filterNot { it.id == id }
        if (selected.value?.id == id) selected.value = null
    }

    fun report(vm: MapViewModel, context: Context, type: IncidentType, subtype: String?, at: LatLng, heading: Float?) {
        if (operations.value["report"]?.phase == Phase.SENDING) return
        pickerOpen.value = false
        submit(vm, context, Action.Report(type, subtype, at, heading, reporterKey(context)))
    }

    fun undo(vm: MapViewModel, context: Context) {
        val sent = lastSent.value ?: return
        if (sent.withdrawToken == null) return
        val previous = withdrawing(sent.incident.id)
        if (previous != null) return retry(vm, context, previous.action.key)
        submit(vm, context, Action.Withdraw(sent))
    }

    fun vote(vm: MapViewModel, context: Context, incident: Incident, present: Boolean) {
        val previous = voting(incident.id)
        if (previous != null) return
        submit(vm, context, Action.Vote(incident, present, reporterKey(context)))
    }

    fun retry(vm: MapViewModel, context: Context, key: String) {
        val previous = operations.value[key] ?: return
        if (previous.phase != Phase.ERROR || previous.failure?.retryable != true || System.currentTimeMillis() < previous.retryAtMs) return
        submit(vm, context, previous.action)
    }

    private fun submit(vm: MapViewModel, context: Context, action: Action) {
        if (operations.value[action.key]?.phase == Phase.SENDING) return
        put(Operation(action, Phase.SENDING))
        vm.viewModelScope.launch {
            try {
                when (action) {
                    is Action.Report -> {
                        val created = vm.incidents.create(action.type, action.subtype, action.at.lat, action.at.lng, action.heading?.toDouble(), action.reporter)
                        rememberMine(context, created.incident.id)
                        upsert(created.incident)
                        lastSent.value = Sent(created.incident, created.withdrawToken, created.merged, System.currentTimeMillis())
                        val label = context.getString(typeLabel(action.type))
                        vm.flashStatus(context.getString(if (created.merged) R.string.community_merged else R.string.community_reported, label))
                    }
                    is Action.Vote -> {
                        val result = vm.incidents.vote(action.incident.id, action.present, action.reporter)
                        if (result.active) upsert(result.incident) else remove(action.incident.id)
                        watch.answered(action.incident.id)
                        if (selected.value?.id == action.incident.id) selected.value = null
                        if (alert.value?.incident?.id == action.incident.id) alert.value = null
                        vm.flashStatus(context.getString(R.string.community_thanks))
                    }
                    is Action.Withdraw -> {
                        vm.incidents.withdraw(action.sent.incident.id, requireNotNull(action.sent.withdrawToken))
                        remove(action.sent.incident.id)
                        if (lastSent.value === action.sent) lastSent.value = null
                        vm.flashStatus(context.getString(R.string.community_withdrawn))
                    }
                }
                put(Operation(action, Phase.CONFIRMED))
            } catch (cancelled: CancellationException) {
                put(Operation(action, Phase.ERROR, Failure.CONNECTION))
                throw cancelled
            } catch (error: Exception) {
                val failure = when ((error as? IncidentApiException)?.status) {
                    429 -> Failure.RATE_LIMIT
                    404 -> Failure.EXPIRED
                    403 -> Failure.FORBIDDEN
                    else -> if (error is java.io.IOException) Failure.CONNECTION else Failure.OTHER
                }
                if (failure == Failure.EXPIRED) {
                    val id = when (action) {
                        is Action.Vote -> action.incident.id
                        is Action.Withdraw -> action.sent.incident.id
                        is Action.Report -> null
                    }
                    id?.let {
                        remove(it)
                        if (alert.value?.incident?.id == it) alert.value = null
                        if (lastSent.value?.incident?.id == it) lastSent.value = null
                    }
                }
                val retryAt = if (failure == Failure.RATE_LIMIT) {
                    System.currentTimeMillis() + ((error as? IncidentApiException)?.retryAfterMs ?: 60_000L)
                } else 0L
                put(Operation(action, Phase.ERROR, failure, retryAt))
            }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- look ---------------------------------------------------------------------------------

    @StringRes
    fun typeLabel(type: IncidentType): Int = when (type) {
        IncidentType.CONGESTION -> R.string.incident_congestion
        IncidentType.POLICE -> R.string.incident_police
        IncidentType.COLLISION -> R.string.incident_collision
        IncidentType.HAZARD -> R.string.incident_hazard
        IncidentType.POTHOLE -> R.string.incident_pothole
        IncidentType.ROADWORKS -> R.string.incident_roadworks
        IncidentType.CLOSURE -> R.string.incident_closure
        IncidentType.WEATHER -> R.string.incident_weather
        IncidentType.CAMERA -> R.string.incident_camera
    }

    @StringRes
    fun subtypeLabel(subtype: String): Int? = when (subtype) {
        "moderate" -> R.string.incident_sub_moderate
        "heavy" -> R.string.incident_sub_heavy
        "standstill" -> R.string.incident_sub_standstill
        "visible" -> R.string.incident_sub_visible
        "hidden" -> R.string.incident_sub_hidden
        "checkpoint" -> R.string.incident_sub_checkpoint
        "minor" -> R.string.incident_sub_minor
        "major" -> R.string.incident_sub_major
        "opposite_side" -> R.string.incident_sub_opposite_side
        "object" -> R.string.incident_sub_object
        "stopped_vehicle" -> R.string.incident_sub_stopped_vehicle
        "animal" -> R.string.incident_sub_animal
        "lane_closed" -> R.string.incident_sub_lane_closed
        "slow" -> R.string.incident_sub_slow
        "rain" -> R.string.incident_sub_rain
        "fog" -> R.string.incident_sub_fog
        "flood" -> R.string.incident_sub_flood
        "speed" -> R.string.incident_sub_speed
        "red_light" -> R.string.incident_sub_red_light
        "mobile" -> R.string.incident_sub_mobile
        else -> null
    }

    @DrawableRes
    fun glyph(type: IncidentType): Int = when (type) {
        IncidentType.CONGESTION -> R.drawable.ic_incident_congestion
        IncidentType.POLICE -> R.drawable.ic_incident_police
        IncidentType.COLLISION -> R.drawable.ic_incident_collision
        IncidentType.HAZARD -> R.drawable.ic_incident_hazard
        IncidentType.POTHOLE -> R.drawable.ic_incident_pothole
        IncidentType.ROADWORKS -> R.drawable.ic_incident_roadworks
        IncidentType.CLOSURE -> R.drawable.ic_incident_closure
        IncidentType.WEATHER -> R.drawable.ic_incident_weather
        IncidentType.CAMERA -> R.drawable.ic_incident_camera
    }

    /** One flat color per type. A jam takes its level's color ([jamColor]), so its pin and its
     *  stretch of road agree. */
    fun color(type: IncidentType, subtype: String? = null): Color = when (type) {
        IncidentType.CONGESTION -> jamColor(subtype)
        IncidentType.POLICE -> Color(0xFF1C64D8)
        IncidentType.COLLISION -> Color(0xFFD6336C)
        IncidentType.HAZARD -> Color(0xFFF2A100)
        IncidentType.POTHOLE -> Color(0xFF8D6E63)
        IncidentType.ROADWORKS -> Color(0xFFF76707)
        IncidentType.CLOSURE -> Color(0xFF343A40)
        IncidentType.WEATHER -> Color(0xFF0C8599)
        IncidentType.CAMERA -> Color(0xFF6741D9)
    }

    /** Traffic as drivers read it: amber, red, then dark red where it does not move; orange when
     *  the reporter gave no level. */
    fun jamColor(subtype: String?): Color = when (subtype) {
        "moderate" -> Color(0xFFF59F00)
        "heavy" -> Color(0xFFE03131)
        "standstill" -> Color(0xFFA51111)
        else -> Color(0xFFF76707)
    }

    /** The hazard badge is light, so its glyph is drawn dark. */
    fun glyphInk(type: IncidentType): Color = if (type == IncidentType.HAZARD) Color(0xD1000000) else Color.White
}
