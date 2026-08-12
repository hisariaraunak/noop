package com.noop.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Small, dependency-free trend primitive. Full interactive charts remain a separate component. */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = NoopTheme.semanticColors.chartPrimary,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - min) / range * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
    }
}

/** Shows today's value relative to a personal baseline interval. */
@Composable
fun BaselineRange(
    value: Float,
    baselineLow: Float,
    baselineHigh: Float,
    domainLow: Float,
    domainHigh: Float,
    modifier: Modifier = Modifier,
) {
    val semantic = NoopTheme.semanticColors
    Canvas(modifier = modifier.fillMaxWidth().height(18.dp)) {
        val domain = (domainHigh - domainLow).takeIf { it > 0f } ?: return@Canvas
        fun x(v: Float) = ((v - domainLow) / domain).coerceIn(0f, 1f) * size.width
        val centerY = size.height / 2
        drawLine(
            color = semantic.neutral.copy(alpha = 0.22f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 4.dp.toPx(),
        )
        drawLine(
            color = semantic.baselineBand,
            start = Offset(x(baselineLow), centerY),
            end = Offset(x(baselineHigh), centerY),
            strokeWidth = 8.dp.toPx(),
        )
        drawCircle(color = semantic.chartPrimary, radius = 5.dp.toPx(), center = Offset(x(value), centerY))
    }
}
