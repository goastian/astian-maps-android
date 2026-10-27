package org.astian.suite.maps.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import org.astian.suite.maps.core.community.Incident
import org.astian.suite.maps.core.community.IncidentType
import org.astian.suite.maps.ui.community.CommunityReports
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng as MLLatLng

private const val INCIDENT_SRC = "astian-incidents-src"
private const val INCIDENT_MINOR_SRC = "astian-incidents-minor-src"
private const val INCIDENT_LINE_SRC = "astian-incident-lines-src"
private const val INCIDENT_LAYER = "astian-incidents"
private const val INCIDENT_MINOR_LAYER = "astian-incidents-minor"
private const val INCIDENT_CLUSTER_LAYER = "astian-incident-clusters"
private const val INCIDENT_CLUSTER_COUNT_LAYER = "astian-incident-cluster-count"
private const val INCIDENT_LINE_CASING_LAYER = "astian-incident-line-casing"
private const val INCIDENT_LINE_LAYER = "astian-incident-line"
private const val INCIDENT_LINE_STRIPES_LAYER = "astian-incident-line-stripes"

/** Below this zoom nearby pins gather into one counted circle (about Google's z12). */
private const val CLUSTER_MAX_ZOOM = 11
/** Potholes and weather appear once streets are readable; everything else from the city view. */
private const val MINOR_MIN_ZOOM = 13f
private const val LINE_MIN_ZOOM = 9.5f

private val MINOR_TYPES = setOf(IncidentType.POTHOLE, IncidentType.WEATHER)
private val JAM_LEVELS = listOf<String?>(null, "moderate", "heavy", "standstill")

private fun incidentImage(type: IncidentType, subtype: String?): String =
    if (type == IncidentType.CONGESTION) "astian-incident-congestion-${subtype?.takeIf { it in JAM_LEVELS } ?: "any"}"
    else "astian-incident-${type.wire}"

/**
 * Community reports on the map. Each report is a round badge in its type's color on a short
 * point at the exact spot; a jam, a closure or roadworks also paints the stretch of road it
 * covers, the way Waze does: a jam in amber, red or dark red by level, a closure in red and
 * white stripes, works in orange and white. Zoomed out, pins gather into counted circles, and
 * potholes and weather wait for street zoom. Reads [CommunityReports.items]; the layers are
 * redrawn after a style reload.
 */
@Composable
internal fun IncidentMapLayer(style: Style?, dark: Boolean) {
    val context = LocalContext.current
    val items = CommunityReports.items.value
    val on = CommunityReports.enabled.value
    LaunchedEffect(style, items, on, dark) {
        val s = style ?: return@LaunchedEffect
        runCatching { ensureIncidentLayer(s, if (on) items else emptyList(), context, dark) }
            .onFailure { android.util.Log.w("AstianCommunity", "layer: ${it.message}") }
    }
}

private fun ensureIncidentLayer(style: Style, items: List<Incident>, context: Context, dark: Boolean) {
    if (style.getLayer(INCIDENT_LAYER) == null) {
        if (items.isEmpty()) return
        installLayers(style, context)
    }
    style.getLayer(INCIDENT_CLUSTER_LAYER)?.setProperties(
        PropertyFactory.circleColor(if (dark) "#E8EAED" else "#1F2933"),
        PropertyFactory.circleStrokeColor(if (dark) "#1F2933" else "#FFFFFF"),
    )
    style.getLayer(INCIDENT_CLUSTER_COUNT_LAYER)?.setProperties(PropertyFactory.textColor(if (dark) "#1F2933" else "#FFFFFF"))
    val now = System.currentTimeMillis()
    val live = items.filterNot { it.expired(now) }
    val (minor, major) = live.partition { it.type in MINOR_TYPES }
    (style.getSource(INCIDENT_SRC) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(major.map(::pinFeature)))
    (style.getSource(INCIDENT_MINOR_SRC) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(minor.map(::pinFeature)))
    val lines = live.mapNotNull { incident ->
        val kind = lineKind(incident.type) ?: return@mapNotNull null
        val line = incident.line?.takeIf { it.size >= 2 } ?: return@mapNotNull null
        Feature.fromGeometry(LineString.fromLngLats(line.map { Point.fromLngLat(it.second, it.first) })).apply {
            addStringProperty("id", incident.id)
            addStringProperty("kind", kind)
            addStringProperty("color", hex(lineColor(incident)))
        }
    }
    (style.getSource(INCIDENT_LINE_SRC) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(lines))
}

