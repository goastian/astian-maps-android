package org.astian.suite.maps.core.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.URI

/**
 * The pure rules for adopting a remote settings bundle, kept free of Android types so they run
 * on the JVM. [CalibrationStore] does the I/O and asks this object what to keep.
 *
 * A bundle comes in two kinds. A **signed** one verifies against the pinned key and is kept as
 * is, including the fields that run code or steer navigation. An **unsigned** one (the website
 * copy, trusted for TLS and control of the host only) keeps its plain data and loses
 * [STRIPPED_FIELDS], so a compromised web host can change a request template but cannot run
 * script, move where the app fetches things from, or show users text and links.
 */
object CalibrationPolicy {
    /** Largest bundle body accepted from any source, in bytes. */
    const val MAX_BYTES = 256 * 1024

    /** The fields an unsigned bundle never keeps: JavaScript, link rewrites, user-facing
     *  notices and the selectors injected into the hidden WebViews. */
    val STRIPPED_FIELDS = listOf("transformsJs", "links", "notices", "reviewSelectors")

    private val ALLOWED_HOSTS = setOf("www.google.com", "google.com")

    /** A bundle that passed every check. [body] is exactly what is written to the cache: the raw
     *  text when [signed] (with its [sig]), the text without [STRIPPED_FIELDS] when not. */
    class Accepted(
        val calibration: Calibration,
        val body: String,
        val sig: String?,
        val signed: Boolean,
    )

    /**
     * Checks [body]: size, an integer `version`, parsing, and the host allowlist. With
     * [requireSignature] a missing or invalid [sig] rejects it; without, an invalid or absent
     * one makes it an unsigned bundle (so its [STRIPPED_FIELDS] are removed) and a valid one
     * makes it signed.
     */
    fun evaluate(
        body: String,
        sig: String?,
        publicKeyB64: String,
        requireSignature: Boolean,
        parse: (String) -> Calibration? = { CalibrationStore.parseBundle(it) },
    ): Accepted? {
        if (body.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return null
        if (!hasIntegerVersion(body)) return null
        val signed = sig != null && BundleSignature.verify(body.toByteArray(Charsets.UTF_8), sig, publicKeyB64)
        if (requireSignature && !signed) return null
        val stored = if (signed) body else stripJson(body) ?: return null
        val parsed = parse(stored) ?: return null
        val calibration = if (signed) parsed else stripUnsigned(parsed)
        if (!isAllowed(calibration)) return null
        return Accepted(calibration, stored, sig.takeIf { signed }, signed)
    }

    fun stripUnsigned(c: Calibration): Calibration =
        c.copy(transformsJs = null, links = emptyMap(), notices = emptyList(), reviewSelectors = null)

    /** [body] as JSON without [STRIPPED_FIELDS], or null when it is not a JSON object. */
    fun stripJson(body: String): String? = runCatching {
        val o = JSON.parseToJsonElement(body).jsonObject
        JsonObject(o.filterKeys { it !in STRIPPED_FIELDS }).toString()
    }.getOrNull()

    /** True iff a candidate with this version and trust may replace the active bundle: strictly
     *  newer, or the same version when it is signed and the active one is not (that restores
     *  the stripped fields without waiting for a version bump). */
    fun supersedes(candidate: Accepted, activeVersion: Int, activeSigned: Boolean): Boolean {
        val v = candidate.calibration.version
        return v > activeVersion || (v == activeVersion && candidate.signed && !activeSigned)
    }

    /** The highest version wins; at the same version a signed bundle beats an unsigned one, and
     *  of two equals the earlier in [candidates] wins. */
    fun best(candidates: List<Accepted>): Accepted? =
        candidates.maxWithOrNull(compareBy<Accepted>({ it.calibration.version }, { if (it.signed) 1 else 0 }))

    /** Security gate: every endpoint must point at an allow-listed host, so a compromised
     *  config can never send requests to an attacker's server. */
    fun isAllowed(c: Calibration): Boolean {
        val hosts = listOf(c.searchEndpoint, c.directionsEndpoint, c.reviewsEndpoint, c.photosEndpoint, c.sessionWarmUrl, c.suggestEndpoint)
            .map { runCatching { URI(it).host }.getOrNull() }
        return hosts.all { it != null && it in ALLOWED_HOSTS } && AstianLinks.acceptable(c.links)
    }

    private val JSON = Json { ignoreUnknownKeys = true }

    private fun hasIntegerVersion(body: String): Boolean = runCatching {
        val v = JSON.parseToJsonElement(body).jsonObject["version"] as? JsonPrimitive
        v != null && !v.isString && v.content.toIntOrNull() != null
    }.getOrDefault(false)
}
