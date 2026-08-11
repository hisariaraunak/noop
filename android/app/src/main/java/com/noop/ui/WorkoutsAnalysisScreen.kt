package com.noop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.HeartRateRecovery
import com.noop.data.WorkoutRow
import java.util.Locale
import kotlin.math.roundToInt

/**
 * "View analysis" — the Activity Breakdown / HR zones / Recovery trend sections that used to live
 * inline on the Workouts list (2026-08 redesign moved them here, reached via a link in the
 * Sessions header, so the list itself gets to the actual workouts sooner). Shows the UNFILTERED
 * picture — its own independent range picker, seeded from whatever range was active on the list
 * when "View analysis" was tapped, but not the list's sport/source/search filter (avoids encoding
 * free-text search through a route argument). Internals of the three sections are unchanged from
 * before, only their host screen is new.
 */
@Composable
fun WorkoutsAnalysisScreen(vm: AppViewModel, initialRangeName: String? = null) {
    val allRows by vm.workouts.collectAsState()
    val lastHistorySyncAt by vm.lastHistorySyncAt.collectAsStateWithLifecycle()
    var range by remember {
        mutableStateOf(WorkoutRange.entries.find { it.name == initialRangeName } ?: WorkoutRange.All)
    }
    // Only re-seed from the tap-through range when none was supplied AND rows just loaded — once
    // the user has this screen open, its own picker is independent (mirrors WorkoutsScreen's own
    // "pick default range once" guard).
    var didPickDefaultRange by remember { mutableStateOf(initialRangeName != null) }
    LaunchedEffect(allRows) {
        if (!didPickDefaultRange && allRows.isNotEmpty()) {
            range = defaultRange(allRows)
            didPickDefaultRange = true
        }
    }

    val resolved = effectiveRange(allRows, range)
    val windowRows = sessions(allRows, resolved)
    val groups = sportGroups(windowRows)

    // Same recovery-trend computation WorkoutsScreen.kt used to own (#516), now independent and
    // unfiltered here — capped to 90 days so a deep imported history doesn't launch hundreds of
    // raw-HR reads; W/M/3M are the promised trend views.
    val recoveryRange = if (resolved.days == null || resolved.days > 90) WorkoutRange.Quarter else resolved
    val recoveryRows = sessions(allRows, recoveryRange).sortedBy { it.startTs }
    val recoveryInputKey = remember(recoveryRange, recoveryRows) {
        buildString {
            append(recoveryRange.name)
            recoveryRows.forEach { append('|').append(it.startTs).append(':').append(it.endTs) }
        }
    }
    var recoveryTrend by remember { mutableStateOf<List<WorkoutRecoveryTrendPoint>>(emptyList()) }
    LaunchedEffect(recoveryInputKey, vm.activeStrapId, lastHistorySyncAt) {
        val built = ArrayList<WorkoutRecoveryTrendPoint>()
        for (row in recoveryRows) {
            val result = vm.workoutHeartRateRecovery(row.startTs, row.endTs, row.source, row.deviceId) ?: continue
            built += WorkoutRecoveryTrendPoint(row.startTs, result)
        }
        recoveryTrend = built
    }

    ScreenScaffold(title = "Activity analysis", subtitle = "Breakdown, HR zones, recovery.") {
        SegmentedPillControl(
            items = WorkoutRange.entries,
            selection = range,
            label = { it.label },
            onSelect = { range = it; didPickDefaultRange = true },
        )
        if (windowRows.isEmpty()) {
            DataPendingNote(
                title = "No workouts in this range",
                body = "Step to a wider range above to see its breakdown.",
            )
        } else {
            BreakdownSection(groups = groups, rows = windowRows)
            ZonesSection(windowRows)
            if (recoveryTrend.isNotEmpty()) {
                RecoveryTrendSection(recoveryTrend, recoveryRange.localizedCaption())
            }
        }
    }
}

