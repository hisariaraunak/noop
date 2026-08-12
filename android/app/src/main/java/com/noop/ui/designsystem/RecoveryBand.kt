package com.noop.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class RecoveryBand {
    High,
    Medium,
    Low,
    Unknown,
}

fun recoveryBand(score: Int?): RecoveryBand = when {
    score == null -> RecoveryBand.Unknown
    score >= 67 -> RecoveryBand.High
    score >= 34 -> RecoveryBand.Medium
    else -> RecoveryBand.Low
}

@Composable
fun RecoveryBand.color(): Color {
    val colors = NoopTheme.semanticColors
    return when (this) {
        RecoveryBand.High -> colors.recoveryHigh
        RecoveryBand.Medium -> colors.recoveryMedium
        RecoveryBand.Low -> colors.recoveryLow
        RecoveryBand.Unknown -> colors.neutral
    }
}
