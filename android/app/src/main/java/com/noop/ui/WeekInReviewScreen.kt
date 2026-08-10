package com.noop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.BalanceRead
import com.noop.analytics.WeeklyDigest
import com.noop.analytics.WeeklyDigestEngine
import com.noop.analytics.WeeklyMetric
import com.noop.analytics.WeeklyMetricSummary
import com.noop.data.DailyMetric
import kotlin.math.abs
import kotlin.math.roundToInt

// MARK: - Week in Review (2026-08, replaces Trends)
//
// The user's read of the old Trends tab: the weekly digest nav + Charge/Effort/Rest trio at the
// top already told them everything useful; the rest (a Charge-only hero chart, a "recovery
// history" strip standing in for a macOS-only calendar heat-grid) did not. This screen keeps only
// the useful part and gives it a dedicated tab: a Story-style swipeable card strip on top (the
// week's headline read, at a glance) over a Dashboard-style dense table on the bottom (every
// metric, every column, always visible). Both zones are built from the SAME already-computed
// WeeklyDigest (WeeklyDigest.kt / buildWeeklyDigest) — no new data plumbing.

@Composable
fun WeekInReviewScreen(vm: AppViewModel) {
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val effortScale = UnitPrefs.effortScale(LocalContext.current)
    val factor = effortDisplayFactor(effortScale)

    // Browse previous weeks, same clamp behaviour Trends used (#710): 0 = the week containing
    // today, each step back one Mon-Sun week, clamped so it never runs past the earliest day held.
    var weekOffset by rememberSaveable { mutableStateOf(0) }
    val minOffset = remember(days) { minWeekOffset(days) }
    LaunchedEffect(minOffset) { weekOffset = weekOffset.coerceIn(minOffset, 0) }
    val anchorDay = remember(weekOffset) { WeeklyDigestEngine.addDays(logicalDayKeyNow(), weekOffset * 7) }
    val digest = remember(days, anchorDay, factor) { buildWeeklyDigest(days, anchorDay, effortDisplayFactor = factor) }

    ScreenScaffold(title = uiString(R.string.l10n_weekly_digest_card_week_in_review_66d95a07)) {
        WeekReviewNavBar(digest = digest, weekOffset = weekOffset, minWeekOffset = minOffset, onStep = { delta ->
            weekOffset = (weekOffset + delta).coerceIn(minOffset, 0)
        })
        if (digest.isEmpty) {
            DataPendingNote(
                title = stringResource(R.string.trends_no_readings_this_week),
                body = stringResource(R.string.trends_no_readings_body),
            )
        } else {
            WeekStoryPager(digest = digest)
            WeekMetricTable(digest = digest, effortScale = effortScale)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                digest.sleepConsistencySD?.let { sd ->
                    Text(
                        uiString(R.string.l10n_weekly_digest_card_sleep_steadiness_rest_varied_fmt1_sd_44997f21, fmt1(sd)),
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                }
                Text(
                    uiString(R.string.l10n_weekly_digest_card_informational_only_not_medical_advice_593feb77),
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
        }
    }
}

// MARK: - Week nav (ported from TrendsScreen.kt's minWeekOffset/WeekNavBar, #710)

/**
 * The most-negative weekOffset allowed: the number of whole Mon-Sun weeks between the earliest
 * day we hold and this week. 0 when history is empty or unparseable. Byte-identical to the old
 * TrendsScreen.kt minWeekOffset.
 */
private fun minWeekOffset(days: List<DailyMetric>): Int {
    val earliest = days.firstOrNull()?.day ?: return 0
    val earliestMon = WeeklyDigestEngine.mondayOfWeek(earliest) ?: return 0
    val thisMon = WeeklyDigestEngine.mondayOfWeek(logicalDayKeyNow()) ?: return 0
    var off = 0
    var mon = thisMon
    while (mon > earliestMon && off > -520) {
        mon = WeeklyDigestEngine.addDays(mon, -7)
        off -= 1
    }
    return off
}

/** Prev/next chevrons + "This week"/"Last week"/"N weeks ago" + the date range and day count. */
@Composable
private fun WeekReviewNavBar(digest: WeeklyDigest, weekOffset: Int, minWeekOffset: Int, onStep: (Int) -> Unit) {
    val atOldest = weekOffset <= minWeekOffset
    val atNewest = weekOffset >= 0
    val label = when {
        weekOffset == 0 -> stringResource(R.string.trends_this_week)
        weekOffset == -1 -> stringResource(R.string.trends_last_week)
        else -> pluralStringResource(R.plurals.trends_weeks_ago, -weekOffset, -weekOffset)
    }
    val prevInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onStep(-1) },
            enabled = !atOldest,
            interactionSource = prevInteraction,
            modifier = Modifier.liquidPress(prevInteraction),
        ) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = stringResource(R.string.trends_previous_week),
                tint = if (atOldest) Palette.textTertiary else Palette.accent,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = NoopType.headline, color = Palette.textPrimary)
            Text(
                "${weekRangeLabel(digest)} · ${uiString(R.string.l10n_weekly_digest_card_digest_dayswithdata_7_days_182e6a18, digest.daysWithData)}",
                style = NoopType.footnote,
                color = Palette.textSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = { onStep(1) },
            enabled = !atNewest,
            interactionSource = nextInteraction,
            modifier = Modifier.liquidPress(nextInteraction),
        ) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.trends_next_week),
                tint = if (atNewest) Palette.textTertiary else Palette.accent,
            )
        }
    }
}

