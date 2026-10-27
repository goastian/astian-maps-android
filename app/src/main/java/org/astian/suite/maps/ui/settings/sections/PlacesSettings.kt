package org.astian.suite.maps.ui.settings.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.astian.suite.maps.R
import org.astian.suite.maps.ui.settings.GroupDivider
import org.astian.suite.maps.ui.settings.SettingsGroup
import org.astian.suite.maps.ui.settings.SettingsScaffold
import org.astian.suite.maps.ui.settings.ToggleRow

/**
 * Places sub-screen (was Place pages; 2026-09-17 settings reshuffle): where the map's places come
 * from, what the map draws for them, and the five place-page content toggles.
 */
@Composable
internal fun PlacesSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsScaffold(stringResource(R.string.settings_places), onBack) { topRow ->
        Spacer(Modifier.height(4.dp))
        PlacesSourceGroup(topRow)
        Spacer(Modifier.height(8.dp))
        PlacesOnMapGroup()
        Spacer(Modifier.height(8.dp))
        SettingsGroup(title = stringResource(R.string.settings_place_pages)) {
        ReviewAndPhotoRows()
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_hide_adult),
            checked = org.astian.suite.maps.ui.HideAdult.on.value,
            onCheckedChange = { org.astian.suite.maps.ui.HideAdult.set(context, it) },
            hint = stringResource(R.string.settings_hide_adult_hint),
        )
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_hide_external_links),
            checked = org.astian.suite.maps.ui.HideExternalLinks.on.value,
            onCheckedChange = { org.astian.suite.maps.ui.HideExternalLinks.set(context, it) },
            hint = stringResource(R.string.settings_hide_external_links_hint),
        )
        }
        Spacer(Modifier.height(24.dp))
    }
}
