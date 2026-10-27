package org.astian.suite.maps.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class CalibrationPolicyTest {
    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    @After fun reset() {
        AstianLinks.overrides = emptyMap()
    }

    private fun sign(body: String): String {
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(keyPair.private)
        s.update(body.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(s.sign())
    }

    private fun bundle(version: Int = 50, extra: String = ""): String =
        """{"version": $version, "searchPb": "!1d{SPAN}", "tuning": {"zoom": 12.5}$extra}"""

    private val codeFields =
        """, "transformsJs": "globalThis.x = 1", "links": {"site": "https://astian.example"},""" +
            """ "notices": [{"id": "n1", "title": "Hi", "url": "https://evil.example"}],""" +
            """ "reviewSelectors": {"text": ".a"}"""

    private fun unsigned(body: String) = CalibrationPolicy.evaluate(body, null, publicKey, requireSignature = false)

    @Test fun `an unsigned bundle is accepted for its plain data`() {
        val a = unsigned(bundle())!!
        assertFalse(a.signed)
        assertNull(a.sig)
        assertEquals(50, a.calibration.version)
        assertEquals("!1d{SPAN}", a.calibration.searchPb)
        assertEquals(12.5, a.calibration.tuning["zoom"]!!, 0.0)
    }

    @Test fun `an unsigned bundle loses the four code and navigation fields`() {
        val a = unsigned(bundle(extra = codeFields))!!
        assertNull(a.calibration.transformsJs)
        assertEquals(emptyMap<String, String>(), a.calibration.links)
        assertEquals(emptyList<Notice>(), a.calibration.notices)
        assertNull(a.calibration.reviewSelectors)
        assertEquals("!1d{SPAN}", a.calibration.searchPb)
    }

    @Test fun `the text stored for an unsigned bundle never carries the stripped fields`() {
        val a = unsigned(bundle(extra = codeFields))!!
        CalibrationPolicy.STRIPPED_FIELDS.forEach { assertFalse(it, a.body.contains("\"$it\"")) }
        val again = unsigned(a.body)!!
        assertEquals(a.calibration, again.calibration)
        assertNull(again.calibration.transformsJs)
    }

    @Test fun `a signed bundle keeps the four fields`() {
        val body = bundle(extra = codeFields)
        val a = CalibrationPolicy.evaluate(body, sign(body), publicKey, requireSignature = true)!!
        assertTrue(a.signed)
        assertEquals(body, a.body)
        assertEquals("globalThis.x = 1", a.calibration.transformsJs)
        assertEquals(mapOf("site" to "https://astian.example"), a.calibration.links)
        assertEquals(listOf("n1"), a.calibration.notices.map { it.id })
        assertEquals(mapOf("text" to ".a"), a.calibration.reviewSelectors)
    }

    @Test fun `a valid signature is honoured when it is optional`() {
        val body = bundle(extra = codeFields)
        val a = CalibrationPolicy.evaluate(body, sign(body), publicKey, requireSignature = false)!!
        assertTrue(a.signed)
        assertNotNull(a.calibration.transformsJs)
    }

    @Test fun `an invalid signature is unsigned when optional and a rejection when required`() {
        val body = bundle(extra = codeFields)
        val bad = sign(body.replace("50", "51"))
        val optional = CalibrationPolicy.evaluate(body, bad, publicKey, requireSignature = false)!!
        assertFalse(optional.signed)
        assertNull(optional.calibration.transformsJs)
        assertNull(CalibrationPolicy.evaluate(body, bad, publicKey, requireSignature = true))
        assertNull(CalibrationPolicy.evaluate(body, null, publicKey, requireSignature = true))
    }

    @Test fun `an endpoint off the allowlist is rejected signed or not`() {
        val body = bundle(extra = """, "searchEndpoint": "https://evil.example/search"""")
        assertNull(unsigned(body))
        assertNull(CalibrationPolicy.evaluate(body, sign(body), publicKey, requireSignature = true))
        val lookalike = bundle(extra = """, "sessionWarmUrl": "https://www.google.com.evil.example/maps"""")
        assertNull(unsigned(lookalike))
        assertNotNull(unsigned(bundle(extra = """, "suggestEndpoint": "https://www.google.com/s?x=1"""")))
    }

    @Test fun `a body over the size limit is rejected`() {
        val padding = "x".repeat(CalibrationPolicy.MAX_BYTES)
        val big = bundle(extra = """, "_comment": "$padding"""")
        assertNull(unsigned(big))
        assertNull(CalibrationPolicy.evaluate(big, sign(big), publicKey, requireSignature = true))
        assertNotNull(unsigned(bundle(extra = """, "_comment": "${"x".repeat(1000)}"""")))
    }

    @Test fun `the version must be an integer`() {
        assertNull(unsigned("""{"version": "50"}"""))
        assertNull(unsigned("""{"version": 50.5}"""))
        assertNull(unsigned("""{"version": null}"""))
        assertNull(unsigned("""{"searchPb": "x"}"""))
        assertNull(unsigned("not json"))
        assertNull(unsigned("[1, 2]"))
    }

    @Test fun `an unsigned bundle needs a strictly higher version`() {
        val a = unsigned(bundle(version = 50))!!
        assertTrue(CalibrationPolicy.supersedes(a, activeVersion = 49, activeSigned = true))
        assertFalse(CalibrationPolicy.supersedes(a, activeVersion = 50, activeSigned = true))
        assertFalse(CalibrationPolicy.supersedes(a, activeVersion = 50, activeSigned = false))
        assertFalse(CalibrationPolicy.supersedes(a, activeVersion = 51, activeSigned = false))
    }

    @Test fun `a signed bundle at the same version replaces an unsigned active one only`() {
        val body = bundle(version = 50)
        val s = CalibrationPolicy.evaluate(body, sign(body), publicKey, requireSignature = true)!!
        assertTrue(CalibrationPolicy.supersedes(s, activeVersion = 50, activeSigned = false))
        assertFalse(CalibrationPolicy.supersedes(s, activeVersion = 50, activeSigned = true))
        assertFalse(CalibrationPolicy.supersedes(s, activeVersion = 51, activeSigned = false))
    }

    @Test fun `the highest version wins and signed beats unsigned at equal version`() {
        val body = bundle(version = 50)
        val signed = CalibrationPolicy.evaluate(body, sign(body), publicKey, requireSignature = true)!!
        val unsignedSame = unsigned(bundle(version = 50))!!
        val unsignedNewer = unsigned(bundle(version = 51))!!
        assertSame(signed, CalibrationPolicy.best(listOf(unsignedSame, signed)))
        assertSame(signed, CalibrationPolicy.best(listOf(signed, unsignedSame)))
        assertSame(unsignedNewer, CalibrationPolicy.best(listOf(signed, unsignedNewer)))
        assertNull(CalibrationPolicy.best(emptyList()))
    }

    @Test fun `links that are not acceptable reject a signed bundle`() {
        val body = bundle(extra = """, "links": {"site": "http://plain.example"}""")
        assertNull(CalibrationPolicy.evaluate(body, sign(body), publicKey, requireSignature = true))
        assertNotNull(unsigned(body))
    }
}