// MARK: - Top zone: swipeable story cards

private sealed class StoryCard {
    abstract val tint: Color
    data class Headline(val text: String, override val tint: Color) : StoryCard()
    data class Balance(val charge: Double, val effort: Double, val balance: BalanceRead, override val tint: Color) : StoryCard()
}

/** The colour a mover's own WoW chip would carry in the table below (mirrors chipTone in
 *  WeeklyDigestSupport.kt) — reused here so a headline card's wash matches its own delta chip. */
private fun moverTint(s: WeeklyMetricSummary): Color = when {
    s.isRoughComparison -> Palette.textTertiary
    s.wowGoodness == 1 -> Palette.statusPositive
    s.wowGoodness == -1 -> Palette.statusCritical
    else -> Palette.textTertiary
}

/** 2-3 cards depending on the week's data; a card is omitted, never shown blank. Each card's
 *  [StoryCard.tint] is driven by its own content — a headline by its mover's goodness (same
 *  colour as its WoW chip in the table), the balance card by its verdict — so the wash reacts
 *  to what the week actually looked like instead of a single fixed colour.
 *
 *  Recomputes the engine's own mover selection (WeeklyDigestEngine.MIN_DAYS_FOR_FOCUS /
 *  FOCUS_THRESHOLD, both public) so each headline card can be matched back to the metric that
 *  produced it — [WeeklyDigest.focalPoints] only exposes the rendered sentences, not their
 *  source. Also guards against the engine's own quirk where, when the week is OVERREACHING or
 *  UNDERLOADED, a focal-point line just restates [BalanceRead.sentence] — showing that as its
 *  own headline card would duplicate the Balance card right next to it, so that line is skipped. */
