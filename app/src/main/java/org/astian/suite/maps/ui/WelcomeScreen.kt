package org.astian.suite.maps.ui

import org.astian.suite.maps.ui.icons.Sym

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.astian.suite.maps.R
import org.astian.suite.maps.ui.theme.isAppInDarkTheme

/**
 * First run, in the MAPS.ME shape: a drawn map behind everything, a carousel of what the app
 * does, then a bottom card that asks for location. "Continue" there ends the welcome and the
 * system dialog fires over the real map (AstianRoot owns that ask and its approximate-location
 * follow-up). Nothing behind this screen is composed until it is done.
 */
@Composable
fun WelcomeScreen(vm: org.astian.suite.maps.ui.map.MapViewModel, onGetStarted: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    val askLocation = remember { !Onboarding.hasLocation(context) }
    Box(Modifier.fillMaxSize()) {
        OnboardingMap(Modifier.fillMaxSize(), puck = step == 1)
        if (step == 0) {
            FeatureTour(onDone = { if (askLocation) step = 1 else onGetStarted() })
        } else {
            androidx.activity.compose.BackHandler { step = 0 }
            LocationCard(
                onAllow = onGetStarted,
                onLater = {
                    Onboarding.skipLocation(context)
                    onGetStarted()
                },
            )
        }
    }
}

private class Slide(
    val icon: ImageVector?,
    val title: Int,
    val body: Int,
    val from: Color,
    val to: Color,
)

private val SLIDES = listOf(
    Slide(null, R.string.onb_welcome_title, R.string.onb_welcome_body, Color(0xFF3B82F0), Color(0xFF1A4DB0)),
    Slide(Sym.CloudDownload, R.string.onb_offline_title, R.string.onb_offline_body, Color(0xFF35C27B), Color(0xFF138A55)),
    Slide(Sym.Traffic, R.string.onb_traffic_title, R.string.onb_traffic_body, Color(0xFFFF8A4C), Color(0xFFE0443A)),
    Slide(Sym.Navigation, R.string.onb_voice_title, R.string.onb_voice_body, Color(0xFF7C6CF6), Color(0xFF4A3BC8)),
    Slide(Sym.DirectionsCar, R.string.onb_car_title, R.string.onb_car_body, Color(0xFF455063), Color(0xFF1C222B)),
    Slide(Sym.Speed, R.string.onb_speed_title, R.string.onb_speed_body, Color(0xFFFFC23D), Color(0xFFEE8618)),
    Slide(Sym.Shield, R.string.onb_private_title, R.string.onb_private_body, Color(0xFF2FB5A7), Color(0xFF16776F)),
)

/** The carousel: one card per thing the app does, the neighbors peeking at the edges, dots, and
 *  Next (Get started on the last card). Skip jumps straight to the location card. */
@Composable
private fun FeatureTour(onDone: () -> Unit) {
    val pager = rememberPagerState { SLIDES.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == SLIDES.lastIndex
    val bg = MaterialTheme.colorScheme.surface
    val scroll = rememberScrollState()
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to bg.copy(alpha = 0.35f), 0.42f to bg.copy(alpha = 0.88f), 1f to bg)),
    ) {
      // Scrolls only where the screen is too short for the card and the button (a keypad phone);
      // elsewhere the spacers center the card.
      androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        val viewport = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .heightIn(min = viewport),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AstianMark(30.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDone, modifier = Modifier.dpadHighlight(RoundedCornerShape(20.dp))) {
                    Text(stringResource(R.string.onb_skip))
                }
            }
            Spacer(Modifier.weight(1f))
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 32.dp),
                pageSpacing = 14.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).let { kotlin.math.abs(it) }.coerceIn(0f, 1f)
                FeatureCard(
                    SLIDES[page],
                    Modifier.graphicsLayer {
                        val s = 1f - 0.06f * offset
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.35f * offset
                    },
                )
            }
            Spacer(Modifier.height(20.dp))
            PagerDots(count = SLIDES.size, current = pager.currentPage)
            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                OnboardingButton(stringResource(if (last) R.string.welcome_get_started else R.string.onb_next)) {
                    if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            }
        }
      }
    }
}

