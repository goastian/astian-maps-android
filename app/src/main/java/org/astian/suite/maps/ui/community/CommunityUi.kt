package org.astian.suite.maps.ui.community

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.astian.suite.maps.R
import org.astian.suite.maps.core.community.Incident
import org.astian.suite.maps.core.community.IncidentBox
import org.astian.suite.maps.core.community.IncidentType
import org.astian.suite.maps.core.model.LatLng
import org.astian.suite.maps.core.model.distanceTo
import org.astian.suite.maps.ui.AstianDialog
import org.astian.suite.maps.ui.dpadClickable
import org.astian.suite.maps.ui.dpadHighlight
import org.astian.suite.maps.ui.formatDistance
import org.astian.suite.maps.ui.icons.Sym
import org.astian.suite.maps.ui.map.BottomPanel
import org.astian.suite.maps.ui.map.MapUiState
import org.astian.suite.maps.ui.map.MapViewModel
import org.astian.suite.maps.ui.map.NavChromeEdges
import org.astian.suite.maps.ui.nav.NavFab
import org.astian.suite.maps.ui.rememberDpadAutoFocus
import org.astian.suite.maps.ui.theme.isAppInDarkTheme

/**
 * Everything community reports put over the map, composed once from `MapFloaters`: the fetch and
 * the live stream for the area in view, the consent note, the type picker, a report's card, the
 * Undo after sending, and the heads-up and "Still there?" question while driving.
 */
@Composable
internal fun BoxScope.CommunityHost(state: MapUiState, vm: MapViewModel) {
    if (!CommunityReports.enabled.value) return
    if (org.astian.suite.maps.ui.PipMode.active.value) return
    ReportsFeed(state, vm)
    AheadFeed(state, vm)
    val context = LocalContext.current
    if (CommunityReports.consentAsk.value) {
        AstianDialog(
            onDismissRequest = { CommunityReports.consentAsk.value = false },
            title = stringResource(R.string.community_consent_title),
            confirmText = stringResource(R.string.community_consent_ok),
            onConfirm = { CommunityReports.consent(context) },
            dismissText = stringResource(android.R.string.cancel),
            onDismiss = { CommunityReports.consentAsk.value = false },
            dismissLowEmphasis = true,
            text = { Text(stringResource(R.string.community_consent_body)) },
        )
    }
    if (CommunityReports.pickerOpen.value) ReportPicker(state, vm)
    CommunityReports.selected.value?.let { IncidentCard(it, state, vm) }
    TopSlot(state, vm)
}

/** The area's reports while the app is in front: a list on arrival and after a move, and the
 *  stream for changes in between, reopened when the view leaves the area it covers. Reports
 *  that run out leave on their own, with or without the network. */
@Composable
private fun ReportsFeed(state: MapUiState, vm: MapViewModel) {
    var center by remember { mutableStateOf<LatLng?>(null) }
    val live by androidx.compose.runtime.rememberUpdatedState(state)
    LaunchedEffect(Unit) {
        while (true) {
            org.astian.suite.maps.ui.AppVisibility.foreground.first { it }
            CommunityReports.pruneExpired()
            val s = live
            val here = if (s.navigating || org.astian.suite.maps.ui.DrivingMode.on.value) s.myLocation ?: vm.viewCenter() else vm.viewCenter()
            if (here != null) {
                center = here
                CommunityReports.refresh(vm, here)
            }
            delay(5_000)
        }
    }
    var followBox by remember { mutableStateOf<IncidentBox?>(null) }
    val now = center
    if (now != null) {
        val inner = IncidentBox.around(now.lat, now.lng, CommunityReports.FETCH_HALF_DEG)
        if (followBox?.contains(inner) != true) followBox = IncidentBox.around(now.lat, now.lng, CommunityReports.FETCH_HALF_DEG * 2)
    }
    val box = followBox ?: return
    val foreground by org.astian.suite.maps.ui.AppVisibility.foreground.collectAsState()
    if (!foreground) return
    LaunchedEffect(box) {
        while (true) {
            runCatching {
                vm.incidents.follow(box).collect { change ->
                    if (change.removed) CommunityReports.remove(change.incident.id) else CommunityReports.upsert(change.incident)
                }
            }
            delay(5_000)
        }
    }
}

/** Feeds the car's position to [AheadWatch] on every fix and once a second, and speaks a new
 *  heads-up once. Composed whatever the top slot shows, so the watch never loses its place. */
