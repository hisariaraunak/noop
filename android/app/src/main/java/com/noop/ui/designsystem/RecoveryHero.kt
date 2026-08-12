package com.noop.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun RecoveryHero(
    score: Int?,
    headline: String,
    recommendation: String,
    modifier: Modifier = Modifier,
) {
    val band = recoveryBand(score)
    NoopSurface(modifier = modifier, level = NoopSurfaceLevel.Glass) {
        Column(verticalArrangement = Arrangement.spacedBy(NoopSpacing.md)) {
            Text(
                text = headline,
                style = NoopType.editorialHeadline,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = score?.toString() ?: "—",
                    style = NoopType.dataHero,
                    color = band.color(),
                )
                if (score != null) {
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = recommendation,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