private fun pinFeature(incident: Incident): Feature =
    Feature.fromGeometry(Point.fromLngLat(incident.lng, incident.lat)).apply {
        addStringProperty("id", incident.id)
        addStringProperty("img", incidentImage(incident.type, incident.subtype))
        addNumberProperty("op", 0.65 + 0.35 * incident.confidence.coerceIn(0.0, 1.0))
        // Better confirmed and more serious reports on top.
        addNumberProperty("sort", incident.confirmations * 100.0 + severity(incident.type))
    }

private fun installLayers(style: Style, context: Context) {
    IncidentType.entries.forEach { type ->
        val subtypes = if (type == IncidentType.CONGESTION) JAM_LEVELS else listOf(null)
        subtypes.forEach { sub ->
            val id = incidentImage(type, sub)
            if (style.getImage(id) == null) style.addImage(id, incidentPinBitmap(context, type, sub))
        }
    }
    if (style.getSource(INCIDENT_LINE_SRC) == null) style.addSource(GeoJsonSource(INCIDENT_LINE_SRC))
    if (style.getSource(INCIDENT_SRC) == null) {
        style.addSource(
            GeoJsonSource(
                INCIDENT_SRC,
                GeoJsonOptions().withCluster(true).withClusterMaxZoom(CLUSTER_MAX_ZOOM).withClusterRadius(48),
            ),
        )
    }
    if (style.getSource(INCIDENT_MINOR_SRC) == null) style.addSource(GeoJsonSource(INCIDENT_MINOR_SRC))

    // The stretches sit under the labels and over the road and the route, where the route's own
    // anchor is: the first label after the last bridge layer.
    val lastBridge = style.layers.indexOfLast { it.id.startsWith("bridge_") }
    val label = (if (lastBridge >= 0) style.layers.drop(lastBridge + 1) else style.layers).firstOrNull { it is SymbolLayer }?.id
    fun below(layer: org.maplibre.android.style.layers.Layer) {
        if (label != null) style.addLayerBelow(layer, label) else style.addLayer(layer)
    }
    below(
        LineLayer(INCIDENT_LINE_CASING_LAYER, INCIDENT_LINE_SRC).withProperties(
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineWidth(lineWidth(extra = 3f)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(0.95f),
        ).apply { minZoom = LINE_MIN_ZOOM },
    )
    below(
        LineLayer(INCIDENT_LINE_LAYER, INCIDENT_LINE_SRC).withProperties(
            PropertyFactory.lineColor(Expression.get("color")),
            PropertyFactory.lineWidth(lineWidth()),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ).apply { minZoom = LINE_MIN_ZOOM },
    )
    below(
        LineLayer(INCIDENT_LINE_STRIPES_LAYER, INCIDENT_LINE_SRC).withProperties(
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineWidth(lineWidth()),
            PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineDasharray(arrayOf(1f, 1f)),
        ).apply {
            minZoom = LINE_MIN_ZOOM
            setFilter(Expression.neq(Expression.get("kind"), Expression.literal("jam")))
        },
    )

    // Pins last, so they draw over the route and the place icons; placement ignores them, so
    // they never push a street name or an icon off the map.
    style.addLayer(
        CircleLayer(INCIDENT_CLUSTER_LAYER, INCIDENT_SRC).withProperties(
            PropertyFactory.circleRadius(
                Expression.step(Expression.get("point_count"), Expression.literal(14f), Expression.stop(5, 17f), Expression.stop(15, 21f)),
            ),
            PropertyFactory.circleStrokeWidth(2.5f),
        ).apply { setFilter(Expression.has("point_count")) },
    )
    style.addLayer(
        SymbolLayer(INCIDENT_CLUSTER_COUNT_LAYER, INCIDENT_SRC).withProperties(
            PropertyFactory.textField(Expression.toString(Expression.get("point_count_abbreviated"))),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        ).apply { setFilter(Expression.has("point_count")) },
    )
    style.addLayer(pinLayer(INCIDENT_LAYER, INCIDENT_SRC).apply { setFilter(Expression.not(Expression.has("point_count"))) })
    style.addLayer(pinLayer(INCIDENT_MINOR_LAYER, INCIDENT_MINOR_SRC).apply { minZoom = MINOR_MIN_ZOOM })
}

private fun pinLayer(id: String, source: String) = SymbolLayer(id, source).withProperties(
    PropertyFactory.iconImage(Expression.get("img")),
    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
    PropertyFactory.iconAllowOverlap(true),
    PropertyFactory.iconIgnorePlacement(true),
    PropertyFactory.iconOpacity(Expression.get("op")),
    PropertyFactory.iconSize(
        Expression.interpolate(
            Expression.linear(), Expression.zoom(),
            Expression.stop(9, 0.62), Expression.stop(12, 0.8), Expression.stop(15, 1.0),
        ),
    ),
    PropertyFactory.symbolSortKey(Expression.get("sort")),
)

/** About a road's width at each zoom: thin from the city view, a lane's worth at drive zoom. */
private fun lineWidth(extra: Float = 0f): Expression = Expression.interpolate(
    Expression.exponential(1.5f), Expression.zoom(),
    Expression.stop(10, 3f + extra),
    Expression.stop(12, 4.5f + extra),
    Expression.stop(14, 7f + extra),
    Expression.stop(16, 10f + extra),
    Expression.stop(18, 14f + extra),
)

private fun lineKind(type: IncidentType): String? = when (type) {
    IncidentType.CONGESTION -> "jam"
    IncidentType.CLOSURE -> "closure"
    IncidentType.ROADWORKS -> "works"
    else -> null
}

private fun lineColor(incident: Incident): androidx.compose.ui.graphics.Color = when (incident.type) {
    IncidentType.CONGESTION -> CommunityReports.jamColor(incident.subtype)
    IncidentType.CLOSURE -> androidx.compose.ui.graphics.Color(0xFFE03131)
    else -> androidx.compose.ui.graphics.Color(0xFFF76707)
}

private fun severity(type: IncidentType): Double = when (type) {
    IncidentType.CLOSURE, IncidentType.COLLISION -> 9.0
    IncidentType.HAZARD, IncidentType.CONGESTION -> 7.0
    IncidentType.POLICE, IncidentType.ROADWORKS -> 5.0
    else -> 1.0
}

private fun hex(color: androidx.compose.ui.graphics.Color): String = String.format("#%06X", 0xFFFFFF and color.toArgb())

/**
 * A tap on a pin or a stretch opens its card; on a counted circle it zooms in to split it.
 * True when the tap was one of those.
 */
internal fun incidentTapped(map: MapLibreMap, tapped: MLLatLng, density: Float): Boolean {
    if (!CommunityReports.enabled.value || CommunityReports.items.value.isEmpty()) return false
    val p = map.projection.toScreenLocation(tapped)
    // A symbol is hit where its icon is drawn, so a box around the finger finds the pin.
    val r = 12f * density
    val box = android.graphics.RectF(p.x - r, p.y - r, p.x + r, p.y + r)
    fun first(vararg layers: String) = runCatching { map.queryRenderedFeatures(box, *layers) }.getOrNull()?.firstOrNull()
    first(INCIDENT_CLUSTER_LAYER)?.let {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(tapped, map.cameraPosition.zoom + 2.0), 350)
        return true
    }
    val hit = first(INCIDENT_LAYER, INCIDENT_MINOR_LAYER) ?: first(INCIDENT_LINE_LAYER) ?: return false
    val id = runCatching { hit.getStringProperty("id") }.getOrNull() ?: return false
    val incident = CommunityReports.items.value.firstOrNull { it.id == id } ?: return false
    CommunityReports.selected.value = incident
    return true
}

