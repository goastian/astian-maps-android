package org.astian.suite.maps.core.data.tiles

/**
 * The basemap looks. [ASTIAN] is the Astian Maps map: the very style the website serves
 * (`packages/map-style`, light and dark), bundled in the app and drawn on the keyless OpenMapTiles
 * tiles. The rest are OpenFreeMap's style files on the same tiles, glyph server and sprite.
 * [LIBERTY] is the earlier Android look: the app paints its own palette, road widths, icons and
 * extra fills over Liberty at runtime (`AstianMapView.applyMapTheme`, `ensureLayers`). The other
 * looks load as OpenFreeMap serves them; only the passes that do not depend on a look (label
 * language, house numbers, Astian Maps' own layers) run on them.
 *
 * Styles are plain URLs, so a look can change over the air without an app release.
 */
enum class MapStyle(
    val key: String,
    val label: String,
    val uri: String,
    /** Astian Maps paints this look (palettes, road widths, icons, twin fills); the others keep their own. */
    val painted: Boolean,
    /** A dark look as served: Astian Maps' layers over it take the dark colors whatever the app theme. */
    val darkLook: Boolean,
    /** The dark variant of a look that ships both and follows the map's light/dark theme; null for a
     *  look that is one fixed style. */
    val darkUri: String? = null,
) {
    ASTIAN(
        "astian", "Astian Maps", "asset://styles/astian-light.json",
        painted = false, darkLook = false, darkUri = "asset://styles/astian-dark.json",
    ),
    LIBERTY("liberty", "OpenFreeMap Liberty", "https://tiles.openfreemap.org/styles/liberty", painted = true, darkLook = false),
    BRIGHT("bright", "OpenFreeMap Bright", "https://tiles.openfreemap.org/styles/bright", painted = false, darkLook = false),
    POSITRON("positron", "OpenFreeMap Positron", "https://tiles.openfreemap.org/styles/positron", painted = false, darkLook = false),
    FIORD("fiord", "OpenFreeMap Fiord", "https://tiles.openfreemap.org/styles/fiord", painted = false, darkLook = true),
    DARK("dark", "OpenFreeMap Dark", "https://tiles.openfreemap.org/styles/dark", painted = false, darkLook = true);

    /** The style JSON ships inside the app instead of being fetched. */
    val bundled: Boolean get() = uri.startsWith("asset://")

    /** True for a look that draws its own light and dark variants by the map's theme. */
    val themed: Boolean get() = darkUri != null

    /** The style URI to load for the map's current light/dark state. */
    fun uriFor(dark: Boolean): String = if (dark && darkUri != null) darkUri else uri

    companion object {
        val DEFAULT = ASTIAN

        fun fromKey(key: String?): MapStyle? = entries.firstOrNull { it.key == key }

        /** The look whose live style URL this is, or null for any other URI. */
        fun byUri(uri: String): MapStyle? = entries.firstOrNull { it.uri == uri }
    }
}

/**
 * Google's stable raster XYZ endpoint (mt0..mt3). Included for testing/parity
 * only: using it ships a Google-look map AND puts tile load back on Google,
 * both of which Astian Maps deliberately avoids by using open tiles. lyrs: m=roads,
 * s=satellite, y=hybrid, t=terrain, h=transparent roads overlay.
 */
object GoogleRasterTiles {
    fun tiles(layers: String = "m"): List<String> =
        (0..3).map { "https://mt$it.google.com/vt/lyrs=$layers&x={x}&y={y}&z={z}" }
}