@Composable
private fun AheadFeed(state: MapUiState, vm: MapViewModel) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick++
        }
    }
    val items = CommunityReports.items.value
    val talking = state.navigating || org.astian.suite.maps.ui.DrivingMode.on.value
    val driving = talking || (state.mySpeed ?: 0f) > 3f
    LaunchedEffect(state.myLocation, state.myBearing, state.mySpeed, items, tick, driving) {
        CommunityReports.noteCourse(state.myBearing, state.mySpeed)
        val alert = if (!driving) null else CommunityReports.watch.update(
            items, state.myLocation, state.myBearing?.toDouble(), state.mySpeed, CommunityReports.mine,
        )
        CommunityReports.alert.value = alert
        if (alert is AheadWatch.Heads && talking && CommunityReports.watch.firstAnnouncement(alert.incident.id)) {
            val label = context.getString(CommunityReports.typeLabel(alert.incident.type))
            val shown = formatDistance(kotlin.math.round(alert.distanceM / 50.0) * 50.0)
            vm.speakIncident(context.getString(R.string.community_ahead_spoken, label, shown))
        }
    }
}

/** The report button: the drive's own round button, with the report glyph in a warm color so
 *  it reads apart from locate and voice. */
@Composable
internal fun ReportFab(modifier: Modifier = Modifier) {
    if (!CommunityReports.enabled.value) return
    val label = stringResource(R.string.community_report)
    NavFab(
        onClick = { CommunityReports.openPicker() },
        modifier = modifier.dpadHighlight(CircleShape).semantics { contentDescription = label },
    ) {
        Icon(
            painterResource(R.drawable.ic_incident_report),
            contentDescription = null,
            tint = REPORT_ACCENT,
            modifier = Modifier.size(26.dp),
        )
    }
}

private val REPORT_ACCENT = Color(0xFFE8590C)

