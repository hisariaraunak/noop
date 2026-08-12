package com.noop.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class NoopSurfaceLevel {
    Standard,
    Elevated,
    Glass,
}

/**
 * Base container for the rebuild. Glass is an explicit choice rather than the default card treatment.
 * Data-dense screens should normally use [Standard] or [Elevated].
 */
@Composable
fun NoopSurface(
    modifier: Modifier = Modifier,
    level: NoopSurfaceLevel = NoopSurfaceLevel.Standard,
    padding: PaddingValues = PaddingValues(NoopSpacing.cardPadding),
    content: @Composable BoxScope.() -> Unit,
) {
    val semantic = NoopTheme.semanticColors
    val color = when (level) {
        NoopSurfaceLevel.Standard -> MaterialTheme.colorScheme.surface
        NoopSurfaceLevel.Elevated -> MaterialTheme.colorScheme.surfaceVariant
        NoopSurfaceLevel.Glass -> semantic.glassSurface
    }
    val border = when (level) {
        NoopSurfaceLevel.Glass -> BorderStroke(1.dp, semantic.glassBorder)
        else -> null
    }
    val elevation = when (level) {
        NoopSurfaceLevel.Elevated -> 2.dp
        else -> 0.dp
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(NoopRadii.large),
        color = color,
        border = border,
        tonalElevation = elevation,
        shadowElevation = elevation,
    ) {
        Box(modifier = Modifier.padding(padding), content = content)
    }
}