@Composable
private fun FeatureCard(slide: Slide, modifier: Modifier = Modifier) {
    val dark = isAppInDarkTheme()
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White,
        shadowElevation = 10.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FeatureArt(slide)
            // Room for a two-line title and a three-line body on every card, so the dots under
            // the pager stay put while swiping.
            Column(Modifier.heightIn(min = 172.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(slide.title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            )
            Text(
                stringResource(slide.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 3,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 22.dp),
            )
            }
        }
    }
}

/** The picture half of a card: the slide's gradient with a few map strokes and rings, and the
 *  feature's glyph (the launcher mark on the first card) in a white disc. */
@Composable
private fun FeatureArt(slide: Slide) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .background(Brush.linearGradient(listOf(slide.from, slide.to))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val faint = Color.White.copy(alpha = 0.14f)
            val w = size.width
            val h = size.height
            drawLine(faint, Offset(-20f, h * 0.78f), Offset(w + 20f, h * 0.42f), strokeWidth = 14.dp.toPx(), cap = StrokeCap.Round)
            drawLine(faint, Offset(w * 0.18f, -20f), Offset(w * 0.34f, h + 20f), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
            drawLine(faint, Offset(w * 0.8f, -20f), Offset(w * 0.66f, h + 20f), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            val c = Offset(w / 2f, h / 2f)
            drawCircle(Color.White.copy(alpha = 0.10f), radius = 88.dp.toPx(), center = c)
            drawCircle(Color.White.copy(alpha = 0.12f), radius = 66.dp.toPx(), center = c)
        }
        if (slide.icon == null) {
            AstianMark(92.dp)
        } else {
            Box(
                Modifier.size(92.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(slide.icon, contentDescription = null, tint = slide.to, modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun PagerDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val on = i == current
            val w by animateDpAsState(if (on) 22.dp else 8.dp, label = "dot")
            Box(
                Modifier
                    .height(8.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

/** The location ask, as a card over the map: Continue ends the welcome and Android's own dialog
 *  follows; Not now skips the ask, and the locate button asks later. */
@Composable
private fun LocationCard(onAllow: () -> Unit, onLater: () -> Unit) {
    val dark = isAppInDarkTheme()
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars), contentAlignment = Alignment.BottomCenter) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 12.dp)) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Sym.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.onb_location_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.onb_location_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                OnboardingButton(stringResource(R.string.welcome_continue), onClick = onAllow)
                TextButton(
                    onClick = onLater,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).dpadHighlight(RoundedCornerShape(20.dp)),
                ) { Text(stringResource(R.string.root_not_now)) }
            }
        }
    }
}

/**
 * The drawn map behind the onboarding: land, a river and a lake, parks, a street grid and two
 * arterials, drifting slowly. Drawn, not loaded, so the first run makes no request at all.
 * [puck] adds the pulsing location dot in the middle.
 */
@Composable
private fun OnboardingMap(modifier: Modifier, puck: Boolean) {
    val dark = isAppInDarkTheme()
    val t = rememberInfiniteTransition(label = "onbMap")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_800, easing = LinearEasing)), label = "pulse")
    val blocks = remember {
        val r = java.util.Random(7)
        List(90) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.025f + r.nextFloat() * 0.05f, 0.02f + r.nextFloat() * 0.04f) }
    }
    val pal = if (dark) MapPalette.DARK else MapPalette.LIGHT
    val puckBlue = MaterialTheme.colorScheme.primary
    Canvas(modifier.background(pal.land)) {
        val s = maxOf(size.width, size.height) * 1.25f
        val ox = -(s - size.width) / 2f + (drift - 0.5f) * size.width * 0.12f
        val oy = -(s - size.height) / 2f + (drift - 0.5f) * size.height * 0.05f
        translate(ox, oy) { drawStylizedMap(s, pal, blocks) }
        if (puck) {
            val c = Offset(size.width / 2f, size.height * 0.42f)
            drawCircle(puckBlue.copy(alpha = 0.22f * (1f - pulse)), radius = (14 + 46 * pulse).dp.toPx(), center = c)
            drawCircle(Color.White, radius = 13.dp.toPx(), center = c)
            drawCircle(puckBlue, radius = 9.dp.toPx(), center = c)
        }
    }
}

