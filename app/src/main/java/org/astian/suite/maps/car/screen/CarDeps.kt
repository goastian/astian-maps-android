package org.astian.suite.maps.car.screen

import androidx.car.app.CarContext
import org.astian.suite.maps.car.CarMapRenderer
import org.astian.suite.maps.core.data.MapDataSource
import org.astian.suite.maps.core.data.PlaceShortcutStore
import org.astian.suite.maps.core.data.RecentPlaceStore
import org.astian.suite.maps.core.data.RouteEngine
import org.astian.suite.maps.core.data.SavedPlaceStore
import org.astian.suite.maps.core.location.LocationProvider
import org.astian.suite.maps.core.nav.NavSession
import org.astian.suite.maps.core.voice.VoiceGuide

/** The `:core` singletons the car screens share (all the same instances the phone app uses). */
data class CarDeps(
    val navSession: NavSession,
    val locationProvider: LocationProvider,
    val mapDataSource: MapDataSource,
    val recentPlaces: RecentPlaceStore,
    val savedPlaces: SavedPlaceStore,
    val shortcuts: PlaceShortcutStore,
    val voiceGuide: VoiceGuide,
    val routeEngine: RouteEngine, // for the speed-limit badge (offline graphs' max_speed)
    // Offline search on the car: the same on-phone place and address stores the phone's offline
    // search reads, so a car search with no signal still finds what the downloaded packs hold.
    val offlinePois: org.astian.suite.maps.core.data.OfflinePoiStore,
    val offlineAddresses: org.astian.suite.maps.core.data.OfflineAddressStore,
    // The shared client, for the UK fuel price file the result rows read (UkFuelPrices.fill).
    val http: okhttp3.OkHttpClient,
) {
    // ONE shared map renderer for the whole car session. Per-screen renderer instances DON'T work:
    // swapping the surface callback to a new instance doesn't re-deliver onSurfaceAvailable, so the
    // new renderer never gets the surface and the map freezes. All screens use this same instance
    // (set as the callback in each onStart) and just switch its mode (browse / preview / nav).
    private var sharedRenderer: CarMapRenderer? = null
    fun mapRenderer(ctx: CarContext): CarMapRenderer =
        sharedRenderer ?: CarMapRenderer(ctx, locationProvider, navSession, routeEngine).also { sharedRenderer = it }
    fun stopMapRenderer() { sharedRenderer?.stop() }
}
