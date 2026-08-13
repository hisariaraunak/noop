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

@Composable
fun RecoveryHero(
    score: Int?,
    headline: String,
    recommendation: String,
    modifier: Modifier = Modifier,
) {
    val band = recoveryBand(score)
    NoopSurface(modifier = modifier, level = NoopSurfaceLevel.Glass) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NoopSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(0.34f), verticalArrangement = Arrangement.spacedBy(NoopSpacing.xxs)) {
                Text("RECOVERY", style = NoopType.labelCaps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(score?.toString() ?: "—", style = NoopType.dataHero, color = band.color())
                    if (score != null) Text("%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(0.66f), verticalArrangement = Arrangement.spacedBy(NoopSpacing.xs)) {
                Text(headline, style = NoopType.sectionTitle, color = MaterialTheme.colorScheme.onSurface)
                Text(recommendation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
