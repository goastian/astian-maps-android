package org.astian.suite.maps.core.community

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.astian.suite.maps.core.AstianConfig
import org.astian.suite.maps.core.config.AstianLinks
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What a community report is about; [wire] is the API's name and [subtypes] the detail it may carry. */
enum class IncidentType(val wire: String, val subtypes: List<String>) {
    CONGESTION("congestion", listOf("moderate", "heavy", "standstill")),
    POLICE("police", listOf("visible", "hidden", "checkpoint")),
    COLLISION("collision", listOf("minor", "major", "opposite_side")),
    HAZARD("hazard", listOf("object", "stopped_vehicle", "animal")),
    POTHOLE("pothole", emptyList()),
    ROADWORKS("roadworks", listOf("lane_closed", "slow")),
    CLOSURE("closure", emptyList()),
    WEATHER("weather", listOf("rain", "fog", "flood")),
    CAMERA("camera", listOf("speed", "red_light", "mobile")),
    ;

    companion object {
        fun of(wire: String): IncidentType? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * An active report as the API returns it. Times are epoch milliseconds. The server moves a new
 * report onto its road shortly after it is made; [roadName] is that road's name and [line] the
 * stretch a jam, a closure or roadworks covers, as (lat, lng) pairs in the direction of travel.
 */
data class Incident(
    val id: String,
    val type: IncidentType,
    val subtype: String?,
    val lat: Double,
    val lng: Double,
    val direction: Double?,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val confidence: Double,
    val confirmations: Int,
    val dismissals: Int,
    val source: String?,
    val lastConfirmedAtMs: Long? = null,
    val roadName: String? = null,
    val line: List<Pair<Double, Double>>? = null,
) {
    fun expired(nowMs: Long = System.currentTimeMillis()): Boolean = expiresAtMs <= nowMs
}

/** west, south, east and north in degrees. */
data class IncidentBox(val west: Double, val south: Double, val east: Double, val north: Double) {
    fun contains(lat: Double, lng: Double): Boolean = lat in south..north && lng in west..east
    fun contains(other: IncidentBox): Boolean =
        west <= other.west && south <= other.south && east >= other.east && north >= other.north
    fun query(): String = listOf(west, south, east, north).joinToString(",") { String.format(java.util.Locale.ROOT, "%.5f", it) }

    companion object {
        /** A box [halfDeg] of latitude either side of a point, as wide on the ground east-west. */
        fun around(lat: Double, lng: Double, halfDeg: Double): IncidentBox {
            val halfLng = (halfDeg / kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.2)).coerceAtMost(1.45)
            return IncidentBox(
                (lng - halfLng).coerceIn(-180.0, 180.0), (lat - halfDeg).coerceIn(-90.0, 90.0),
                (lng + halfLng).coerceIn(-180.0, 180.0), (lat + halfDeg).coerceIn(-90.0, 90.0),
            )
        }
    }
}

class IncidentApiException(val status: Int, message: String, val retryAfterMs: Long? = null) : Exception(message)

data class IncidentCreated(val incident: Incident, val merged: Boolean, val withdrawToken: String?)

data class IncidentVoted(val incident: Incident, val active: Boolean)

/** One change from the live stream: [removed] false is a new or changed report. */
data class IncidentChange(val incident: Incident, val removed: Boolean)

/**
 * Astian's community-report API (services/api, `/v1/incidents`). Nothing here talks to Google:
 * the requests carry [AstianConfig.ASTIAN_UA], the honest identity, and no cookie. The reporter
 * key is a random value the app replaces every day (ui/community/CommunityReports).
 */
@Singleton
class IncidentsClient @Inject constructor(private val http: OkHttpClient) {
    private val json = "application/json".toMediaType()

    /** Held open as long as the stream is followed; the server sends a keepalive every 15 s. */
    private val streamHttp: OkHttpClient by lazy {
        http.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).readTimeout(45, TimeUnit.SECONDS).build()
    }

    suspend fun list(box: IncidentBox): List<Incident> = withContext(Dispatchers.IO) {
        val body = call(get("/incidents?bbox=${box.query()}"))
        val items = JSONObject(body).getJSONArray("items")
        (0 until items.length()).mapNotNull { parseIncident(items.getJSONObject(it)) }
    }

