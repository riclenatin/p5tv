package com.chroniac.tvhome.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chroniac.tvhome.AppEntry
import com.chroniac.tvhome.Bond
import com.chroniac.tvhome.BondRepository
import com.chroniac.tvhome.Entry
import com.chroniac.tvhome.R
import com.chroniac.tvhome.RankUp
import com.chroniac.tvhome.ShortcutEntry
import com.chroniac.tvhome.Weather
import kotlinx.coroutines.delay
import kotlin.math.pow

/** Full frame rate this long after the last key press or transition. */
private const val ACTIVE_SECONDS = 2.0

/** Then ~25 fps for ambient motion (cursor bob); after this, no frames at all. */
private const val DEEP_IDLE_SECONDS = 45.0
private const val IDLE_FRAME_DELAY_MS = 40L
private const val RANK_UP_MS = 3600L

@Composable
fun LauncherScreen(
    theme: ThemeId,
    apps: List<AppEntry>,
    shortcuts: List<ShortcutEntry>,
    bonds: Map<String, Bond>,
    rankUps: List<RankUp>,
    weather: Weather?,
    state: MenuState,
    onLaunch: (Entry) -> Unit,
    onMove: (AppEntry, Int) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onRankUpSeen: (RankUp) -> Unit,
    /** Called whenever the highlighted item actually changes (the game's cursor sound). */
    onCursor: () -> Unit,
) {
    val palette = theme.palette
    val entries = remember(apps, shortcuts) { apps + shortcuts }
    val time = remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) {
        while (true) {
            val idle = state.idleSeconds()
            when {
                idle > DEEP_IDLE_SECONDS -> state.awaitWake()
                idle > ACTIVE_SECONDS -> delay(IDLE_FRAME_DELAY_MS)
            }
            withFrameNanos { time.doubleValue = state.onFrame(it) }
        }
    }
    LaunchedEffect(entries.size) { state.clampTo(entries.size) }
    // Apps load asynchronously; the collage replays its entrance once they arrive so it is not spent
    // on an empty list. The command menu's opening is timed from the moment the screen is shown.
    val appsLoaded = apps.isNotEmpty()
    LaunchedEffect(appsLoaded) { if (appsLoaded && theme.layout == ThemeLayout.Portrait) state.requestReplay() }

    val firstTheme = remember { theme }
    var flashAt by remember { mutableDoubleStateOf(-10.0) }
    LaunchedEffect(theme) { if (theme != firstTheme || flashAt >= 0) flashAt = state.now() }

    val rankUp = rankUps.firstOrNull { up -> apps.any { it.key == up.packageName } }
    var rankUpShownAt by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(rankUp) {
        if (rankUp == null) return@LaunchedEffect
        rankUpShownAt = state.now()
        state.poke()
        delay(RANK_UP_MS)
        onRankUpSeen(rankUp)
    }

    var menu by remember { mutableStateOf<OpenMenu?>(null) }
    val keys = remember { KeyTracker() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler { menu = null } // also swallows BACK on the home screen itself

    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    val appInfo = stringResource(R.string.app_info)
    val arcana = stringArrayResource(R.array.arcana)

    fun openMenu(app: AppEntry) {
        val index = apps.indexOf(app)
        val items = buildList {
            if (index > 0) add(ActionItem(moveUp) { onMove(app, -1); state.select(index - 1) })
            if (index < apps.lastIndex) add(ActionItem(moveDown) { onMove(app, 1); state.select(index + 1) })
            add(ActionItem(appInfo) { onAppInfo(app) })
        }
        menu = OpenMenu(app, items, state.now())
    }

    fun onKey(event: KeyEvent): Boolean {
        state.poke()
        val down = event.action == KeyEvent.ACTION_DOWN
        if (rankUp != null) {
            if (!down) onRankUpSeen(rankUp)
            return true
        }
        val current = menu
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!down) return true
                val delta = if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                if (current != null) {
                    current.moveSelection(delta)?.let {
                        menu = it
                        onCursor()
                    }
                } else {
                    val before = state.selected
                    state.select((state.selected + delta).coerceIn(0, entries.lastIndex))
                    if (state.selected != before) onCursor()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (down) {
                    // Held OK: the first auto-repeat opens the context menu; its release must not launch.
                    if (event.repeatCount == 1 && current == null && !keys.okConsumed) {
                        (entries.getOrNull(state.selected) as? AppEntry)?.let { openMenu(it) }
                        keys.okConsumed = true
                    }
                    return true
                }
                if (keys.okConsumed) {
                    keys.okConsumed = false
                } else if (current != null) {
                    menu = null
                    current.items[current.selected].perform()
                } else {
                    entries.getOrNull(state.selected)?.let(onLaunch)
                }
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (down) return true
                if (current != null) menu = null
                else (entries.getOrNull(state.selected) as? AppEntry)?.let { openMenu(it) }
                return true
            }
        }
        return false
    }

    val selected = entries.getOrNull(state.selected)
    val bond = (selected as? AppEntry)?.let { bonds[it.key] }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { onKey(it.nativeKeyEvent) }
    ) {
        when (theme.layout) {
            ThemeLayout.Portrait -> {
                CollageBackdrop(palette)
                Portrait(
                    entry = selected,
                    bondLine = bond?.takeIf { it.rank > 0 }?.let { stringResource(R.string.bond_rank, arcana[it.arcana], it.rank) },
                    selectedAt = state.selectedAt,
                    time = time,
                    palette = palette,
                    modifier = Modifier.fillMaxSize().padding(start = 70.dp, top = 205.dp),
                )
                MenuList(entries, state, time, palette, anchorY = 0.44f, Modifier.fillMaxSize().padding(start = 480.dp))
                DateBadge(weather, palette, Modifier.padding(start = 40.dp, top = 24.dp))
            }
            ThemeLayout.Phantom -> {
                // Layer order as in the game's opening: the silhouettes cover the big number, the
                // stickers and plates stay on top of them, and COMMAND blends over everything.
                PhantomBackdrop(palette, state, time)
                CampHero(state, time)
                CommandNumber(state, palette, time)
                OpeningSilhouettes(state, time, palette)
                CampStickers(palette, state, time)
                CommandTitle(state, time)
                CommandList(entries, state, time, palette)
                captionFor(selected, bond, arcana)?.let { caption ->
                    CommandCaption(
                        text = caption,
                        rank = bond?.rank ?: 0,
                        state = state,
                        time = time,
                        palette = palette,
                        modifier = Modifier.offset(x = 568.dp, y = 448.dp),
                    )
                }
                val hud = Modifier.graphicsLayer { alpha = Opening.hudAlpha(gameFrame(time.doubleValue, state.openedAt)) }
                PhantomClock(palette, hud.offset(x = 310.dp, y = 428.dp))
                PhantomChips(weather, palette, hud.offset(x = 24.dp, y = 18.dp))
            }
        }
        // Button hints, bottom-right as in the game ("○ Close ✕ Confirm"); the command menu fades them in.
        Hints(
            listOf(
                stringResource(R.string.hint_ok) to stringResource(R.string.hint_open),
                stringResource(R.string.hint_hold) to stringResource(R.string.hint_menu),
            ),
            palette,
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 26.dp, bottom = 22.dp)
                .graphicsLayer {
                    alpha = if (theme.layout == ThemeLayout.Phantom) Opening.buttonsAlpha(gameFrame(time.doubleValue, state.openedAt)) else 1f
                },
        )
        menu?.let { ActionMenu(it, time, palette) }
        if (rankUp != null) {
            val label = apps.firstOrNull { it.key == rankUp.packageName }?.label.orEmpty()
            RankUpOverlay(rankUp, label, rankUpShownAt, time, palette)
        }
        ThemeFlash(flashAt, time, palette)
    }
}

/** What the line under "COMMAND" says about the selected entry; nothing for an app without a bond rank. */
@Composable
private fun captionFor(entry: Entry?, bond: Bond?, arcana: Array<String>): String? = when (entry) {
    is AppEntry -> bond?.takeIf { it.rank > 0 }?.let {
        stringResource(R.string.bond_rank, arcana[it.arcana], it.rank) + " · " +
            stringResource(R.string.bond_time, (it.minutes / 60).toInt(), (it.minutes % 60).toInt(), BondRepository.WINDOW_DAYS)
    }
    is ShortcutEntry -> entry.description
    null -> null
}

/** White flash over the whole screen when the theme changes. */
@Composable
private fun BoxScope.ThemeFlash(flashAt: Double, time: DoubleState, palette: Palette) {
    Box(Modifier.matchParentSize().drawBehind {
        val a = (1f - progress(time.doubleValue, flashAt, 0.4f)).pow(2)
        if (a > 0.01f) drawRect(palette.paper, alpha = a)
    })
}

/** Mutable flag outside snapshot state: it never affects rendering. */
private class KeyTracker {
    var okConsumed = false
}
