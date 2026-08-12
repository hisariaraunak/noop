package com.noop.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

enum class DeltaDirection { Positive, Negative, Neutral }

data class MetricDeltaModel(
    val text: String,
    val direction: DeltaDirection = DeltaDirection.Neutral,
    val favorable: Boolean? = null,
)

@Composable
fun MetricDelta(delta: MetricDeltaModel, modifier: Modifier = Modifier) {
    val semantic = NoopTheme.semanticColors
    val color = when (delta.favorable) {
        true -> semantic.positive
        false -> semantic.negative
        null -> semantic.neutral
    }
    val prefix = when (delta.direction) {
        DeltaDirection.Positive -> "↑ "
        DeltaDirection.Negative -> "↓ "
        DeltaDirection.Neutral -> ""
    }
    Text(
        text = prefix + delta.text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

@Composable
fun MetricValue(
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, style = NoopType.dataDisplay, color = color)
        if (!unit.isNullOrBlank()) {
            Text(
                text = " $unit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    unit: String? = null,
    delta: MetricDeltaModel? = null,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
) {
    NoopSurface(modifier = modifier, level = NoopSurfaceLevel.Standard) {
        Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.sm)) {
            Text(
                text = label.uppercase(),
                style = NoopType.labelCaps,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                MetricValue(value = value, unit = unit)
                delta?.let { MetricDelta(it) }
            }
            if (!supportingText.isNullOrBlank()) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