    suspend fun create(
        type: IncidentType,
        subtype: String?,
        lat: Double,
        lng: Double,
        direction: Double?,
        reporterKey: String,
    ): IncidentCreated = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("type", type.wire)
            .put("coordinates", JSONObject().put("latitude", lat).put("longitude", lng))
            .put("reporterKey", reporterKey)
            .put("source", "android")
        if (subtype != null) payload.put("subtype", subtype)
        if (direction != null) payload.put("directionDegrees", ((direction % 360) + 360) % 360)
        val o = JSONObject(call(post("/incidents", payload)))
        IncidentCreated(
            incident = parseIncident(o.getJSONObject("incident")) ?: throw IncidentApiException(0, "unreadable incident"),
            merged = o.optBoolean("merged"),
            withdrawToken = o.optString("withdrawToken").takeIf { it.isNotBlank() },
        )
    }

    suspend fun vote(id: String, present: Boolean, reporterKey: String): IncidentVoted = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("vote", if (present) "present" else "cleared").put("reporterKey", reporterKey)
        val o = JSONObject(call(post("/incidents/$id/votes", payload)))
        IncidentVoted(
            incident = parseIncident(o.getJSONObject("incident")) ?: throw IncidentApiException(0, "unreadable incident"),
            active = o.optBoolean("active", true),
        )
    }

    suspend fun withdraw(id: String, token: String) = withContext(Dispatchers.IO) {
        call(post("/incidents/$id/withdraw", JSONObject().put("withdrawToken", token)))
        Unit
    }

    /** The server-sent events for [box] until the collector stops or the connection drops. The
     *  read blocks an IO thread; cancelling the collector cancels the call, which ends it. */
    fun follow(box: IncidentBox): Flow<IncidentChange> = channelFlow {
        val call = streamHttp.newCall(get("/incidents/stream?bbox=${box.query()}", accept = "text/event-stream"))
        invokeOnClose { call.cancel() }
        call.execute().use { response ->
            if (!response.isSuccessful) throw IncidentApiException(response.code, "stream HTTP ${response.code}")
            val source = response.body?.source() ?: return@use
            var event = ""
            val data = StringBuilder()
            while (!isClosedForSend) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.isEmpty() -> {
                        if (data.isNotEmpty() && (event == "incident" || event == "removed")) {
                            runCatching { JSONObject(data.toString()) }.getOrNull()?.let(::parseIncident)?.let {
                                trySend(IncidentChange(it, removed = event == "removed"))
                            }
                        }
                        event = ""
                        data.setLength(0)
                    }
                    line.startsWith("event:") -> event = line.substring(6).trim()
                    line.startsWith("data:") -> data.append(line.substring(5).trim())
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun get(path: String, accept: String = "application/json"): Request =
        Request.Builder().url(AstianLinks.communityApi() + path)
            .header("User-Agent", AstianConfig.ASTIAN_UA)
            .header("Accept", accept)
            .build()

    private fun post(path: String, body: JSONObject): Request =
        Request.Builder().url(AstianLinks.communityApi() + path)
            .header("User-Agent", AstianConfig.ASTIAN_UA)
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(json))
            .build()

    private fun call(request: Request): String =
        http.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull().orEmpty()
                val retryAfter = r.header("Retry-After")?.let { value ->
                    value.toLongOrNull()?.coerceIn(0, 86_400)?.times(1_000) ?: runCatching {
                        (java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0)
                    }.getOrNull()
                }
                throw IncidentApiException(r.code, detail.ifBlank { "HTTP ${r.code}" }, retryAfter)
            }
            text
        }

    companion object {
        /** One incident object of the API, or null when its type is one this build does not know. */
        fun parseIncident(o: JSONObject): Incident? {
            val type = IncidentType.of(o.optString("type")) ?: return null
            val at = o.getJSONObject("coordinates")
            val road = o.optJSONObject("road")
            val line = road?.optJSONArray("line")?.let { points ->
                (0 until points.length()).mapNotNull { i ->
                    points.optJSONArray(i)?.takeIf { it.length() >= 2 }?.let { it.getDouble(1) to it.getDouble(0) }
                }.takeIf { it.size >= 2 }
            }
            return Incident(
                id = o.getString("id"),
                type = type,
                subtype = o.optString("subtype").takeIf { it.isNotBlank() && it in type.subtypes },
                lat = at.getDouble("latitude"),
                lng = at.getDouble("longitude"),
                direction = if (o.has("directionDegrees") && !o.isNull("directionDegrees")) o.getDouble("directionDegrees") else null,
                createdAtMs = instantMs(o.getString("createdAt")),
                expiresAtMs = instantMs(o.getString("expiresAt")),
                confidence = o.optDouble("confidence", 0.5),
                confirmations = o.optInt("confirmations"),
                dismissals = o.optInt("dismissals"),
                source = o.optString("source").takeIf { it.isNotBlank() },
                lastConfirmedAtMs = o.optString("lastConfirmedAt").takeIf { it.isNotBlank() && it != "null" }?.let(::instantMs),
                roadName = road?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
                line = line,
            )
        }

        private fun instantMs(iso: String): Long = java.time.Instant.parse(iso).toEpochMilli()
    }
}