private class MapPalette(
    val land: Color,
    val block: Color,
    val park: Color,
    val water: Color,
    val street: Color,
    val major: Color,
    val majorCasing: Color,
) {
    companion object {
        val LIGHT = MapPalette(
            land = Color(0xFFF2EFE7), block = Color(0xFFE7E2D7), park = Color(0xFFCDE7B9),
            water = Color(0xFFA9D2F2), street = Color.White, major = Color(0xFFFBD38D), majorCasing = Color(0xFFE5B262),
        )
        val DARK = MapPalette(
            land = Color(0xFF1C2126), block = Color(0xFF242A30), park = Color(0xFF203A2A),
            water = Color(0xFF1B3550), street = Color(0xFF2F383F), major = Color(0xFF6E5B32), majorCasing = Color(0xFF4C3F24),
        )
    }
}

private fun DrawScope.drawStylizedMap(s: Float, p: MapPalette, blocks: List<FloatArray>) {
    blocks.forEach { b ->
        drawRoundRect(p.block, topLeft = Offset(b[0] * s, b[1] * s), size = Size(b[2] * s, b[3] * s), cornerRadius = CornerRadius(4f, 4f))
    }
    drawOval(p.park, topLeft = Offset(0.06f * s, 0.10f * s), size = Size(0.30f * s, 0.18f * s))
    drawRoundRect(p.park, topLeft = Offset(0.62f * s, 0.80f * s), size = Size(0.30f * s, 0.16f * s), cornerRadius = CornerRadius(40f, 40f))
    drawOval(p.park, topLeft = Offset(0.14f * s, 0.74f * s), size = Size(0.18f * s, 0.12f * s))
    drawOval(p.water, topLeft = Offset(0.70f * s, 0.08f * s), size = Size(0.24f * s, 0.17f * s))
    val river = Path().apply {
        moveTo(-0.05f * s, 0.60f * s)
        cubicTo(0.25f * s, 0.52f * s, 0.45f * s, 0.78f * s, 0.70f * s, 0.66f * s)
        cubicTo(0.85f * s, 0.59f * s, 0.95f * s, 0.63f * s, 1.05f * s, 0.70f * s)
    }
    drawPath(river, p.water, style = Stroke(width = 0.05f * s, cap = StrokeCap.Round))
    rotate(-12f, pivot = Offset(s / 2f, s / 2f)) {
        val streetW = 0.012f * s
        var x = -0.2f
        while (x < 1.2f) {
            drawLine(p.street, Offset(x * s, -0.2f * s), Offset(x * s, 1.2f * s), strokeWidth = streetW)
            x += 0.105f
        }
        var y = -0.2f
        while (y < 1.2f) {
            drawLine(p.street, Offset(-0.2f * s, y * s), Offset(1.2f * s, y * s), strokeWidth = streetW)
            y += 0.09f
        }
    }
    val arterial = Path().apply {
        moveTo(-0.05f * s, 0.30f * s)
        cubicTo(0.30f * s, 0.36f * s, 0.62f * s, 0.30f * s, 1.05f * s, 0.46f * s)
    }
    drawPath(arterial, p.majorCasing, style = Stroke(width = 0.030f * s, cap = StrokeCap.Round))
    drawPath(arterial, p.major, style = Stroke(width = 0.022f * s, cap = StrokeCap.Round))
    val avenue = Path().apply {
        moveTo(0.58f * s, -0.05f * s)
        cubicTo(0.52f * s, 0.35f * s, 0.60f * s, 0.62f * s, 0.46f * s, 1.05f * s)
    }
    drawPath(avenue, p.majorCasing, style = Stroke(width = 0.024f * s, cap = StrokeCap.Round))
    drawPath(avenue, p.major, style = Stroke(width = 0.017f * s, cap = StrokeCap.Round))
}

