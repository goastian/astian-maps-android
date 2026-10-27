package org.astian.suite.maps.core.config

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstianLinksTest {
    @After fun reset() {
        AstianLinks.overrides = emptyMap()
        AstianLinks.compiled = emptyMap()
    }

    @Test fun `defaults derive the files from their bases`() {
        assertEquals("https://github.com/goastian/astian-maps-android/releases/download/obf-regions/obf-manifest.json", AstianLinks.obfManifest())
        assertEquals("https://raw.githubusercontent.com/astian-org/astian-maps/main/calibration.json", AstianLinks.calibration())
        assertEquals(AstianLinks.calibration() + ".sig", AstianLinks.calibrationSig())
        assertEquals("https://maps.astian.org/calibration.json", AstianLinks.calibrationMirror())
        assertEquals("https://astian-org.github.io/astian-maps/fonts", AstianLinks.mapFonts())
        assertEquals("https://maps.astian.org", AstianLinks.site())
        assertEquals("https://maps.astian.org/calibration.json", AstianLinks.calibrationWeb())
        // The website and the Pages address are separate keys: moving the site leaves the fonts.
        AstianLinks.overrides = mapOf(AstianLinks.SITE to "https://astian.example")
        assertEquals("https://astian-org.github.io/astian-maps/fonts", AstianLinks.mapFonts())
        assertEquals("https://github.com/goastian/astian-maps-android/releases/download/voices/x.tar.bz2", AstianLinks.release("voices", "x.tar.bz2"))
        assertEquals("https://api.github.com/repos/astian-org/astian-maps/releases/latest", AstianLinks.api("/releases/latest"))
    }

    @Test fun `the web calibration link is its own key and does not follow the site base`() {
        AstianLinks.overrides = mapOf(AstianLinks.SITE to "https://astian.example")
        assertEquals("https://astian.example/calibration.json", AstianLinks.calibrationMirror())
        assertEquals("https://maps.astian.org/calibration.json", AstianLinks.calibrationWeb())
        assertTrue(AstianLinks.CALIBRATION_WEB in AstianLinks.keys)
    }

    @Test fun `a pushed base moves every file under it, and a pushed key moves that one`() {
        AstianLinks.overrides = mapOf(AstianLinks.RELEASES to "https://github.com/NewName/astian-maps/releases/download")
        assertEquals("https://github.com/NewName/astian-maps/releases/download/obf-regions/obf-manifest.json", AstianLinks.obfManifest())
        assertEquals("https://github.com/NewName/astian-maps/releases/download/fuel-gb/", AstianLinks.fuelGb())
        assertEquals("https://github.com/goastian/astian-maps-android", AstianLinks.repo())
        AstianLinks.overrides = mapOf(AstianLinks.RAW to "https://example.org/astian", AstianLinks.PRIVACY to "https://example.org/privacy")
        assertEquals("https://example.org/astian/calibration.json", AstianLinks.calibration())
        assertEquals("https://example.org/privacy", AstianLinks.privacy())
    }

    @Test fun `a pushed link loses its trailing slash and must be printable ascii`() {
        AstianLinks.overrides = mapOf(AstianLinks.RAW to "https://example.org/astian/")
        assertEquals("https://example.org/astian/calibration.json", AstianLinks.calibration())
        assertEquals(true, AstianLinks.acceptable(mapOf(AstianLinks.REPO to "https://example.org/astian-maps")))
        assertEquals(false, AstianLinks.acceptable(mapOf(AstianLinks.REPO to "https://véla.example")))
        assertEquals(false, AstianLinks.acceptable(mapOf(AstianLinks.REPO to "https://example.org/a b")))
        assertEquals(false, AstianLinks.acceptable(mapOf("nope" to "https://example.org")))
    }

    @Test fun `a build's own link beats the default and loses to a pushed one`() {
        AstianLinks.compiled = mapOf(AstianLinks.OBF_MANIFEST to "http://127.0.0.1:8099/obf-manifest.json", AstianLinks.CELLS_MANIFEST to "")
        assertEquals("http://127.0.0.1:8099/obf-manifest.json", AstianLinks.obfManifest())
        assertEquals(AstianLinks.default(AstianLinks.CELLS_MANIFEST), AstianLinks.cellsManifest())
        AstianLinks.overrides = mapOf(AstianLinks.OBF_MANIFEST to "https://example.org/obf.json")
        assertEquals("https://example.org/obf.json", AstianLinks.obfManifest())
    }

    @Test fun `a bundle may push https links under known names only`() {
        assertTrue(AstianLinks.acceptable(mapOf(AstianLinks.SITE to "https://astian.example")))
        assertFalse(AstianLinks.acceptable(mapOf(AstianLinks.SITE to "http://astian.example")))
        assertFalse(AstianLinks.acceptable(mapOf("somethingElse" to "https://astian.example")))
        assertFalse(AstianLinks.acceptable(mapOf(AstianLinks.SITE to "https://astian.example/a b")))
        val c = CalibrationStore.parseBundle("""{"version": 99, "links": {"site": "https://astian.example", "repo": ""}}""")!!
        assertEquals(mapOf("site" to "https://astian.example"), c.links)
    }
}
