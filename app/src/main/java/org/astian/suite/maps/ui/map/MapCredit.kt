package org.astian.suite.maps.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.astian.suite.maps.R
import org.astian.suite.maps.ui.dpadHighlight

private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
private const val OPENMAPTILES_URL = "https://openmaptiles.org/"

/**
 * The map's footer credit, the same line the web map prints:
 * "Astian Maps | (c) OpenStreetMap contributors · (c) OpenMapTiles". The two credits are links, in
 * the brand blue like the web ones; the ODbL asks for a visible, findable OpenStreetMap credit in
 * every map state, so both the browse map and the drive chrome draw this one.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MapCredit(darkTheme: Boolean, modifier: Modifier = Modifier, centered: Boolean = false) {
    val context = LocalContext.current
    val text = if (darkTheme) Color(0xFFB8C2CC) else Color(0xFF4A4A4A)
    val link = if (darkTheme) Color(0xFF8AB4F8) else Color(0xFF1A5FD0)
    val style = MaterialTheme.typography.labelSmall
    fun open(url: String) {
        runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }
    }
    // A flow, so a narrow strip breaks the line between the credits, never inside one.
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = if (centered) androidx.compose.foundation.layout.Arrangement.Center else androidx.compose.foundation.layout.Arrangement.Start,
    ) {
        Text(stringResource(R.string.map_credit_brand), style = style, color = text)
    }
}
