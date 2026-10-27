package org.astian.suite.maps.ui.map

import java.io.File
import org.astian.suite.maps.core.data.tiles.MapStyle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledLookTest {
    private fun asset(name: String): JSONObject {
        val file = listOf("src/main/assets/$name", "app/src/main/assets/$name").map(::File).first { it.isFile }
        return JSONObject(file.readText())
    }

    @Test
    fun astianIsTheDefaultLookAndFollowsTheTheme() {
        assertEquals(MapStyle.ASTIAN, MapStyle.DEFAULT)
        assertTrue(MapStyle.ASTIAN.bundled)
        assertTrue(MapStyle.ASTIAN.themed)
        assertFalse(MapStyle.ASTIAN.painted)
        assertEquals("asset://styles/astian-light.json", MapStyle.ASTIAN.uriFor(dark = false))
        assertEquals("asset://styles/astian-dark.json", MapStyle.ASTIAN.uriFor(dark = true))
        assertEquals(MapStyle.LIBERTY.uri, MapStyle.LIBERTY.uriFor(dark = true))
        assertFalse(MapStyle.LIBERTY.bundled)
    }

    @Test
    fun bundledStylesAreTheWebStyleOnKeylessTilesWithTheSharedSprite() {
        for (variant in listOf("light", "dark")) {
            val style = asset("styles/astian-$variant.json")
            assertEquals("Astian Maps — $variant", style.getString("name"))
            assertEquals(113, style.getJSONArray("layers").length())
            assertEquals("https://tiles.openfreemap.org/planet", style.getJSONObject("sources").getJSONObject("openmaptiles").getString("url"))
            assertEquals("asset://sprites/astian", style.getString("sprite"))
            assertTrue(style.getString("glyphs").startsWith("https://"))
            val sprite = asset("sprites/astian.json")
            val layers = style.getJSONArray("layers")
            for (i in 0 until layers.length()) {
                val icon = layers.getJSONObject(i).optJSONObject("layout")?.optString("icon-image").orEmpty()
                if (icon.isNotEmpty() && !icon.contains("{")) assertTrue("missing sprite $icon", sprite.has(icon))
            }
        }
    }
}
