package com.chroniac.tvhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chroniac.tvhome.R
import com.chroniac.tvhome.RankUp
import kotlin.math.min

/** "Rank up!" fanfare over everything: spinning burst, the app's name and the new rank. */
@Composable
fun BoxScope.RankUpOverlay(rankUp: RankUp, appLabel: String, shownAt: Double, time: DoubleState, palette: Palette) {
    val arcana = stringArrayResource(R.array.arcana)[rankUp.arcana]
    Box(Modifier.matchParentSize().drawWithCache {
        val c = Offset(size.width / 2, size.height / 2)
        val outer = burst(c, inner = size.height * 0.30f, outer = size.height * 0.62f, spikes = 16, seed = 5f)
        val inner = burst(c, inner = size.height * 0.24f, outer = size.height * 0.44f, spikes = 13, seed = 9f)
        onDrawBehind {
            val t = time.doubleValue
            val p = progress(t, shownAt, 0.4f)
            drawRect(palette.ink, alpha = 0.6f * easeOutCubic(p))
            val pop = easeOutBack(p, 1.8f)
            val spin = ((t - shownAt) * 18.0 % 360.0).toFloat()
            scale(pop, pop, c) {
                rotate(spin, c) { drawPath(outer, palette.accent) }
                rotate(-spin * 0.6f, c) { drawPath(inner, palette.paper) }
            }
        }
    })
    Column(
        Modifier
            .align(Alignment.Center)
            .graphicsLayer {
                val p = easeOutBack(progress(time.doubleValue, shownAt, 0.36f, delay = 0.12f), 2f)
                scaleX = p
                scaleY = p
                rotationZ = -6f
                alpha = min(1f, p * 2f).coerceAtLeast(0f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.background(palette.ink, SkewShape(0.2f)).padding(horizontal = 34.dp, vertical = 6.dp)) {
            BasicText(stringResource(R.string.rank_up).uppercase(), style = TextStyle(fontFamily = Fonts.dela, fontSize = 58.sp, color = palette.paper))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.background(palette.paper, SkewShape(0.2f)).padding(horizontal = 26.dp, vertical = 4.dp)) {
            BasicText(appLabel.uppercase(), style = TextStyle(fontFamily = Fonts.dela, fontSize = 36.sp, color = palette.ink))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.background(palette.ink, SkewShape(0.2f)).padding(horizontal = 22.dp, vertical = 4.dp)) {
            BasicText(
                stringResource(R.string.rank_up_detail, arcana, rankUp.from, rankUp.to).uppercase(),
                style = TextStyle(fontFamily = Fonts.russo, fontSize = 28.sp, color = palette.accent),
            )
        }
        Spacer(Modifier.height(12.dp))
        RankPips(rankUp.to, palette, Modifier.size(width = 260.dp, height = 22.dp))
    }
}

/** Ten slanted pips, the first [rank] filled: the confidant rank meter. */
@Composable
fun RankPips(rank: Int, palette: Palette, modifier: Modifier = Modifier) {
    Box(modifier.drawBehind {
        val step = size.width / 10f
        val lean = size.height * 0.35f
        for (i in 0 until 10) {
            val x = i * step
            drawPath(
                polygon(
                    Offset(x + lean, 0f),
                    Offset(x + step * 0.8f, 0f),
                    Offset(x + step * 0.8f - lean, size.height),
                    Offset(x, size.height),
                ),
                if (i < rank) palette.accent else palette.paper,
                alpha = if (i < rank) 1f else 0.25f,
            )
        }
    })
}
