package com.chroniac.tvhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chroniac.tvhome.AppEntry
import kotlin.math.min

class ActionItem(val label: String, val perform: () -> Unit)

/** Context menu state for one app; [openedAt] drives the slide-in. */
class OpenMenu(val app: AppEntry, val items: List<ActionItem>, val openedAt: Double) {
    var selected = 0
        private set

    fun moveSelection(delta: Int): OpenMenu? {
        val next = (selected + delta).coerceIn(0, items.lastIndex)
        if (next == selected) return null
        return OpenMenu(app, items, openedAt).also { it.selected = next }
    }
}

/** Dimmed overlay with a skewed black panel sliding in from the right. */
@Composable
fun BoxScope.ActionMenu(menu: OpenMenu, time: DoubleState, palette: Palette) {
    val shape = SkewShape(0.06f)
    Box(Modifier.matchParentSize().drawBehind {
        drawRect(palette.ink, alpha = 0.55f * easeOutCubic(progress(time.doubleValue, menu.openedAt, 0.18f)))
    })
    Column(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 90.dp)
            .graphicsLayer {
                val p = easeOutBack(progress(time.doubleValue, menu.openedAt, 0.28f), 1.6f)
                translationX = (1f - p) * 260.dp.toPx()
                rotationZ = mix(10f, -3f, p)
                alpha = min(1f, p * 1.5f)
            }
            .background(palette.ink, shape)
            .border(4.dp, palette.accent, shape)
            .padding(horizontal = 44.dp, vertical = 26.dp)
    ) {
        BasicText(menu.app.label.uppercase(), style = TextStyle(fontFamily = Fonts.dela, fontSize = 30.sp, color = palette.paper))
        Spacer(Modifier.height(14.dp))
        menu.items.forEachIndexed { i, item ->
            val chosen = i == menu.selected
            Box(
                Modifier
                    .padding(vertical = 4.dp)
                    .then(if (chosen) Modifier.background(palette.paper, SkewShape(0.3f)) else Modifier)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                BasicText(
                    item.label.uppercase(),
                    style = TextStyle(fontFamily = Fonts.russo, fontSize = 26.sp, color = if (chosen) palette.ink else palette.paper),
                )
            }
        }
    }
}

/** Remote hints, bottom-right. */
@Composable
fun Hints(hints: List<Pair<String, String>>, palette: Palette, modifier: Modifier = Modifier) {
    Row(modifier.graphicsLayer { rotationZ = -3f }) {
        hints.forEach { (key, action) ->
            Box(Modifier.padding(start = 14.dp).background(palette.paper, SkewShape(0.35f)).padding(horizontal = 12.dp, vertical = 2.dp)) {
                BasicText(key, style = TextStyle(fontFamily = Fonts.oswald, fontSize = 18.sp, color = palette.ink))
            }
            Box(Modifier.background(palette.ink, SkewShape(0.35f)).padding(horizontal = 14.dp, vertical = 2.dp)) {
                BasicText(action.uppercase(), style = TextStyle(fontFamily = Fonts.oswald, fontSize = 18.sp, color = palette.paper))
            }
        }
    }
}
