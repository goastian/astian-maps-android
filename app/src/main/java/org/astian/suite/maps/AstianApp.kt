package org.astian.suite.maps

import android.app.Application
import android.content.Context
import org.astian.suite.maps.core.diag.DiagLog
import org.astian.suite.maps.diag.CrashCatcher
import org.astian.suite.maps.ui.AppLocale
import org.astian.suite.maps.ui.Onboarding
import org.astian.suite.maps.ui.Traffic
import org.astian.suite.maps.ui.TransitLayer
import org.astian.suite.maps.ui.Units
import org.astian.suite.maps.ui.theme.AppTheme
import org.astian.suite.maps.ui.theme.DynamicColor
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AstianApp : Application(), coil.ImageLoaderFactory {
    @Inject lateinit var diag: DiagLog
    @Inject lateinit var http: okhttp3.OkHttpClient
    @Inject lateinit var locationProvider: org.astian.suite.maps.core.location.LocationProvider
    // Built here, before anything below asks for a link: the store applies the cached bundle's
    // pushed links as it is made, and the fonts and camera fetches at start read them.
    @Inject lateinit var calibrationStore: org.astian.suite.maps.core.config.CalibrationStore

    /** Coil with a HARD memory-cache cap. The default budget is ~25% of the app's heap CLASS,
     *  and largeHeap makes that class huge - on a 512 MB large heap Coil happily retains up to
     *  ~128 MB of decoded gallery bitmaps by design, which is most of the "rapid place churn
     *  runs into the ceiling" OOM (issue #182; measured: 3 gallery-bearing places grew the live
     *  Dalvik heap 14 -> 94 MB). 48 MB still holds a couple of screens of thumbnails + a hero
     *  or two; everything else re-decodes from Coil's disk cache, which is untouched. */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        // The shared client (Google hosts go over Cronet and are counted), with Chrome's image
        // headers put on Google image requests first; Coil's own client said "okhttp/4.12.0".
        .okHttpClient { http.newBuilder().apply { interceptors().add(0, org.astian.suite.maps.core.net.GoogleTransport.imageHeaders) }.build() }
        .memoryCache {
            coil.memory.MemoryCache.Builder(this)
                .maxSizeBytes(if (org.astian.suite.maps.ui.MemoryPressure.lowRam) 16 * 1024 * 1024 else 48 * 1024 * 1024)
                .build()
        }
        .build()

    /** Apply the persisted in-app language to the Application context too (no-op when following the
     *  system), so `getString` from the ViewModel/nav-notification also localizes — resolved at launch
     *  from the saved pref (an in-session change re-reads it on next launch). */
    /**
     * Hand OS memory pressure to every holder that owns a large or native allocation (ported from
     * astian-dpad, 2026-07-23). Before this existed nothing in the app implemented
     * ComponentCallbacks2, so a TRIM_MEMORY_COMPLETE released nothing at all and the OS had no
     * option but to kill us. Coil's own cache is trimmed here; everything else releases through
     * [org.astian.suite.maps.ui.MemoryPressure].
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        org.astian.suite.maps.ui.MemoryPressure.dispatch(level)
        org.astian.suite.maps.diag.TripNote.add("memory: the system asked for memory back (level $level)")
        if (org.astian.suite.maps.ui.MemoryPressure.isSevere(level)) {
            runCatching { coil.Coil.imageLoader(this).memoryCache?.clear() }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(org.astian.suite.maps.ui.AdaptiveDensity.wrap(base)))
    }

    override fun onCreate() {
        super.onCreate()
        // Before anything restores the voice engine: no neural runtime for this chip, no neural voice.
        org.astian.suite.maps.core.voice.AstianPiper.runtimeAvailable = org.astian.suite.maps.voice.NeuralRuntime.supported
        // A build's own data links (a local test server, -PobfManifestUrl= and the like), before
        // anything fetches. Blank means the compiled default in AstianLinks.
        org.astian.suite.maps.core.config.AstianLinks.compiled = mapOf(
            org.astian.suite.maps.core.config.AstianLinks.OBF_MANIFEST to BuildConfig.OBF_MANIFEST_URL,
            org.astian.suite.maps.core.config.AstianLinks.OVERLAY_MANIFEST to BuildConfig.OVERLAY_MANIFEST_URL,
            org.astian.suite.maps.core.config.AstianLinks.MAXSPEED_MANIFEST to BuildConfig.MAXSPEED_MANIFEST_URL,
            org.astian.suite.maps.core.config.AstianLinks.ADDRESS_MANIFEST to BuildConfig.ADDRESS_MANIFEST_URL,
            org.astian.suite.maps.core.config.AstianLinks.PLACES_MANIFEST to BuildConfig.PLACES_MANIFEST_URL,
            org.astian.suite.maps.core.config.AstianLinks.CELLS_MANIFEST to BuildConfig.CELLS_MANIFEST_URL,
            org.astian.suite.maps.core.config.AstianLinks.BASEMAP_MANIFEST to BuildConfig.BASEMAP_MANIFEST_URL,
            org.astian.suite.maps.core.config.AstianLinks.WORLD_BASEMAP to BuildConfig.WORLD_BASEMAP_URL,
            org.astian.suite.maps.core.config.AstianLinks.POI_PACK_MANIFEST to BuildConfig.POI_PACK_MANIFEST_URL,
            org.astian.suite.maps.core.config.AstianLinks.FLOCK_MANIFEST to BuildConfig.FLOCK_MANIFEST_URL,
            org.astian.suite.maps.core.config.AstianLinks.ROAD_FEATURES_MANIFEST to BuildConfig.ROAD_FEATURES_MANIFEST_URL,
            org.astian.suite.maps.core.config.AstianLinks.MAP_FONTS to BuildConfig.MAP_FONTS_URL,
            org.astian.suite.maps.core.config.AstianLinks.COMMUNITY_API to BuildConfig.COMMUNITY_API_URL,
        ).filterValues { it.isNotBlank() }
        // Device memory class first: the Coil cap and the eager-warm decisions read it.
        org.astian.suite.maps.ui.MemoryPressure.init(this)
        org.astian.suite.maps.ui.SpeechPreload.init(this) // after MemoryPressure: its default reads the RAM tier
        org.astian.suite.maps.ui.FullPlaceLoad.init(this)
        // Google-host requests over Chrome's network stack (Cronet), built lazily on the first one.
        // Calibration `useCronet` 0, or an engine that fails to build, leaves them on OkHttp.
        // Before Cronet opens its cache: a due rotation deletes it (Settings > Privacy).
        org.astian.suite.maps.web.SessionRotation.init(this)
        org.astian.suite.maps.web.GoogleStanding.init(this)
        org.astian.suite.maps.web.GoogleTelemetry.init(this)
        org.astian.suite.maps.diag.GoogleUsageStore.init(this) // Settings > Privacy > Requests to Google
        // adb-only: `setprop debug.astian.tune.feedDump 1` saves raw review-feed replies to
        // Android/data/org.astian.suite.maps/files/feeddump/ (ReviewFeedDebug). Never on otherwise.
        if (org.astian.suite.maps.ui.AppTune.localOn("feedDump")) {
            val dir = getExternalFilesDir("feeddump")
            org.astian.suite.maps.core.data.google.ReviewFeedDebug.sink = { raw ->
                dir?.let { java.io.File(it, "qv9Egd-${System.currentTimeMillis()}.txt").writeText(raw) }
            }
        }
        // adb-only: `setprop debug.astian.tune.netLog 1` also opens the WebViews to Chrome's remote
        // inspector (adb forward to webview_devtools_remote_<pid>), for reading their real headers.
        if (org.astian.suite.maps.ui.AppTune.localOn("netLog")) android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        // A test switch for the routes' fallbacks (ValhallaRouter.matchOff). adb only.
        org.astian.suite.maps.core.data.ValhallaRouter.matchOff = org.astian.suite.maps.ui.AppTune.localOn("noMatch")
        org.astian.suite.maps.web.SessionRotation.appJar = http.cookieJar as? org.astian.suite.maps.core.di.ResettableCookieJar
        org.astian.suite.maps.net.CronetHolder.init(this)
        org.astian.suite.maps.core.net.GoogleTransport.interceptor = org.astian.suite.maps.net.CronetTransport(http.cookieJar, org.astian.suite.maps.web.WebViewCookieJar())
        // Push the device class down to :core, which cannot read an :app holder (same seam as
        // CategoryFilter.enabled). Gates the ambient POI fan-out in GoogleMapsDataSource.
        org.astian.suite.maps.core.data.LowRamMode.enabled = org.astian.suite.maps.ui.MemoryPressure.lowRam
        org.astian.suite.maps.offline.StorageLocation.init(this)
        org.astian.suite.maps.offline.RoadNameTileSource.install(this) // street names for LineNamer (issue #478)
        Units.init(this)
        // The desktop window size Google's requests describe: picked once per install, then kept
        // (a size that changed per launch would be its own oddity). See BrowserViewport.
        run {
            val p = getSharedPreferences("astian_settings", MODE_PRIVATE)
            val idx = p.getInt("browser_viewport", -1).takeIf { it >= 0 }
                ?: kotlin.random.Random.nextInt(org.astian.suite.maps.core.data.google.BrowserViewport.CHOICES.size).also { p.edit().putInt("browser_viewport", it).apply() }
            val (w, h) = org.astian.suite.maps.core.data.google.BrowserViewport.choice(idx)
            org.astian.suite.maps.core.data.google.BrowserViewport.set(w, h)
        }
        org.astian.suite.maps.ui.Clock24.refresh(this) // the 12/24-hour clock setting (issue #357); MainActivity refreshes it on resume
        org.astian.suite.maps.core.data.transit.Transitous.durationFormatter = { secs -> org.astian.suite.maps.ui.formatDuration(secs.toDouble()) }
        org.astian.suite.maps.core.i18n.DelayText.formatter = { min ->
            if (min > 0) getString(R.string.transit_min_late, min) else getString(R.string.transit_min_early, -min)
        }
        AppTheme.init(this)
        DynamicColor.init(this)
        AppLocale.init(this) // resolve the app language (system default) → drives the nav-text locale
        Traffic.init(this)
        TransitLayer.init(this)
        org.astian.suite.maps.ui.SatelliteLayer.init(this) // persisted satellite-imagery toggle
        org.astian.suite.maps.ui.LayersButton.init(this) // persisted show/hide of the map layers button
        org.astian.suite.maps.ui.MapTilt.init(this) // two-finger tilt on/off (issue #627)
        org.astian.suite.maps.ui.NorthLock.init(this) // Settings > Map "Keep north up"
        org.astian.suite.maps.ui.ParkingButton.init(this) // parking button shown with no spot saved (issue #626)
        org.astian.suite.maps.ui.LinkAction.init(this) // what a location link from another app does (discussion #640)
        org.astian.suite.maps.ui.StartView.init(this) // where the map opens, and the view picked for it
        org.astian.suite.maps.ui.NavEndConfirm.init(this) // ask before ending a drive, off by default (issue #624)
        org.astian.suite.maps.ui.SpeedDisplay.init(this) // speed + limit badge on/off, phone and car (issue #625)
        org.astian.suite.maps.ui.Topography.init(this)
        org.astian.suite.maps.ui.Flock.init(this) // load the persisted surveillance-camera toggle (else it read false every launch)
        org.astian.suite.maps.ui.SpeedCams.init(this) // same init-or-it-reads-false trap as Flock
        org.astian.suite.maps.ui.SpeedCamWarn.init(this) // spoken camera warning (issue #229), off by default
        org.astian.suite.maps.ui.SpeedingAlert.init(this) // spoken over-the-limit alert (issue #404), off by default
        org.astian.suite.maps.ui.BridgeAlert.init(this) // bridge openings on the route in the Netherlands, on by default
        org.astian.suite.maps.ui.BikeSafe.init(this) // bike routes prefer lanes and quiet streets (issue #401), on by default
        // The chooser's sticky avoid toggles, for the nav session's own fetches from the first
        // drive on (a resumed drive or an Android Auto start never opens the phone's chooser).
        getSharedPreferences("astian_settings", MODE_PRIVATE).let { p ->
            org.astian.suite.maps.core.data.RoutingPrefs.avoidTolls = p.getBoolean("avoid_tolls", false)
            org.astian.suite.maps.core.data.RoutingPrefs.avoidHighways = p.getBoolean("avoid_highways", false)
            org.astian.suite.maps.core.data.RoutingPrefs.avoidFerries = p.getBoolean("avoid_ferries", false)
        }
        org.astian.suite.maps.ui.FlockRouteAlert.init(this) // load the persisted "warn about cameras on route" toggle
        org.astian.suite.maps.ui.FlockDetour.init(this) // side-street detours around cameras (issue #600), nested under it
        org.astian.suite.maps.ui.FlockNavAlert.init(this) // plate-camera card + spoken alert while navigating, off by default
        // Parse the bundled on-device ALPR/Flock camera dataset off the main thread (map layer draws
        // instantly, route counts are reliable), then refresh from the hosted manifest so the data updates
        // without an app release (weekly CI cron re-hosts a newer version; a bump swaps it in on next launch).
        CoroutineScope(Dispatchers.IO).launch {
            // The catalog regions' real boundaries (issue #599): until this lands, region picks
            // fall back to their boxes, which is what they always were.
            org.astian.suite.maps.offline.RegionPolys.ensureLoaded(this@AstianApp)
            org.astian.suite.maps.data.FlockCameras.ensureLoaded(this@AstianApp)
            org.astian.suite.maps.data.FlockCameras.refresh(this@AstianApp, org.astian.suite.maps.core.config.AstianLinks.flockManifest())
        }
        org.astian.suite.maps.ui.SimLocation.onChange = {
            locationProvider.pinned = it
            android.util.Log.i("AstianSim", "simulated location " + if (it != null) "on" else "off")
        }
        org.astian.suite.maps.ui.SimLocation.init(this)
        org.astian.suite.maps.ui.UiScale.init(this)
        org.astian.suite.maps.ui.PageTransitions.init(this)
        org.astian.suite.maps.ui.AppFont.init(this) // user-supplied UI font (issue #252)
        org.astian.suite.maps.ui.MapColors.init(this)
        org.astian.suite.maps.ui.MapButtonShape.init(this) // round or square map buttons (issue #797)
        org.astian.suite.maps.ui.TurnArrows.init(this) // arrows on the route at the turns ahead (issue #804)
        org.astian.suite.maps.ui.CarSpeed.init(this) // the car's speed badge, off by default
        org.astian.suite.maps.ui.LiveReviews.init(this)
        org.astian.suite.maps.ui.ShowReviews.init(this)
        org.astian.suite.maps.ui.LoadPhotos.init(this)
        org.astian.suite.maps.ui.OfflinePlaces.init(this)
        org.astian.suite.maps.ui.ReviewsOnTap.init(this)
        org.astian.suite.maps.ui.PhotosOnTap.init(this)
        org.astian.suite.maps.ui.DetailsRetry.init(this)
        org.astian.suite.maps.ui.OtherLocationsAuto.init(this)
        org.astian.suite.maps.ui.RouteTrafficOnTap.init(this)
        org.astian.suite.maps.core.data.RoutingPrefs.googleTraffic = !org.astian.suite.maps.ui.RouteTrafficOnTap.on.value
        org.astian.suite.maps.ui.HideAdult.init(this)
        org.astian.suite.maps.ui.HideExternalLinks.init(this)
        org.astian.suite.maps.ui.GoogleFree.init(this) // "Use Astian Maps without Google": mirrors into the :core NoGoogle flag
        org.astian.suite.maps.ui.Buildings3d.init(this)
        org.astian.suite.maps.ui.RouteTrail.init(this)
        org.astian.suite.maps.ui.TurnDeclutterPref.init(this)
        org.astian.suite.maps.ui.CategoryChipsPref.init(this)
        org.astian.suite.maps.ui.QuickChipsPref.init(this) // which quick-category chips show, and their order (issue #779)
        org.astian.suite.maps.ui.RoadLabel.init(this)
        org.astian.suite.maps.ui.NavNorthUp.init(this)
        org.astian.suite.maps.ui.ZoomToTurn.init(this) // zoom out to the next turn while driving, off by default (issue #781)
        org.astian.suite.maps.ui.PuckStyle.init(this)
        org.astian.suite.maps.ui.HouseNumbers.init(this) // house-number zoom gate (issue #329)
        org.astian.suite.maps.ui.MapNames.init(this) // which name a map label shows (issue #738)
        org.astian.suite.maps.ui.PreferButtons.init(this)
        org.astian.suite.maps.ui.ZoomKeys.init(this)
        org.astian.suite.maps.ui.PipTurnCard.init(this)
        org.astian.suite.maps.ui.PauseInBar.init(this)
        org.astian.suite.maps.ui.FasterRouteAuto.init(this)
        org.astian.suite.maps.ui.RegionUpdates.init(this)
        org.astian.suite.maps.ui.BuildingOverlay.init(this)
        org.astian.suite.maps.ui.BuildingDebug.init(this)
        org.astian.suite.maps.ui.MapPoiPrefs.init(this)
        // Mirrors into the :core flag NavEngine reads (issue #596).
        org.astian.suite.maps.ui.SpokenRoadNames.init(this)
        org.astian.suite.maps.ui.RoadNumbers.init(this) // numbered roads go by their number (issue #836), off by default
        org.astian.suite.maps.ui.SpokenDetail.init(this)
        org.astian.suite.maps.ui.RoutePicker.init(this)
        org.astian.suite.maps.ui.VoiceSearch.init(this)
        org.astian.suite.maps.ui.ContactsSearch.init(this) // contacts-in-search toggle (issue #243)
        org.astian.suite.maps.diag.NavTrace.init(this) // opt-in nav smoothness trace (issue #251)
        org.astian.suite.maps.ui.MapLook.init(this) // which OpenFreeMap look the basemap loads (issue #750); before MapFonts
        org.astian.suite.maps.ui.map.MapFonts.init(this) // Roboto basemap glyphs (cached patched style + async refresh)
        Onboarding.init(this)
        org.astian.suite.maps.ui.community.CommunityReports.init(this)
        org.astian.suite.maps.ui.WhatsNew.init(this)
        // Persist any fatal crash (stack trace + breadcrumbs) so it survives the
        // restart and can be exported from Settings → Diagnostics next launch.
        CrashCatcher.install(this) { diag.snapshot() }
    }
}