/** A type's badge: a flat circle in its color with the glyph, the same as its pin on the map. */
@Composable
internal fun IncidentBadge(type: IncidentType, subtype: String?, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(CommunityReports.color(type, subtype)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(CommunityReports.glyph(type)),
            contentDescription = null,
            tint = CommunityReports.glyphInk(type),
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/**
 * The picker. The spot and direction are taken when it opens, so a report made while moving
 * lands where the driver saw the thing; stopped in a jam, the course held before stopping is
 * sent. One tap on a type sends it; a type with details shows them for a few seconds and sends
 * the plain report when the ring runs out (two taps at most, docs/06-trafico-comunitario.md).
 */
@Composable
private fun ReportPicker(state: MapUiState, vm: MapViewModel) {
    val context = LocalContext.current
    val at = remember { state.myLocation }
    val heading = remember { CommunityReports.reportHeading(state.myBearing, state.mySpeed) }
    var chosen by remember { mutableStateOf<IncidentType?>(null) }
    val first = rememberDpadAutoFocus(chosen)
    fun send(type: IncidentType, subtype: String?) {
        val spot = at ?: return
        CommunityReports.report(vm, context, type, subtype, spot, heading)
    }
    BottomPanel(onDismiss = { CommunityReports.pickerOpen.value = false }) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp)) {
            val type = chosen
            Text(
                if (type == null) stringResource(R.string.community_what) else stringResource(CommunityReports.typeLabel(type)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(if (at == null) R.string.community_no_fix else R.string.community_where_now),
                style = MaterialTheme.typography.bodyMedium,
                color = if (at == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            if (type == null) {
                IncidentType.entries.chunked(3).forEachIndexed { row, types ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        types.forEachIndexed { i, t ->
                            val shape = RoundedCornerShape(12.dp)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .then(if (row == 0 && i == 0) Modifier.focusRequester(first) else Modifier)
                                    .clip(shape)
                                    .dpadHighlight(shape)
                                    .dpadClickable(enabled = at != null) {
                                        if (t.subtypes.isEmpty()) send(t, null) else chosen = t
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                IncidentBadge(t, null, 56.dp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(CommunityReports.typeLabel(t)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            } else {
                DetailStep(type, first, onPick = { send(type, it) }, onBack = { chosen = null })
            }
        }
    }
}

/** The details of [type] as plain rows, and Send with a ring that sends the plain report when
 *  it closes. Any tap on a detail sends at once. A jam's levels carry their road color. */
@Composable
private fun DetailStep(type: IncidentType, first: FocusRequester, onPick: (String?) -> Unit, onBack: () -> Unit) {
    // Wall-clock time, not an animation: the system's animation scale (0.5x on many phones, off
    // under accessibility settings) would shorten the time to pick a detail, down to none at all.
    var spent by remember(type) { mutableFloatStateOf(0f) }
    LaunchedEffect(type) {
        val started = android.os.SystemClock.uptimeMillis()
        while (true) {
            val elapsed = android.os.SystemClock.uptimeMillis() - started
            spent = (elapsed.toFloat() / AUTO_SEND_MS).coerceIn(0f, 1f)
            if (elapsed >= AUTO_SEND_MS) break
            delay(50)
        }
        onPick(null)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
        IncidentBadge(type, null, 40.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.community_detail_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
    type.subtypes.forEachIndexed { i, sub ->
        val shape = RoundedCornerShape(12.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .dpadHighlight(shape)
                .dpadClickable { onPick(sub) }
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (type == IncidentType.CONGESTION) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(CommunityReports.jamColor(sub)))
                Spacer(Modifier.width(12.dp))
            }
            Text(
                CommunityReports.subtypeLabel(sub)?.let { stringResource(it) } ?: sub,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, modifier = Modifier.dpadHighlight(CircleShape)) {
            Text(stringResource(R.string.community_back))
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onPick(null) },
            contentPadding = PaddingValues(start = 12.dp, end = 20.dp),
            modifier = Modifier.height(48.dp).dpadHighlight(CircleShape),
        ) {
            val ink = MaterialTheme.colorScheme.onPrimary
            Canvas(Modifier.size(22.dp)) {
                val stroke = 2.5.dp.toPx()
                drawCircle(ink.copy(alpha = 0.3f), style = Stroke(stroke))
                drawArc(
                    ink, -90f, 360f * (1f - spent), false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.community_send), style = MaterialTheme.typography.titleSmall)
        }
    }
}

private const val AUTO_SEND_MS = 7_000

/** Answers are only offered this close to a report: a vote is someone who saw the spot. */
private const val VOTE_NEAR_M = 1_500.0

/** A report's card: what, where, when, how sure, and the two answers when the driver is near. */
@Composable
private fun IncidentCard(incident: Incident, state: MapUiState, vm: MapViewModel) {
    val context = LocalContext.current
    val first = rememberDpadAutoFocus(incident.id)
    val me = state.myLocation
    val near = me != null && me.distanceTo(LatLng(incident.lat, incident.lng)) <= VOTE_NEAR_M &&
        incident.id !in CommunityReports.mine
    BottomPanel(onDismiss = { CommunityReports.selected.value = null }) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IncidentBadge(incident.type, incident.subtype, 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title(incident), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    incident.roadName?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(details(incident, me), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (near) {
                AnswerRow(incident, first) { present ->
                    CommunityReports.vote(vm, context, incident, present)
                    vm.flashStatus(context.getString(R.string.community_thanks))
                }
            } else {
                Text(
                    stringResource(R.string.community_vote_near),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Not there" and "Still there" as the theme's own outlined and filled buttons, wide and tall
 *  enough to hit at a glance. A jam asks whether traffic is still slow. */
@Composable
private fun AnswerRow(incident: Incident, first: FocusRequester? = null, onAnswer: (Boolean) -> Unit) {
    val jam = incident.type == IncidentType.CONGESTION
    val shape = RoundedCornerShape(14.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { onAnswer(false) },
            shape = shape,
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.weight(1f).height(52.dp).dpadHighlight(shape),
        ) {
            Icon(Sym.Close, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (jam) R.string.community_jam_no else R.string.community_gone), maxLines = 1)
        }
        Button(
            onClick = { onAnswer(true) },
            shape = shape,
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .then(if (first != null) Modifier.focusRequester(first) else Modifier)
                .dpadHighlight(shape),
        ) {
            Icon(Sym.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (jam) R.string.community_jam_yes else R.string.community_still_there), maxLines = 1)
        }
    }
}

@Composable
private fun title(incident: Incident): String {
    val type = stringResource(CommunityReports.typeLabel(incident.type))
    val sub = incident.subtype?.let { CommunityReports.subtypeLabel(it) }?.let { stringResource(it) }
    return if (sub != null) "$type · $sub" else type
}

@Composable
private fun ago(atMs: Long): String {
    val minutes = ((System.currentTimeMillis() - atMs) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.community_ago_now)
        minutes < 60 -> stringResource(R.string.community_ago_min, minutes.toInt())
        else -> stringResource(R.string.community_ago_hours, (minutes / 60).toInt())
    }
}

@Composable
private fun details(incident: Incident, me: LatLng?): String {
    val confirmed = incident.lastConfirmedAtMs?.let {
        stringResource(R.string.community_confirmed_ago, ago(it).replaceFirstChar { c -> c.lowercase() })
    } ?: if (incident.confirmations == 0) stringResource(R.string.community_unconfirmed)
    else pluralStringResource(R.plurals.community_confirmations, incident.confirmations, incident.confirmations)
    val away = me?.let { formatDistance(it.distanceTo(LatLng(incident.lat, incident.lng))) }
    return listOfNotNull(ago(incident.createdAtMs), confirmed, away).joinToString(" · ")
}

/**
 * The slot under the search bar, or under the turn card in a drive: the Undo after a report, else
 * the report ahead while driving, which turns into "Still there?" 200 m before it.
 */
@Composable
private fun BoxScope.TopSlot(state: MapUiState, vm: MapViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val top = if (state.navigating && NavChromeEdges.bannerBottomPx > 0) {
        with(density) { NavChromeEdges.bannerBottomPx.toDp() } + 8.dp
    } else null
    val modifier = Modifier
        .align(Alignment.TopCenter)
        .then(if (top != null) Modifier.offset { IntOffset(0, top.roundToPx()) } else Modifier.statusBarsPadding().padding(top = 72.dp))
        .padding(horizontal = 12.dp)
        .widthIn(max = 480.dp)
        .fillMaxWidth()

    val sent = CommunityReports.lastSent.value
    if (sent != null && sent.withdrawToken != null) {
        LaunchedEffect(sent) {
            delay((UNDO_MS - (System.currentTimeMillis() - sent.atMs)).coerceAtLeast(0))
            if (CommunityReports.lastSent.value === sent) CommunityReports.lastSent.value = null
        }
        SlotCard(modifier) {
            Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IncidentBadge(sent.incident.type, sent.incident.subtype, 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.community_reported, stringResource(CommunityReports.typeLabel(sent.incident.type))),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { CommunityReports.undo(vm, context) }, modifier = Modifier.dpadHighlight(CircleShape)) {
                    Text(stringResource(R.string.community_undo), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        return
    }
    when (val alert = CommunityReports.alert.value) {
        is AheadWatch.Heads -> HeadsCard(alert, modifier)
        is AheadWatch.Ask -> AskCard(alert, vm, modifier)
        null -> Unit
    }
}

private const val UNDO_MS = 8_000L

@Composable
private fun SlotCard(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isAppInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) { content() }
}

/** A report on the way, 700 to 200 m out: what, how far, and on which road. */
@Composable
private fun HeadsCard(alert: AheadWatch.Heads, modifier: Modifier) {
    val incident = alert.incident
    val label = stringResource(CommunityReports.typeLabel(incident.type))
    val shown = formatDistance(kotlin.math.round(alert.distanceM / 50.0) * 50.0)
    SlotCard(modifier) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IncidentBadge(incident.type, incident.subtype, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.community_ahead, label, shown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(incident.roadName, details(incident, null)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** From 200 m before a report until a few seconds after passing it: is it still there? The bar
 *  along the bottom runs down with the time left to answer. */
@Composable
private fun AskCard(alert: AheadWatch.Ask, vm: MapViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val incident = alert.incident
    val label = stringResource(CommunityReports.typeLabel(incident.type))
    val shown = formatDistance(kotlin.math.round(alert.distanceM / 10.0) * 10.0)
    val jam = incident.type == IncidentType.CONGESTION
    SlotCard(modifier) {
        Column {
            Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IncidentBadge(incident.type, incident.subtype, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (alert.passed) label else stringResource(R.string.community_ahead, label, shown),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(if (jam) R.string.community_jam_still_q else R.string.community_still_there_q),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                AnswerRow(incident) { present ->
                    CommunityReports.vote(vm, context, incident, present)
                    vm.flashStatus(context.getString(R.string.community_thanks))
                }
            }
            TimeLeft(alert.shareLeft)
        }
    }
}

@Composable
private fun TimeLeft(share: Float) {
    Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}
