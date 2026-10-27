package org.astian.suite.maps.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.astian.suite.maps.R
import org.astian.suite.maps.core.model.ShortcutKind
import org.astian.suite.maps.ui.dpadClickable
import org.astian.suite.maps.ui.dpadHighlight
import org.astian.suite.maps.ui.icons.Sym
import org.astian.suite.maps.ui.icons.SymOutlined
import org.astian.suite.maps.ui.rememberDpadAutoFocus
import org.astian.suite.maps.ui.settings.SettingsSection
import org.astian.suite.maps.ui.SheetPalette
import org.astian.suite.maps.ui.theme.isAppInAmoled
import org.astian.suite.maps.ui.theme.isAppInDarkTheme

/**
 * The MAPS.ME-style bottom bar of the bare portrait map: a chip row (Home, Work, the quick
 * categories) over four tabs, Search, Route, Saved and More. While it shows, the top search bar
 * and its chip row step aside ([SearchGates.browseBar] says when). Holder state lives here so
 * MapScreen needs no new parameter or local for it.
 */
internal object BrowseBar {
    /** Search was tapped: MapScreen shows the top search bar again and [SearchBar] focuses it.
     *  Cleared once the field holds focus. */
    val revealSearch = mutableStateOf(false)

    /** Your lists (the Saved tab), shown by MapScreen's ListsSheet. */
    val listsOpen = mutableStateOf(false)

    val moreOpen = mutableStateOf(false)

    /** How far the bar and its chip row reach above the navigation bar, measured. The bottom
     *  map chrome (locate, parking, scale bar, credit) lifts by this while the bar shows. */
    val lift: MutableState<Dp> = mutableStateOf(TAB_ROW_HEIGHT + 52.dp)

    /** Opens Settings, on [section] when given. Set by AstianRoot, which owns the navigation. */
    var openSettings: (SettingsSection?) -> Unit = {}
}

private val TAB_ROW_HEIGHT = 64.dp

@Composable
internal fun BoxScope.BrowseBottomBar(state: MapUiState, vm: MapViewModel) {
    if (org.astian.suite.maps.ui.PipMode.active.value) return
    val shown = SearchGates.browseBarLive.value
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = shown
    val reveal = BrowseBar.revealSearch.value
    // The field normally takes focus within a frame or two; if it never does, give the bare map
    // its bar back rather than leaving the top search bar up with nothing open.
    LaunchedEffect(reveal) {
        if (reveal) {
            kotlinx.coroutines.delay(1500)
            BrowseBar.revealSearch.value = false
        }
    }
    AnimatedVisibility(
        visibleState = visible,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Column(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            BarChips(
                state, vm,
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { BrowseBar.lift.value = with(density) { it.height.toDp() } + TAB_ROW_HEIGHT }
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
            )
            TabRow(vm)
        }
    }
    if (BrowseBar.moreOpen.value) MoreSheet(state, vm, onDismiss = { BrowseBar.moreOpen.value = false })
    RegionOffer(state, vm)
}

