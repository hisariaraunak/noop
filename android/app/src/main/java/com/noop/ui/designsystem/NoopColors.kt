package com.noop.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Morning Dew-derived brand palette for the Android rebuild.
 *
 * Brand colors are intentionally separate from physiological status colors. A moss accent means
 * "NOOP"; it does not automatically mean "healthy" or "high recovery".
 */
object NoopBrandColors {
    val Moss900 = Color(0xFF2E4E38)
    val Moss700 = Color(0xFF45664F)
    val Moss400 = Color(0xFF7DA086)
    val Moss200 = Color(0xFFC6ECCE)

    val Clay700 = Color(0xFF75584D)
    val Clay300 = Color(0xFFFED7CA)

    val Mist700 = Color(0xFF314A57)
    val Mist300 = Color(0xFFCCE6F6)

    val Ink = Color(0xFF191C1B)
    val InkMuted = Color(0xFF424842)
    val Outline = Color(0xFF727972)
    val OutlineSoft = Color(0xFFC2C8C0)

    val Canvas = Color(0xFFF8FAF8)
    val SurfaceLowest = Color(0xFFFFFFFF)
    val SurfaceLow = Color(0xFFF2F4F2)
    val Surface = Color(0xFFECEEEC)
    val SurfaceHigh = Color(0xFFE6E9E7)
    val SurfaceHighest = Color(0xFFE1E3E1)

    val DarkCanvas = Color(0xFF101412)
    val DarkSurfaceLow = Color(0xFF171C19)
    val DarkSurface = Color(0xFF1C221F)
    val DarkSurfaceHigh = Color(0xFF242A27)
    val DarkInk = Color(0xFFE9EEE9)
    val DarkInkMuted = Color(0xFFBBC5BD)
}

@Immutable
data class NoopSemanticColors(
    val recoveryHigh: Color,
    val recoveryMedium: Color,
    val recoveryLow: Color,
    val positive: Color,
    val caution: Color,
    val negative: Color,
    val neutral: Color,
    val chartPrimary: Color,
    val chartSecondary: Color,
    val chartTertiary: Color,
    val baselineBand: Color,
    val glassSurface: Color,
    val glassBorder: Color,
)

val NoopLightSemanticColors = NoopSemanticColors(
    recoveryHigh = Color(0xFF2F7D4B),
    recoveryMedium = Color(0xFFB7791F),
    recoveryLow = Color(0xFFB33A3A),
    positive = Color(0xFF2F7D4B),
    caution = Color(0xFFB7791F),
    negative = Color(0xFFB33A3A),
    neutral = Color(0xFF66706A),
    chartPrimary = NoopBrandColors.Moss700,
    chartSecondary = NoopBrandColors.Clay700,
    chartTertiary = NoopBrandColors.Mist700,
    baselineBand = Color(0x2645664F),
    glassSurface = Color(0x99FFFFFF),
    glassBorder = Color(0xCCFFFFFF),
)

val NoopDarkSemanticColors = NoopSemanticColors(
    recoveryHigh = Color(0xFF72C58C),
    recoveryMedium = Color(0xFFE2B463),
    recoveryLow = Color(0xFFE27A7A),
    positive = Color(0xFF72C58C),
    caution = Color(0xFFE2B463),
    negative = Color(0xFFE27A7A),
    neutral = Color(0xFFAAB4AD),
    chartPrimary = Color(0xFFABCDB3),
    chartSecondary = Color(0xFFE4BEB1),
    chartTertiary = Color(0xFFB0CAD9),
    baselineBand = Color(0x3345664F),
    glassSurface = Color(0x99171C19),
    glassBorder = Color(0x332FFFFFF),
)
