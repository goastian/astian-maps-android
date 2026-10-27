package org.astian.suite.maps.ui.settings.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.fillMaxWidth
import org.astian.suite.maps.ui.settings.settingsAnchor
import org.astian.suite.maps.ui.dpadHighlight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.astian.suite.maps.R
import org.astian.suite.maps.ui.settings.PageIntro
import org.astian.suite.maps.ui.settings.SettingsGroup
import org.astian.suite.maps.ui.settings.SettingsScaffold
import org.astian.suite.maps.ui.dpadHighlight // D-pad-only operation (docs/dpad.md)

/** Privacy sub-screen: what the app never does, Clear history, link handling and the privacy policy,
 *  with the data sources and their switches under Advanced. The places source moved to Places and
 *  live rechecks to Navigation (2026-09-17). */
@Composable
internal fun PrivacySettingsScreen(vm: org.astian.suite.maps.ui.map.MapViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsScaffold(stringResource(R.string.settings_privacy), onBack) { topRow ->
        Spacer(Modifier.height(4.dp))
        PrivacyPoints()
        // Clear history (issue #425): one row for what used to be spread over three screens
        // (Clear recents on the search page, Clear all under Parking history, trips one at a
        // time under Diagnostics). Confirmed, since it cannot be undone.
        Spacer(Modifier.height(8.dp))
        var confirmClear by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        SettingsGroup {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().settingsAnchor(stringResource(R.string.settings_clear_history)).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(stringResource(R.string.settings_clear_history), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_clear_history_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            FilledTonalButton(
                modifier = topRow.padding(top = 8.dp).dpadHighlight(androidx.compose.material3.ButtonDefaults.filledTonalShape),
                onClick = { confirmClear = true },
            ) { Text(stringResource(R.string.settings_clear_history_action)) }
        }
        }
        if (confirmClear) {
            org.astian.suite.maps.ui.AstianDialog(
                onDismissRequest = { confirmClear = false },
                title = stringResource(R.string.settings_clear_history_confirm_title),
                confirmText = stringResource(R.string.settings_clear_history_action),
                onConfirm = {
                    vm.clearAllHistory()
                    confirmClear = false
                    android.widget.Toast.makeText(context, context.getString(R.string.settings_clear_history_done), android.widget.Toast.LENGTH_SHORT).show()
                },
                dismissText = stringResource(android.R.string.cancel),
                onDismiss = { confirmClear = false },
            ) {
                Text(stringResource(R.string.settings_clear_history_confirm_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        MapsLinksGroup()
        Spacer(Modifier.height(8.dp))
        SettingsGroup {
        androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        FilledTonalButton(
            modifier = Modifier.dpadHighlight(androidx.compose.material3.ButtonDefaults.filledTonalShape),
            onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(org.astian.suite.maps.core.config.AstianLinks.privacy()),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        ) { Text(stringResource(R.string.settings_privacy_button)) }
        }
        }
        // Advanced: the data sources and their switches, folded away. A settings-search hit on
        // one of its rows opens it.
        Spacer(Modifier.height(8.dp))
        val highlight = org.astian.suite.maps.ui.settings.LocalSettingsHighlight.current
        val advancedRows = listOf(
            R.string.settings_google_free, R.string.settings_google_uses, R.string.settings_google_free_links,
            R.string.settings_google_session, R.string.settings_block_google_telemetry, R.string.settings_google_usage,
        ).map { stringResource(it) }
        var advanced by androidx.compose.runtime.saveable.rememberSaveable {
            androidx.compose.runtime.mutableStateOf(highlight != null && advancedRows.any { it.equals(highlight, ignoreCase = true) })
        }
        org.astian.suite.maps.ui.settings.CollapsibleSectionTitle(
            stringResource(R.string.settings_privacy_advanced),
            expanded = advanced,
            modifier = Modifier.padding(horizontal = 4.dp),
            onToggle = { advanced = !advanced },
        )
        if (advanced) {
            PageIntro(stringResource(R.string.settings_data_privacy_hint))
            // The master switch (2026-09-21): one control instead of the four-toggle recipe the FAQ
            // used to give, and it reaches the surfaces those toggles could not (search, Street View,
            // transit, the satellite fallback, the traffic raster).
            SettingsGroup {
                org.astian.suite.maps.ui.settings.ToggleRow(
                    label = stringResource(R.string.settings_google_free),
                    checked = org.astian.suite.maps.ui.GoogleFree.on.value,
                    onCheckedChange = { org.astian.suite.maps.ui.GoogleFree.set(context, it) },
                    hint = stringResource(R.string.settings_google_free_hint),
                )
                // Only meaningful while the switch is on: whether a shared short link may still ask
                // Google's shortener where it points.
                if (org.astian.suite.maps.ui.GoogleFree.on.value) {
                    org.astian.suite.maps.ui.settings.GroupDivider()
                    org.astian.suite.maps.ui.settings.ToggleRow(
                        label = stringResource(R.string.settings_google_free_links),
                        checked = org.astian.suite.maps.ui.GoogleFree.resolveLinks.value,
                        onCheckedChange = { org.astian.suite.maps.ui.GoogleFree.setResolveLinks(context, it) },
                        hint = stringResource(R.string.settings_google_free_links_hint),
                    )
                }
            }
            // With Google on: everything it is used for, switch by switch. The same rows sit on
            // their own pages; this is the one place that lists them all.
            if (!org.astian.suite.maps.ui.GoogleFree.on.value) {
                Spacer(Modifier.height(8.dp))
                GoogleUsesSection(vm)
            }
            // How long one Google session lives (2026-09-23, web/SessionRotation): a saved cookie is a
            // pseudonymous history, a new one gets Google's limited view. Pointless with Google off.
            if (!org.astian.suite.maps.ui.GoogleFree.on.value) {
                Spacer(Modifier.height(8.dp))
                SettingsGroup {
                    org.astian.suite.maps.ui.settings.SubHead(stringResource(R.string.settings_google_session))
                    org.astian.suite.maps.ui.settings.Hint(stringResource(R.string.settings_google_session_hint))
                    if (org.astian.suite.maps.web.GoogleStanding.limited.value) {
                        org.astian.suite.maps.ui.settings.Hint(stringResource(R.string.settings_google_session_limited))
                    }
                    val rot = org.astian.suite.maps.web.SessionRotation
                    listOf(
                        rot.WEEK to R.string.settings_google_session_week,
                        rot.DAY to R.string.settings_google_session_day,
                        rot.LAUNCH to R.string.settings_google_session_launch,
                    ).forEach { (key, label) ->
                        org.astian.suite.maps.ui.settings.SelectableRow(
                            label = stringResource(label),
                            selected = rot.mode.value == key,
                            onClick = { rot.setMode(context, key) },
                        )
                    }
                    // What "every time" costs, shown once it is picked, so nobody wonders why the most
                    // private option is not the default (user 2026-09-23).
                    if (rot.mode.value == rot.LAUNCH) {
                        org.astian.suite.maps.ui.settings.Hint(stringResource(R.string.settings_google_session_launch_hint))
                    }
                    androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        FilledTonalButton(
                            modifier = Modifier.dpadHighlight(androidx.compose.material3.ButtonDefaults.filledTonalShape),
                            onClick = {
                                rot.startNewNow(context)
                                android.widget.Toast.makeText(context, context.getString(R.string.settings_google_session_done), android.widget.Toast.LENGTH_SHORT).show()
                            },
                        ) { Text(stringResource(R.string.settings_google_session_now)) }
                    }
                    org.astian.suite.maps.ui.settings.ToggleRow(
                        label = stringResource(R.string.settings_block_google_telemetry),
                        checked = org.astian.suite.maps.web.GoogleTelemetry.block.value,
                        onCheckedChange = { org.astian.suite.maps.web.GoogleTelemetry.set(context, it) },
                        hint = stringResource(R.string.settings_block_google_telemetry_hint),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            GoogleUsageGroup()
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** What the app itself never does, as a short checked list at the top of the page. */
@Composable
private fun PrivacyPoints() {
    SettingsGroup {
        org.astian.suite.maps.ui.settings.SubHead(stringResource(R.string.privacy_points_title))
        listOf(
            R.string.privacy_point_account,
            R.string.privacy_point_ads,
            R.string.privacy_point_sdk,
            R.string.privacy_point_local,
        ).forEach { res ->
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(
                    org.astian.suite.maps.ui.icons.Sym.CheckCircle,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color(0xFF2E9E5B),
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** How many requests went to Google, today and over the last week, by purpose (2026-09-25,
 *  core/net/GoogleUsage). Read when the page opens; the numbers are a snapshot, not live. */
@Composable
private fun GoogleUsageGroup() {
    val today = androidx.compose.runtime.remember { org.astian.suite.maps.diag.GoogleUsageStore.today() }
    val week = androidx.compose.runtime.remember { org.astian.suite.maps.diag.GoogleUsageStore.week() }
    val avg = androidx.compose.runtime.remember { org.astian.suite.maps.diag.GoogleUsageStore.weekDailyAverage() }
    SettingsGroup {
        org.astian.suite.maps.ui.settings.SubHead(stringResource(R.string.settings_google_usage))
        org.astian.suite.maps.ui.settings.Hint(stringResource(R.string.settings_google_usage_hint))
        if (week.isEmpty()) {
            org.astian.suite.maps.ui.settings.Hint(stringResource(R.string.settings_google_usage_none))
        } else {
            Text(
                stringResource(R.string.settings_google_usage_summary, today.sumOf { it.second }, avg),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            val todayMap = today.toMap()
            week.forEach { (kind, n) ->
                Text(
                    stringResource(R.string.settings_google_usage_row, googleUsageLabel(kind), todayMap[kind] ?: 0, n),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun googleUsageLabel(kind: String): String = when {
    kind == "search" -> stringResource(R.string.google_usage_kind_search)
    kind == "nearby places" -> stringResource(R.string.google_usage_kind_nearby)
    kind == "place details" -> stringResource(R.string.google_usage_kind_details)
    kind == "photos" -> stringResource(R.string.google_usage_kind_photos)
    kind == "reviews" -> stringResource(R.string.google_usage_kind_reviews)
    kind == "images" -> stringResource(R.string.google_usage_kind_images)
    kind == "directions" -> stringResource(R.string.google_usage_kind_directions)
    kind == "suggestions" -> stringResource(R.string.google_usage_kind_suggestions)
    kind == "street view" -> stringResource(R.string.google_usage_kind_streetview)
    kind == "session" -> stringResource(R.string.google_usage_kind_session)
    kind == "shared list" -> stringResource(R.string.google_usage_kind_lists)
    kind == "page resources" -> stringResource(R.string.google_usage_kind_page_resources)
    kind.startsWith("page: ") -> stringResource(R.string.google_usage_kind_page, kind.removePrefix("page: "))
    else -> stringResource(R.string.google_usage_kind_other)
}

/** Whether Android hands Google Maps links to Astian Maps (issue #614). Android 12+ never lets an app verify
 *  someone else's domain, so the links open in the browser until they are switched on under the
 *  app's "Open by default" page; this shows the state and opens that page. */
@Composable
private fun MapsLinksGroup() {
    if (android.os.Build.VERSION.SDK_INT < 31) return
    val context = LocalContext.current
    var state by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(mapsLinkState(context)) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) state = mapsLinkState(context)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    Spacer(Modifier.height(8.dp))
    SettingsGroup {
        org.astian.suite.maps.ui.settings.SubHead(stringResource(R.string.settings_maps_links))
        org.astian.suite.maps.ui.settings.Hint(
            stringResource(
                when (state) {
                    2 -> R.string.settings_maps_links_on
                    1 -> R.string.settings_maps_links_partial
                    else -> R.string.settings_maps_links_off
                }
            )
        )
        androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            FilledTonalButton(
                modifier = Modifier.dpadHighlight(androidx.compose.material3.ButtonDefaults.filledTonalShape),
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                android.net.Uri.parse("package:" + context.packageName),
                            ),
                        )
                    }
                },
            ) { Text(stringResource(R.string.settings_maps_links_open)) }
        }
    }
}

/** 0 = off, 1 = some of the Google Maps hosts, 2 = all of them. */
private fun mapsLinkState(context: android.content.Context): Int = runCatching {
    if (android.os.Build.VERSION.SDK_INT < 31) return 0
    val m = context.getSystemService(android.content.pm.verify.domain.DomainVerificationManager::class.java)
    val st = m.getDomainVerificationUserState(context.packageName) ?: return 0
    if (!st.isLinkHandlingAllowed) return 0
    val hosts = st.hostToStateMap
    val on = hosts.count { it.value != android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_NONE }
    when { on == 0 -> 0; on < hosts.size -> 1; else -> 2 }
}.getOrDefault(0)