@Composable
private fun BarChips(state: MapUiState, vm: MapViewModel, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        state.home?.let { ShortcutChip(stringResource(R.string.shortcut_home), SymOutlined.Home) { vm.openShortcut(ShortcutKind.HOME) } }
        state.work?.let { ShortcutChip(stringResource(R.string.shortcut_work), SymOutlined.Work) { vm.openShortcut(ShortcutKind.WORK) } }
        CategoryChips(onPick = vm::quickSearch, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ShortcutChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    ElevatedAssistChip(
        onClick = onClick,
        modifier = Modifier.padding(end = 8.dp).dpadHighlight(CircleShape),
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = CircleShape,
        colors = androidx.compose.material3.AssistChipDefaults.elevatedAssistChipColors(
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun TabRow(vm: MapViewModel) {
    val dark = isAppInDarkTheme()
    val amoled = isAppInAmoled()
    Surface(
        color = barColor(dark, amoled),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        shadowElevation = 16.dp,
        border = if (amoled) BorderStroke(1.dp, SheetPalette.BorderAmoled) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(TAB_ROW_HEIGHT).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tab(SymOutlined.Search, stringResource(R.string.bar_search)) { BrowseBar.revealSearch.value = true }
                Tab(SymOutlined.Directions, stringResource(R.string.bar_route)) {
                    vm.beginPickDestination()
                    BrowseBar.revealSearch.value = true
                }
                Tab(SymOutlined.Bookmarks, stringResource(R.string.bar_saved)) { BrowseBar.listsOpen.value = true }
                Tab(SymOutlined.MoreHoriz, stringResource(R.string.bar_more)) { BrowseBar.moreOpen.value = true }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tab(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .weight(1f)
            .padding(horizontal = 4.dp)
            .clip(shape)
            .dpadHighlight(shape)
            .dpadClickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun barColor(dark: Boolean, amoled: Boolean): Color = when {
    amoled -> SheetPalette.Amoled
    dark -> Color(0xFF1E1F20)
    else -> Color.White
}

/**
 * A panel that rises from the bottom over a dimmed map, closed by a tap outside it or Back. A raw
 * Dialog, so a key-first device can place focus in it (docs/dpad.md).
 */
@Composable
internal fun BottomPanel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shown = remember { MutableTransitionState(false) }
        shown.targetState = true
        val dismiss = androidx.compose.runtime.rememberUpdatedState(onDismiss)
        // A tap on the dimmed map closes the panel; Back is the key path (the Dialog's own).
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { dismiss.value() } },
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(visibleState = shown, enter = slideInVertically { it } + fadeIn()) {
                val dark = isAppInDarkTheme()
                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = if (dark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    Column(Modifier.navigationBarsPadding()) {
                        Box(
                            Modifier
                                .padding(top = 10.dp, bottom = 4.dp)
                                .align(Alignment.CenterHorizontally)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                        content()
                    }
                }
            }
        }
    }
}

/** The More tab: what the app is, quick actions, and the way into Settings. */
@Composable
private fun MoreSheet(state: MapUiState, vm: MapViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val first = rememberDpadAutoFocus()
    fun settings(section: SettingsSection?) {
        onDismiss()
        BrowseBar.openSettings(section)
    }
    BottomPanel(onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF233A6B), Color(0xFF111A2E))))
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                org.astian.suite.maps.ui.AstianMark(48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(stringResource(R.string.more_subtitle), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.78f))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                QuickAction(SymOutlined.CloudDownload, stringResource(R.string.more_download_maps), Modifier.weight(1f).focusRequester(first)) {
                    settings(SettingsSection.OFFLINE)
                }
                QuickAction(SymOutlined.DirectionsCar, stringResource(R.string.more_driving_mode), Modifier.weight(1f)) {
                    onDismiss()
                    vm.enterDrivingMode()
                }
                QuickAction(SymOutlined.Share, stringResource(R.string.more_share_location), Modifier.weight(1f)) {
                    shareLocation(context, state.myLocation)
                }
                QuickAction(SymOutlined.Draw, stringResource(R.string.more_measure), Modifier.weight(1f)) {
                    onDismiss()
                    vm.startDrawing()
                }
            }
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (isAppInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    MenuRow(SymOutlined.Settings, stringResource(R.string.settings_title)) { settings(null) }
                    MenuRow(SymOutlined.VolumeUp, stringResource(R.string.settings_voice)) { settings(SettingsSection.VOICE) }
                    MenuRow(SymOutlined.Shield, stringResource(R.string.settings_privacy)) { settings(SettingsSection.PRIVACY) }
                    MenuRow(SymOutlined.Info, stringResource(R.string.settings_about)) { settings(SettingsSection.ABOUT) }
                    MenuRow(SymOutlined.Favorite, stringResource(R.string.settings_support)) {
                        onDismiss()
                        org.astian.suite.maps.ui.Onboarding.openDonate(context)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .dpadHighlight(shape)
            .dpadClickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (isAppInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .dpadHighlight(RoundedCornerShape(16.dp))
            .dpadClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Sym.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun shareLocation(context: android.content.Context, at: org.astian.suite.maps.core.model.LatLng?) {
    if (at == null) {
        android.widget.Toast.makeText(context, context.getString(R.string.more_share_no_fix), android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val lat = String.format(java.util.Locale.ROOT, "%.5f", at.lat)
    val lng = String.format(java.util.Locale.ROOT, "%.5f", at.lng)
    val link = "https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=17/$lat/$lng"
    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_TEXT, context.getString(R.string.more_share_text, link))
    runCatching { context.startActivity(android.content.Intent.createChooser(send, null)) }
}

/**
 * The one-time offer to download the region around Home or the user (MapViewModel.maybeOfferRouting),
 * as MAPS.ME's "Download the map" card: the region, its real size on the button, and Later.
 */
@Composable
private fun RegionOffer(state: MapUiState, vm: MapViewModel) {
    val region = state.routingOffer ?: return
    val pack = state.poiPackRegions.firstOrNull { it.id == region.id }
    val size = org.astian.suite.maps.ui.settings.sections.fmtMb(
        org.astian.suite.maps.ui.settings.sections.regionInstalledMb(region, pack, state.regionExtrasMb[region.id] ?: 0),
    )
    val first = rememberDpadAutoFocus()
    BottomPanel(onDismiss = { vm.answerRoutingOffer(false) }) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 12.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Sym.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.region_offer_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.region_offer_body, region.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Button(
                onClick = { vm.answerRoutingOffer(true) },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp).focusRequester(first).dpadHighlight(CircleShape),
            ) {
                Text(stringResource(R.string.region_offer_download, size), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(
                onClick = { vm.answerRoutingOffer(false) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).dpadHighlight(CircleShape),
            ) { Text(stringResource(R.string.region_offer_later)) }
        }
    }
}