private fun storyCards(digest: WeeklyDigest): List<StoryCard> {
    val movers = digest.metrics
        .filter {
            it.weekOverWeek.current.n >= WeeklyDigestEngine.MIN_DAYS_FOR_FOCUS &&
                it.weekOverWeek.previous.n >= WeeklyDigestEngine.MIN_DAYS_FOR_FOCUS &&
                abs(it.normalisedMove) >= WeeklyDigestEngine.FOCUS_THRESHOLD
        }
        .sortedByDescending { abs(it.normalisedMove) }

    val restatesBalance = digest.balance == BalanceRead.OVERREACHING || digest.balance == BalanceRead.UNDERLOADED
    val cards = mutableListOf<StoryCard>()
    if (!(movers.isEmpty() && restatesBalance)) {
        digest.focalPoints.getOrNull(0)?.let { text ->
            cards.add(StoryCard.Headline(text, tint = movers.getOrNull(0)?.let(::moverTint) ?: Palette.accent))
        }
    }

    val charge = digest.summary(WeeklyMetric.CHARGE)?.thisWeek?.mean ?: 0.0
    val effort = digest.summary(WeeklyMetric.EFFORT)?.thisWeek?.mean ?: 0.0
    cards.add(StoryCard.Balance(charge, effort, digest.balance, tint = balanceVerdictColor(digest.balance)))

    if (!restatesBalance) {
        digest.focalPoints.getOrNull(1)?.let { text ->
            cards.add(StoryCard.Headline(text, tint = movers.getOrNull(1)?.let(::moverTint) ?: Palette.accent))
        }
    }
    return cards
}

private val STORY_CARD_HEIGHT = 172.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekStoryPager(digest: WeeklyDigest, modifier: Modifier = Modifier) {
    val cards = remember(digest) { storyCards(digest) }
    val pagerState = rememberPagerState(pageCount = { cards.size })
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Metrics.space10)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(STORY_CARD_HEIGHT),
            pageSpacing = Metrics.space10,
        ) { page ->
            StoryCardContent(cards[page])
        }
        if (cards.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(cards.size) { i ->
                    val active = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (active) 7.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (active) Palette.accent else Palette.textTertiary.copy(alpha = 0.4f)),
                    )
                }
            }
        }
    }
}

/** The story card's wash reacts to its own content — [StoryCard.tint] — so a card reporting a
 *  good move glows the same green as its table chip, a bad move glows rose, and the balance
 *  card glows its own verdict colour (amber/OVERREACHING, cyan/UNDERLOADED, green/BALANCED). */
@Composable
private fun StoryCardContent(card: StoryCard) {
    val tint = card.tint
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(Metrics.cardRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tint.copy(alpha = if (Palette.isLight) 0.28f else 0.34f),
                        Palette.surfaceRaised,
                    ),
                ),
            )
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(Metrics.cardRadius))
            .padding(Metrics.cardPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        when (card) {
            is StoryCard.Headline -> {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(card.text, style = NoopType.title2, color = Palette.textPrimary)
            }
            is StoryCard.Balance -> {
                BalanceBar(charge = card.charge, effort = card.effort, balance = card.balance)
                Spacer(Modifier.height(12.dp))
                Text(card.balance.sentence, style = NoopType.subhead, color = Palette.textSecondary)
            }
        }
    }
}

private fun balanceVerdictColor(balance: BalanceRead): Color = when (balance) {
    BalanceRead.OVERREACHING -> Palette.statusWarning
    BalanceRead.UNDERLOADED -> Palette.metricCyan
    BalanceRead.BALANCED -> Palette.statusPositive
    BalanceRead.INSUFFICIENT -> Palette.textTertiary
}

/** Verdict label + a two-tone Charge/Effort track. Shared visual language between the story card
 *  (full size) and could anchor a compact table header row in future (compact size). */
@Composable
fun BalanceBar(
    charge: Double,
    effort: Double,
    balance: BalanceRead,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val total = (charge + effort).coerceAtLeast(1.0)
    val chargeFrac = (charge / total).toFloat().coerceIn(0.04f, 0.96f)
    val verdictColor = balanceVerdictColor(balance)
    val verdictLabel = when (balance) {
        BalanceRead.OVERREACHING -> "OVERREACHING"
        BalanceRead.UNDERLOADED -> "UNDERLOADED"
        BalanceRead.BALANCED -> "BALANCED"
        BalanceRead.INSUFFICIENT -> "INSUFFICIENT DATA"
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Overline("Effort vs Charge")
            Text(verdictLabel, style = NoopType.captionNumber, color = verdictColor)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 6.dp else 8.dp)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            Box(modifier = Modifier.weight(chargeFrac).fillMaxHeight().background(Palette.chargeColor))
            Box(modifier = Modifier.weight(1f - chargeFrac).fillMaxHeight().background(Palette.effortColor))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Charge ${charge.roundToInt()}", style = NoopType.footnote, color = Palette.textSecondary)
            Text("Effort ${effort.roundToInt()}", style = NoopType.footnote, color = Palette.textSecondary)
        }
    }
}