// MARK: - Activity breakdown (per-sport NoopCards, identical layout) — relocated verbatim from
// WorkoutsScreen.kt (2026-08 redesign).

@Composable
private fun BreakdownSection(groups: List<SportGroup>, rows: List<WorkoutRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        SectionHeader(
            title = uiString(R.string.l10n_workouts_screen_activity_breakdown_214431d6),
            overline = "By sport",
            trailing = "${groups.size} sport${if (groups.size == 1) "" else "s"}",
        )
        // This sport's own sessions, so each card can carry an HR-zone mini-bar.
        groups.forEach { g -> SportCard(g, zones = zoneSummary(rows.filter { it.sport == g.sport })) }
    }
}

@Composable
private fun SportCard(g: SportGroup, zones: ZoneSummary?) {
    // Frosted Effort-tinted card with the sport glyph in the Effort world, plus an HR-zone mini-bar
    // when the sessions carry imported zones.
    NoopCard(tint = Palette.effortColor) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Identical header for every card.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    sportIcon(g.sport),
                    contentDescription = null,
                    tint = Palette.effortColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    WorkoutEditing.displaySport(g.sport),
                    style = NoopType.headline,
                    color = Palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(uiString(R.string.l10n_workouts_screen_g_count_247d8c10, g.count), style = NoopType.number(15f), color = Palette.effortBright)
            }
            if (zones != null) {
                SegmentBar(
                    segments = zones.minutes.mapIndexed { i, m ->
                        Palette.hrZoneColor(i + 1) to (m / zones.totalMinutes).toFloat()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 8.dp,
                )
            }
            CardDivider()
            // Identical 4-up stat strip for every card.
            Row(modifier = Modifier.fillMaxWidth()) {
                MiniStat("Sessions", "${g.count}", Modifier.weight(1f))
                MiniStat("Time", oneDecimal(g.totalTimeH) + "h", Modifier.weight(1f))
                MiniStat("Kcal", grouped(g.totalKcal), Modifier.weight(1f), tint = Palette.metricAmber)
                MiniStat("Avg/sess", "${g.avgTimePerSessionMin.roundToInt()}m", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier, tint: Color = Palette.textPrimary) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Overline(label)
        Text(
            value,
            style = NoopType.number(15f),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// MARK: - HR zones (imported per-workout zone split, one card) — relocated verbatim.

@Composable
private fun ZonesSection(rows: List<WorkoutRow>) {
    val z = remember(rows) { zoneSummary(rows) } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        SectionHeader(
            title = uiString(R.string.l10n_workouts_screen_hr_zones_293d7175),
            overline = "Whoop import",
            trailing = "${z.sessionsWithZones} of ${rows.size} session${if (rows.size == 1) "" else "s"}",
        )
        NoopCard(tint = Palette.effortColor) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Proportional stacked bar — the Hypnogram geometry with zone colors.
                SegmentBar(
                    segments = z.minutes.mapIndexed { i, m ->
                        Palette.hrZoneColor(i + 1) to (m / z.totalMinutes).toFloat()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 24.dp,
                )
                CardDivider()
                // 5-up stat strip, identical rhythm to the sport cards' MiniStat row.
                Row(modifier = Modifier.fillMaxWidth()) {
                    z.minutes.forEachIndexed { i, m ->
                        ZoneStat(i + 1, m, z.totalMinutes, Modifier.weight(1f))
                    }
                }
                Text(
                    uiString(R.string.l10n_workouts_screen_share_of_imported_zone_time_duration_b0985680),
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
        }
    }
}

// MARK: - Recovery trend (shared-axis 1/2/5-minute HRR, #516) — relocated verbatim.

private data class WorkoutRecoveryTrendPoint(
    val startTs: Long,
    val result: HeartRateRecovery.Result,
)

/** Shared-axis 1/2/5-minute HRR trend (#516). These are raw bpm changes, not normalized values, so the
 *  distance between lines remains meaningful. Missing minute windows are omitted from that series. */
@Composable
private fun RecoveryTrendSection(points: List<WorkoutRecoveryTrendPoint>, rangeCaption: String) {
    val oneColor = Palette.metricRose
    val twoColor = Palette.metricCyan
    val fiveColor = Palette.metricPurple
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        SectionHeader(
            title = uiString(R.string.l10n_workouts_screen_recovery_trend_516),
            overline = uiString(R.string.l10n_workouts_screen_hrr_range_516, rangeCaption),
            trailing = uiPlural(
                R.plurals.l10n_workouts_screen_hrr_workout_count_516,
                points.size,
                points.size,
            ),
        )
        NoopCard(tint = Palette.metricRose) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecoveryTrendChart(
                    points = points,
                    oneColor = oneColor,
                    twoColor = twoColor,
                    fiveColor = fiveColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Metrics.chartHeight),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(dateLabel(points.first().startTs), style = NoopType.footnote, color = Palette.textTertiary)
                    Spacer(Modifier.weight(1f))
                    Text(dateLabel(points.last().startTs), style = NoopType.footnote, color = Palette.textTertiary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RecoveryLegend(uiString(R.string.l10n_workouts_screen_hrr_one_minute_516), oneColor)
                    RecoveryLegend(uiString(R.string.l10n_workouts_screen_hrr_two_minutes_516), twoColor)
                    RecoveryLegend(uiString(R.string.l10n_workouts_screen_hrr_five_minutes_516), fiveColor)
                }
                CardDivider()
                Text(
                    uiString(R.string.l10n_workouts_screen_hrr_trend_explanation_516),
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun RecoveryLegend(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = NoopType.footnote, color = Palette.textSecondary)
    }
}

@Composable
private fun RecoveryTrendChart(
    points: List<WorkoutRecoveryTrendPoint>,
    oneColor: Color,
    twoColor: Color,
    fiveColor: Color,
    modifier: Modifier = Modifier,
) {
    val allValues = points.flatMap {
        listOfNotNull(it.result.after1Minute, it.result.after2Minutes, it.result.after5Minutes)
    }
    if (allValues.isEmpty()) return
    val low = allValues.minOrNull() ?: 0
    val high = allValues.maxOrNull() ?: low
    val padding = maxOf(4.0, (high - low) * 0.12)
    val yMin = low - padding
    val yMax = high + padding
    val xMin = points.first().startTs
    val xMax = points.last().startTs
    val chartDescription = uiString(R.string.l10n_workouts_screen_hrr_chart_accessibility_516)

    Canvas(modifier = modifier.semantics { contentDescription = chartDescription }) {
        val gridColor = Palette.hairline
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        fun x(ts: Long): Float = if (xMax == xMin) size.width / 2f
            else ((ts - xMin).toDouble() / (xMax - xMin).toDouble() * size.width).toFloat()
        fun y(value: Int): Float =
            (size.height - ((value - yMin) / (yMax - yMin) * size.height)).toFloat()

        fun drawSeries(color: Color, pick: (HeartRateRecovery.Result) -> Int?) {
            val series = points.mapNotNull { point -> pick(point.result)?.let { point.startTs to it } }
            if (series.isEmpty()) return
            val path = Path()
            series.forEachIndexed { index, (ts, value) ->
                val px = x(ts)
                val py = y(value)
                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            if (series.size > 1) {
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            series.forEach { (ts, value) -> drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(x(ts), y(value))) }
        }

        drawSeries(oneColor) { it.after1Minute }
        drawSeries(twoColor) { it.after2Minutes }
        drawSeries(fiveColor) { it.after5Minutes }
    }
}

// MARK: - Formatting (small file-scoped helpers, matching the same "duplicated per file" idiom
// WorkoutsScreen.kt/TodayScreen.kt already use for these one-liners rather than centralizing them)

private fun oneDecimal(v: Double): String = String.format(Locale.US, "%.1f", v)
private fun grouped(v: Double): String = String.format(Locale.US, "%,d", v.roundToInt())
