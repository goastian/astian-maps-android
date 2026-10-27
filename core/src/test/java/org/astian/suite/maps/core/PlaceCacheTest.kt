package org.astian.suite.maps.core

import org.astian.suite.maps.core.data.PlaceCache
import org.astian.suite.maps.core.model.LatLng
import org.astian.suite.maps.core.model.Place
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceCacheTest {
    private val online = Place(id = "g:1", name = "Nick The Greek", location = LatLng(38.5435, -121.7400), featureId = "0x1:0x2", rating = 4.7)

    @Test
    fun `an offline result finds the copy saved online by name and position`() {
        val dir = Files.createTempDirectory("pc").toFile()
        PlaceCache.save(dir, online, emptyList())
        val offline = Place(id = "overture:abc", name = "Nick the Greek", location = LatLng(38.54352, -121.74003))
        assertEquals(4.7, PlaceCache.load(dir, offline)?.place?.rating)
    }

    @Test
    fun `the same name across town is another place`() {
        val dir = Files.createTempDirectory("pc").toFile()
        PlaceCache.save(dir, online, emptyList())
        assertNull(PlaceCache.load(dir, Place(id = "overture:zzz", name = "Nick The Greek", location = LatLng(38.56, -121.70))))
    }
}