// MARK: - Bottom zone: the dense table

private data class MetricIconSpec(val icon: ImageVector, val tint: Color)

private fun metricIconSpec(metric: WeeklyMetric): MetricIconSpec = when (metric) {
    WeeklyMetric.CHARGE -> MetricIconSpec(Icons.Filled.Bolt, Palette.chargeColor)
    WeeklyMetric.EFFORT -> MetricIconSpec(Icons.AutoMirrored.Filled.DirectionsRun, Palette.effortColor)
    WeeklyMetric.REST -> MetricIconSpec(Icons.Filled.Bedtime, Palette.restColor)
    WeeklyMetric.HRV -> MetricIconSpec(Icons.Filled.MonitorHeart, Palette.metricCyan)
    WeeklyMetric.RHR -> MetricIconSpec(Icons.Filled.Favorite, Palette.metricRose)
}

/** Formats a raw stored mean (0-100 scale for Charge/Effort/Rest) for one of the table's THIS
 *  WK / LAST WK / 4-WK columns. `null` (no data for that slice) renders "—". No "/100" or "/21"
 *  denominator here (unlike meanText's card read-out) — four numeric columns per row leaves no
 *  room for it, and the scale is already established elsewhere (Today's Effort tile). */
private fun formatMetricValue(metric: WeeklyMetric, mean: Double?, effortScale: EffortScale): String {
    if (mean == null) return "—"
    if (metric == WeeklyMetric.EFFORT) {
        return UnitFormatter.effortDisplay(mean, effortScale)
    }
    val v = mean.roundToInt()
    return if (metric.unit.isEmpty()) "$v" else "$v ${metric.unit}"
}

@Composable
private fun WeekMetricTable(digest: WeeklyDigest, effortScale: EffortScale, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = Metrics.space8)) {
            Spacer(Modifier.width(108.dp))
            TableHeaderCell("THIS WK")
            TableHeaderCell("LAST WK")
            TableHeaderCell("4-WK")
            TableHeaderCell("WOW")
        }
        HorizontalDivider(color = Palette.hairline)
        DISPLAY_ORDER.forEach { metric ->
            val s = digest.summary(metric) ?: return@forEach
            WeekMetricRow(s, effortScale)
        }
    }
}

@Composable
private fun RowScope.TableHeaderCell(label: String) {
    Text(
        label,
        style = NoopType.footnote,
        color = Palette.textTertiary,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun WeekMetricRow(s: WeeklyMetricSummary, effortScale: EffortScale) {
    val spec = metricIconSpec(s.metric)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = rowAccessibility(s, effortScale) }
            .padding(vertical = Metrics.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(108.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(spec.icon, contentDescription = null, tint = spec.tint, modifier = Modifier.size(14.dp))
            Text(
                s.metric.label,
                style = NoopType.subhead,
                color = Palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatMetricValue(s.metric, if (s.thisWeek.n > 0) s.thisWeek.mean else null, effortScale),
            style = NoopType.bodyNumber,
            color = Palette.textPrimary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatMetricValue(s.metric, if (s.weekOverWeek.previous.n > 0) s.weekOverWeek.previous.mean else null, effortScale),
            style = NoopType.subhead,
            color = Palette.textSecondary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatMetricValue(s.metric, s.baselineMean, effortScale),
            style = NoopType.subhead,
            color = Palette.textSecondary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            DeltaChip(s)
        }
    }
}
