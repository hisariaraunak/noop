package com.noop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// MARK: - Insights hub (IA phase 4, 2026-08)
//
// The single "Insights" entry point, replacing five separate More-drawer rows (Insights Hub,
// Intelligence, the old Insights/journal screen, Explore, Compare) with one landing page of nav
// cards. Each card pushes that section's OWN existing screen unchanged — nothing here is inlined
// or re-implemented, this file is purely a router. Coach stays a separate top-level destination
// (a different interaction model — chat, its own ViewModel — not "insights content").

/**
 * Landing page for the Insights area: a card per section, each opening its own full screen.
 */
@Composable
fun InsightsScreen(
    onOpenJournal: () -> Unit,
    onOpenInsightsHub: () -> Unit,
    onOpenIntelligence: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenCompare: () -> Unit,
) {
    val context = LocalContext.current
    val showDayCycleBackground = remember { NoopPrefs.showDayCycleBackground(context) }
    val skyBehindCards = remember { NoopPrefs.skyBehindCards(context) }

    ScreenScaffold(
        title = "Insights",
        subtitle = "Your journal, what moves your Charge, and how your metrics relate.",
        topBackground = if (showDayCycleBackground) { { LiquidScreenSky(fillHeight = skyBehindCards) } } else null,
        fullBleedBackground = showDayCycleBackground && skyBehindCards,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space8)) {
            CompactMetricSummaryCard(
                icon = Icons.Filled.Edit,
                tint = Palette.metricAmber,
                label = "Journal",
                caption = "Log today, activity cost, your experiment",
                onClick = onOpenJournal,
            )
            CompactMetricSummaryCard(
                icon = Icons.Filled.Insights,
                tint = Palette.metricCyan,
                label = "What Moves Your Charge",
                caption = "Lag-aware effects, alcohol & caffeine dose curves",
                onClick = onOpenInsightsHub,
            )
            CompactMetricSummaryCard(
                icon = Icons.Filled.Psychology,
                tint = Palette.chargeColor,
                label = "Charge Model",
                caption = "Tomorrow's forecast, how the score is built",
                onClick = onOpenIntelligence,
            )
            CompactMetricSummaryCard(
                icon = Icons.Filled.Explore,
                tint = Palette.accent,
                label = "Explore",
                caption = "Any single metric, over any window",
                onClick = onOpenExplore,
            )
            CompactMetricSummaryCard(
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                tint = Palette.metricPurple,
                label = "Compare",
                caption = "Overlay 2-4 metrics, see how they correlate",
                onClick = onOpenCompare,
            )
        }
    }
}