/** The Astian Maps mark as the launcher draws it: the white pin with its "A" on the brand gradient. The launcher
 *  foreground keeps its art inside the adaptive icon's safe zone, so it is drawn 1.5 times the
 *  box and clipped. */
@Composable
internal fun AstianMark(size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 27))
            .background(Brush.linearGradient(listOf(Color(0xFF3B82F0), Color(0xFF1A4DB0)))),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}

/** The filled primary button of a first-run page, which takes focus on a D-pad device. A Material
 *  `Button` would not take requestFocus here (its nested focusable is not reachable, the same as
 *  dialog buttons), so this is a directly `.focusable()` box with OK via `.onKeyEvent` and touch
 *  via `pointerInput`, styled like the filled Button it replaces. */
@Composable
private fun OnboardingButton(label: String, autoFocus: Boolean = true, onClick: () -> Unit) {
    val fr = remember { FocusRequester() }
    val dpadFirst = rememberDpadFirstDevice()
    val tap by androidx.compose.runtime.rememberUpdatedState(onClick)
    LaunchedEffect(dpadFirst) {
        if (dpadFirst && autoFocus) repeat(40) {
            if (runCatching { fr.requestFocus() }.isSuccess) return@LaunchedEffect
            kotlinx.coroutines.delay(50)
        }
    }
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .focusRequester(fr)
            .dpadHighlight(shape)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF3B82F0), Color(0xFF1A4DB0))))
            .onKeyEvent { ev ->
                if ((ev.key == Key.DirectionCenter || ev.key == Key.Enter) && ev.type == KeyEventType.KeyUp) {
                    tap(); true
                } else {
                    false
                }
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { tap() } },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

/** The release notes of the build just updated to, shown once (see [WhatsNew]). Same shape as the
 *  donate prompt: an icon, a title, the text, a filled "Got it" and a quiet "Full notes". */
@Composable
fun WhatsNewPrompt(version: String, notes: String, onOpenRelease: () -> Unit, onDismiss: () -> Unit) {
    AstianDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.whatsnew_title, version),
        confirmText = stringResource(R.string.whatsnew_got_it),
        onConfirm = onDismiss,
        dismissText = stringResource(R.string.whatsnew_full_notes),
        onDismiss = onOpenRelease,
        dismissLowEmphasis = true,
        icon = { Icon(Sym.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        text = {
          Column {
            // The list can be long after a week of nightlies; cap it and scroll inside the dialog.
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(notes, style = MaterialTheme.typography.bodyMedium)
            }
            // The same switch as Settings > About, where the prompt is.
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .dpadHighlight()
                    .dpadClickable { WhatsNew.setEnabled(context, !WhatsNew.enabled.value) },
            ) {
                androidx.compose.material3.Checkbox(checked = WhatsNew.enabled.value, onCheckedChange = null)
                Text(
                    stringResource(R.string.whatsnew_show_after_updates),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
          }
        },
    )
}

/** The one-time, low-pressure donation prompt (see [Onboarding] for the etiquette). */
@Composable
fun DonatePrompt(onDonate: () -> Unit, onDismiss: () -> Unit) {
    AstianDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.welcome_donate_title),
        confirmText = stringResource(R.string.welcome_donate_confirm),
        onConfirm = onDonate,
        dismissText = stringResource(R.string.welcome_donate_dismiss),
        onDismiss = onDismiss,
        icon = { Icon(Sym.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        text = { Text(stringResource(R.string.welcome_donate_body)) },
    )
}
