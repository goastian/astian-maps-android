package org.astian.suite.maps.core

import org.astian.suite.maps.core.data.StreetViewCache
import org.astian.suite.maps.core.model.LatLng
import org.astian.suite.maps.core.model.StreetViewPano
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreetViewCacheTest {
    private fun pano(id: String, lat: Double, lng: Double) = StreetViewPano(panoId = id, lat = lat, lng = lng)

    @Test
    fun `a stored pano comes back by id and by position, and clear removes it`() {
        val dir = Files.createTempDirectory("sv").toFile()
        StreetViewCache.saveMeta(dir, pano("abc", 38.5435, -121.7400))
        StreetViewCache.saveImage(dir, "abc", byteArrayOf(1, 2, 3))
        assertEquals("abc", StreetViewCache.loadMeta(dir, "abc")?.panoId)
        assertEquals("abc", StreetViewCache.nearest(dir, LatLng(38.54355, -121.74005))?.panoId)
        assertNull(StreetViewCache.nearest(dir, LatLng(38.56, -121.70)))
        StreetViewCache.clear(dir)
        assertNull(StreetViewCache.loadMeta(dir, "abc"))
    }

    @Test
    fun `metadata with no image is not offered`() {
        val dir = Files.createTempDirectory("sv").toFile()
        StreetViewCache.saveMeta(dir, pano("noimg", 38.5435, -121.7400))
        assertNull(StreetViewCache.nearest(dir, LatLng(38.5435, -121.7400)))
    }
}
