package org.astian.suite.maps.core.config

import android.content.Context
import org.astian.suite.maps.core.AstianConfig
import org.astian.suite.maps.core.data.google.BrowserHeaders
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the active [Calibration] and refreshes it at launch from two kinds of channel.
 *
 * On construction it loads the best valid cached copy (signed or unsigned, whichever has the
 * higher version, signed on a tie) if it is not older than the bundled [DEFAULT], else uses
 * [DEFAULT], so the app always starts with a working config, offline. [refresh] (called once
 * at startup) gathers candidates:
 *   1. the website's `calibration.json` ([AstianLinks.CALIBRATION_WEB]) over HTTPS, **no
 *      signature required**. If a `calibration.json.sig` beside it verifies against
 *      [PINNED_PUBLIC_KEY] the bundle counts as signed; otherwise it is unsigned, and
 *   2. the first of the signed sources (pushed link, compiled URL, mirror, compiled mirror)
 *      whose detached signature **verifies** against [PINNED_PUBLIC_KEY].
 * Every candidate must also be at most [CalibrationPolicy.MAX_BYTES], carry an integer
 * `version`, and name only allow-listed hosts for every endpoint; the best candidate (highest
 * version, signed first on a tie) is adopted if it is newer than the active one.
 *
 * Trust differs by channel. A signed bundle may carry everything: pb/endpoint/path config,
 * user-facing [Notice]s, `links` and a sandboxed JavaScript transform bundle
 * ([Calibration.transformsJs]). An unsigned one is only as trustworthy as TLS plus control of
 * the web host, so [CalibrationPolicy] strips the code- and navigation-affecting fields from it
 * (`transformsJs`, `links`, `notices`, `reviewSelectors`) before it can become active, and the
 * unsigned cache never stores them.
 */