/** The pin: a flat round badge in the type's color with a white rim, the glyph, and a short
 *  point whose tip is the report's spot. */
internal fun incidentPinBitmap(context: Context, type: IncidentType, subtype: String?): Bitmap {
    val d = context.resources.displayMetrics.density
    val w = (36 * d).toInt()
    val h = (46 * d).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val cx = w / 2f
    val cy = 17 * d
    val outer = 16 * d
    val outline = Path().apply {
        addCircle(cx, cy, outer, Path.Direction.CW)
        moveTo(cx - 7 * d, cy + 13.5f * d)
        lineTo(cx, h - 1f)
        lineTo(cx + 7 * d, cy + 13.5f * d)
        close()
    }
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        setShadowLayer(2.5f * d, 0f, 1f * d, 0x52000000)
    }
    c.drawPath(outline, white)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CommunityReports.color(type, subtype).toArgb() }
    c.drawCircle(cx, cy, outer - 2.5f * d, fill)
    ContextCompat.getDrawable(context, CommunityReports.glyph(type))?.mutate()?.let { glyph ->
        val g = (18 * d).toInt()
        val left = (cx - g / 2f).toInt()
        val top = (cy - g / 2f).toInt()
        glyph.setBounds(left, top, left + g, top + g)
        glyph.setTint(CommunityReports.glyphInk(type).toArgb())
        glyph.draw(c)
    }
    return bmp
}