@Singleton
class CalibrationStore @Inject constructor(
    @ApplicationContext context: Context,
    private val http: OkHttpClient,
) {
    private val cacheFile = File(context.filesDir, "calibration.json")
    private val sigFile = File(context.filesDir, "calibration.json.sig")
    private val unsignedCacheFile = File(context.filesDir, "calibration.unsigned.json")

    @Volatile
    private var activeSigned = true

    @Volatile
    private var active: Calibration = (loadCached()?.also { activeSigned = it.signed }?.calibration ?: Calibration.DEFAULT)
        .also { latest = it; AstianLinks.overrides = it.links }

    @Volatile
    private var refreshed = false

    /** The config every scraper call reads. Never null; never blocks. */
    fun current(): Calibration = active

    suspend fun refresh() {
        if (refreshed) return
        refreshed = true
        withContext(Dispatchers.IO) {
            runCatching {
                // A host that does not resolve, refuses or times out moves on to the next
                // address, as a non-2xx reply does; and so does a copy that answers with the
                // wrong bytes (a site's catch-all page, a stale mirror).
                val candidates = mutableListOf<CalibrationPolicy.Accepted>()
                val webUrl = AstianLinks.calibrationWeb().takeIf { it.startsWith("https://") }
                if (webUrl != null) {
                    fetchWeb(webUrl)?.let { body ->
                        val sig = fetchQuiet("$webUrl.sig")
                        CalibrationPolicy.evaluate(body, sig, PINNED_PUBLIC_KEY, requireSignature = false)
                            ?.let(candidates::add)
                    }
                }
                // The signed file is asked for where the last bundle said it lives (a moved
                // repository announces its new place through the old one first); when that does
                // not answer, where this build was compiled to look; and when GitHub itself does
                // not answer, the copy on the website. Anything not signed is rejected here: this
                // is what makes pushing CODE safe.
                val signedAt = listOf(
                    AstianLinks.calibration(),
                    AstianLinks.default(AstianLinks.CALIBRATION),
                    AstianLinks.calibrationMirror(),
                    AstianLinks.default(AstianLinks.CALIBRATION_MIRROR),
                ).distinct().filter { it != webUrl }
                for (url in signedAt) {
                    val b = fetchQuiet(url) ?: continue
                    val s = fetchQuiet("$url.sig") ?: continue
                    val accepted = CalibrationPolicy.evaluate(b, s, PINNED_PUBLIC_KEY, requireSignature = true) ?: continue
                    candidates.add(accepted)
                    break
                }
                val remote = CalibrationPolicy.best(
                    candidates.filter { CalibrationPolicy.supersedes(it, active.version, activeSigned) },
                ) ?: return@runCatching
                runCatching {
                    if (remote.signed) {
                        cacheFile.writeText(remote.body)
                        sigFile.writeText(remote.sig.orEmpty())
                    } else {
                        unsignedCacheFile.writeText(remote.body)
                    }
                }
                activeSigned = remote.signed
                active = remote.calibration
                latest = remote.calibration
                AstianLinks.overrides = remote.calibration.links
            }
        }
    }

    private fun fetchQuiet(url: String): String? = runCatching { fetch(http, url) }.getOrNull()

    /** The unsigned channel: HTTPS only, no redirect off https or off the requested host. */
    private fun fetchWeb(url: String): String? = runCatching {
        val client = http.newBuilder().followSslRedirects(false).build()
        val host = url.toHttpUrl().host
        fetch(client, url) { it.request.url.isHttps && it.request.url.host == host }
    }.getOrNull()

    private fun fetch(client: OkHttpClient, url: String, accept: (Response) -> Boolean = { true }): String? {
        // Astian Maps' own repo - the honest, contactable identifier, not the scrape's browser UA.
        val req = Request.Builder().url(url).header("User-Agent", AstianConfig.ASTIAN_UA).build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful || !accept(resp)) return null
            val source = resp.body.source()
            // One byte past the limit is enough to know the body is too big, without reading it all.
            source.request(CalibrationPolicy.MAX_BYTES + 1L)
            if (source.buffer.size > CalibrationPolicy.MAX_BYTES) null else source.readString(Charsets.UTF_8)
        }
    }

    /** Both caches are re-checked as if freshly fetched: the signed one must still verify, the
     *  unsigned one is stripped again, so a cache tampered on disk or written by an older build
     *  falls back to the bundled DEFAULT for one launch. */
    private fun loadCached(): CalibrationPolicy.Accepted? = runCatching {
        val signedCache = runCatching {
            if (cacheFile.exists() && sigFile.exists()) {
                CalibrationPolicy.evaluate(cacheFile.readText(), sigFile.readText(), PINNED_PUBLIC_KEY, requireSignature = true)
            } else null
        }.getOrNull()
        val unsignedCache = runCatching {
            if (unsignedCacheFile.exists()) {
                CalibrationPolicy.evaluate(unsignedCacheFile.readText(), null, PINNED_PUBLIC_KEY, requireSignature = false)
            } else null
        }.getOrNull()
        CalibrationPolicy.best(listOfNotNull(signedCache, unsignedCache))
            ?.takeIf { it.calibration.version >= Calibration.DEFAULT.version }
    }.getOrNull()

    companion object {
        private val BUNDLE_JSON = Json { ignoreUnknownKeys = true }

        /** Lenient JSON → Calibration: any missing field falls back to [DEFAULT], so
         *  a remote file can override just the one thing that drifted. */
        fun parseBundle(body: String): Calibration? = runCatching {
            val o = BUNDLE_JSON.parseToJsonElement(body).jsonObject
            fun str(k: String, fallback: String): String =
                (o[k] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: fallback
            val version = (o["version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return@runCatching null
            val d = Calibration.DEFAULT
        // SANITIZED, not merely defaulted: an unsendable value (a control character, an absurd
        // length) must fall back to the compiled UA rather than reach OkHttp, which throws at
        // request-build time inside a runCatching that swallows it: one stray newline in a
        // pushed bundle would otherwise kill every scrape with no crash and no log.
        val ua = BrowserHeaders.sanitize((o["userAgent"] as? JsonPrimitive)?.content) ?: d.userAgent
            // Field-index paths: each value is a JSON array of ints; merge the remote
            // ones over the bundled defaults so a file can override just one path.
            val remotePaths = (o["paths"] as? JsonObject)?.mapNotNull { (k, v) ->
                val list = (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }
                if (!list.isNullOrEmpty()) k to list else null
            }?.toMap().orEmpty()
            val remoteDirPaths = (o["directionsPaths"] as? JsonObject)?.mapNotNull { (k, v) ->
                val list = (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }
                if (!list.isNullOrEmpty()) k to list else null
            }?.toMap().orEmpty()
            val remoteSuggestPaths = (o["suggestPaths"] as? JsonObject)?.mapNotNull { (k, v) ->
                val list = (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }
                if (!list.isNullOrEmpty()) k to list else null
            }?.toMap().orEmpty()
            // Plain string maps (review words / selectors): blank values skipped, empty map = absent.
            fun strMap(k: String): Map<String, String>? =
                (o[k] as? JsonObject)?.mapNotNull { (key, v) ->
                    (v as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { key to it }
                }?.toMap()?.takeIf { it.isNotEmpty() }
            val notices = (o["notices"] as? JsonArray)?.mapNotNull { el ->
                val n = el as? JsonObject ?: return@mapNotNull null
                fun s(k: String): String? = (n[k] as? JsonPrimitive)?.content
                val id = s("id") ?: return@mapNotNull null
                val title = s("title") ?: return@mapNotNull null
                Notice(
                    id = id,
                    level = s("level") ?: Notice.LEVEL_INFO,
                    title = title,
                    body = s("body") ?: "",
                    url = s("url"),
                )
            }.orEmpty()
            val transformsJs = (o["transformsJs"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            // Word-table overrides, lenient like tuning: bad entries are skipped, an empty result
            // reads as absent (null = compiled tables) so a malformed block can't blank a language.
            // These fields existed in Calibration from day one but were never parsed here - every
            // remote bundle silently dropped them (found chasing the Hebrew openNow bug, 2026-07-19).
            fun wordMap(k: String): Map<String, List<String>>? =
                (o[k] as? JsonObject)?.mapNotNull { (lang, v) ->
                    val words = (v as? JsonArray)
                        ?.mapNotNull { w -> (w as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } }
                    if (!words.isNullOrEmpty()) lang to words else null
                }?.toMap()?.takeIf { it.isNotEmpty() }
            fun wordList(k: String): List<String>? =
                (o[k] as? JsonArray)
                    ?.mapNotNull { w -> (w as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } }
                    ?.takeIf { it.isNotEmpty() }
            val stopBoardIndices = (o["stopBoardIndices"] as? JsonObject)?.mapNotNull { (k, v) ->
                (v as? JsonPrimitive)?.content?.toIntOrNull()?.let { k to it }
            }?.toMap()?.takeIf { it.isNotEmpty() }
            Calibration(
                version = version,
                searchEndpoint = str("searchEndpoint", d.searchEndpoint),
                searchPb = str("searchPb", d.searchPb),
                directionsEndpoint = str("directionsEndpoint", d.directionsEndpoint),
                directionsPb = str("directionsPb", d.directionsPb),
                reviewsEndpoint = str("reviewsEndpoint", d.reviewsEndpoint),
                reviewsPb = str("reviewsPb", d.reviewsPb),
                sessionWarmUrl = str("sessionWarmUrl", d.sessionWarmUrl),
                userAgent = ua,
                // DERIVED from the UA's major whenever it can be read, so the pair can never disagree
                // (Chrome computes the whole header from the major; see BrowserHeaders.secChUaFor).
                // The pushed field only matters for a UA that names no Chrome major.
                secChUa = BrowserHeaders.chromeMajor(ua)?.toIntOrNull()?.let { BrowserHeaders.secChUaFor(it) }
                    ?: BrowserHeaders.sanitize((o["secChUa"] as? JsonPrimitive)?.content)
                    ?: d.secChUa,
                chromeFullVersion = BrowserHeaders.sanitize((o["chromeFullVersion"] as? JsonPrimitive)?.content) ?: d.chromeFullVersion,
                photosEndpoint = str("photosEndpoint", d.photosEndpoint),
                photosProto = str("photosProto", d.photosProto),
                rpcContext = (o["rpcContext"] as? JsonPrimitive)?.content?.let { BrowserHeaders.sanitize(it) ?: "" } ?: d.rpcContext,
                reviewFeedProto = str("reviewFeedProto", d.reviewFeedProto),
                streetViewSearchBody = str("streetViewSearchBody", d.streetViewSearchBody),
                paths = Calibration.DEFAULT_PATHS + remotePaths,
                notices = notices,
                transformsJs = transformsJs,
                defaultVoiceSpeaker = (o["defaultVoiceSpeaker"] as? JsonPrimitive)?.content?.toIntOrNull() ?: d.defaultVoiceSpeaker,
                defaultVoiceSpeed = (o["defaultVoiceSpeed"] as? JsonPrimitive)?.content?.toFloatOrNull() ?: d.defaultVoiceSpeed,
                defaultVoiceId = (o["defaultVoiceId"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: d.defaultVoiceId,
                defaultMapPalette = (o["defaultMapPalette"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: d.defaultMapPalette,
                defaultPlacesSource = (o["defaultPlacesSource"] as? JsonPrimitive)?.content?.takeIf { it == "open" || it == "both" || it == "google" } ?: d.defaultPlacesSource,
                classicRoutePicker = (o["classicRoutePicker"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: d.classicRoutePicker,
                // Numbers only, leniently: a non-numeric value is skipped rather than failing the
                // bundle, so one bad dial can never take down the whole calibration.
                tuning = (o["tuning"] as? JsonObject)?.mapNotNull { (k, v) ->
                    (v as? JsonPrimitive)?.content?.toDoubleOrNull()?.let { k to it }
                }?.toMap() ?: d.tuning,
                statusClosedWords = wordMap("statusClosedWords"),
                statusOpenWords = wordMap("statusOpenWords"),
                transitCategoryWords = wordList("transitCategoryWords"),
                transitExcludeWords = wordList("transitExcludeWords"),
                stopBoardIndices = stopBoardIndices,
                directionsPaths = Calibration.DEFAULT_DIRECTIONS_PATHS + remoteDirPaths,
                suggestEndpoint = str("suggestEndpoint", d.suggestEndpoint),
                suggestPb = str("suggestPb", d.suggestPb),
                suggestPaths = Calibration.DEFAULT_SUGGEST_PATHS + remoteSuggestPaths,
                reviewWords = strMap("reviewWords"),
                reviewSelectors = strMap("reviewSelectors"),
                links = strMap("links") ?: emptyMap(),
            )
        }.getOrNull()

        /** The last applied bundle, readable WITHOUT an injected store - the view layer's
         *  tuning dials (zoom defaults, overlay threshold) live too deep in composables to
         *  plumb the store into. Set at init (cached bundle or compiled default) and on every
         *  accepted refresh; always signature-verified content, stripped unsigned content, or [Calibration.DEFAULT]. */
        @Volatile
        var latest: Calibration = Calibration.DEFAULT
            private set

        // EC P-256 public key (SPKI, base64) — the private half lives only in
        // ~/.astian-signing/astian-calibration.key, never in the repo. Embedding the
        // PUBLIC key is safe; it just lets the app reject bundles we didn't sign.
        private const val PINNED_PUBLIC_KEY =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuz8/zxOJFhVqKco74fkmzrLlyPra4/pTEUm7lmue/Kig0T497fcs+hjhZkaSqVZAwloNrr0+0ILi7yATmU+d3g=="
    }
}
