package com.noop.ui

import com.noop.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.noop.data.WhoopRepository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.noop.analytics.StepsCalibrationPoint
import com.noop.analytics.StepsCalibrationPointStore
import com.noop.analytics.StepsCalibrationSource
import com.noop.analytics.StepsEstimateEngine
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// MARK: - StepsCalibrationScreen (ported from Strand/Screens/SettingsView.swift StepsCalibrationSheet)
//
// WHOOP 4.0 steps-ESTIMATE calibration. A 4.0 sends no step count over BLE, so NOOP estimates steps
// from the strap's daily MOTION VOLUME, calibrated per-user against the phone's real step count. This
// screen is read-only over the engine's fit (it never recomputes the headline): an honest explainer,
// the current calibration, a recent estimated-vs-phone accuracy table, and a manual coefficient
// override with a live preview. Mirrors the macOS StepsCalibrationSheet card-for-card and shares its
// confidence wording via [StepsCalibrationFormat]. Presented in a full-screen Dialog from Settings →
// Profile → "Steps estimate".

/** Shared formatters for the steps-estimate calibration UI — kept apart so the Profile summary row and
 *  this screen agree on the confidence wording. Mirrors the macOS `StepsCalibrationFormat`. */
object StepsCalibrationFormat {
    /** A 0–1 confidence as Low / Medium / High. Thirds: < 0.34 Low, < 0.67 Medium, else High. A manual
     *  coefficient is confidence 1.0 → "High". */
    fun confidenceLabel(confidence: Double): String = when {
        confidence < 0.34 -> "Low"
        confidence < 0.67 -> "Medium"
        else -> "High"
    }
}

/** One recent day's estimated-vs-phone steps comparison row for the accuracy table. */
private data class StepsComparisonRow(val day: String, val estimated: Int, val actual: Int) {
    /** Signed error of the estimate vs the phone count, as a percentage. */
    val errorPct: Double get() = if (actual > 0) (estimated - actual).toDouble() / actual * 100 else 0.0
}

@Composable
fun StepsCalibrationScreen(
    vm: AppViewModel,
    profile: ProfileStore,
    onProfileChanged: () -> Unit,
    onClose: () -> Unit,
) {
    val scroll = rememberScrollState()

    // Recent days that have BOTH an estimate (reconstructed) and a phone count — the accuracy table.
    var comparison by remember { mutableStateOf<List<StepsComparisonRow>>(emptyList()) }
    // A representative recent motion volume (median of the days we measured), seeding the live preview.
    var sampleMotion by remember { mutableStateOf<Double?>(null) }
    // Flips true once the load pass has run, so the "no motion synced" note (#37) doesn't flash on first frame.
    var loaded by remember { mutableStateOf(false) }
    // Nested full-screen Dialog for the "calibrate with a walk" flow (see StepsWalkCalibrationScreen
    // below) — same idiom Settings uses to present this screen itself.
    var showWalk by remember { mutableStateOf(false) }

    // The stepper's ceiling anchors to whatever's in force with generous headroom, so a nudge either way
    // stays reachable; a floor keeps it usable before any fit. Mirrors the macOS sliderMax.
    val stepperMax = maxOf(profile.stepsCalibrationCoefficient, profile.stepsManualCoefficient, 50.0) * 2

    // Build the comparison table + a typical-day motion, once. The engine stores `steps_est` ONLY for
    // strap-only days (a phone-covered day uses the phone's real count), so an estimate and a phone
    // count never co-exist in storage. To still SHOW how close the estimate is, we reconstruct what the
    // estimate WOULD have been on recent phone-covered days: read each day's motion the same way the
    // engine does (gravity over [localMidnight, +24h)) and run the public StepsEstimateEngine with the
    // live calibration. Reuses the engine, never invents a number, needs no extra storage.
    LaunchedEffect(Unit) {
        loaded = true
        val coeff = if (profile.stepsManualCoefficient > 0) {
            profile.stepsManualCoefficient
        } else {
            profile.stepsCalibrationCoefficient
        }
        if (coeff <= 0) return@LaunchedEffect

        // Phone step counts come from apple-health AND, for HC-only users, Health Connect (#37). Both are
        // stored in appleDaily under their own source; union them with apple-health winning per day.
        val stepsByDay = LinkedHashMap<String, Int>()
        for (row in vm.repo.appleDaily(WhoopRepository.APPLE_HEALTH_SOURCE, "0000-01-01", "9999-12-31")) {
            row.steps?.takeIf { it > 0 }?.let { stepsByDay[row.day] = it }
        }
        for (row in vm.repo.appleDaily(WhoopRepository.HEALTH_CONNECT_SOURCE, "0000-01-01", "9999-12-31")) {
            row.steps?.takeIf { it > 0 }?.let { stepsByDay.putIfAbsent(row.day, it) }
        }
        val phoneDays = stepsByDay.entries
            .map { it.key to it.value }
            .sortedByDescending { it.first }

        val cal = StepsEstimateEngine.Calibration(
            coefficient = coeff,
            sampleDays = profile.stepsCalibrationSampleDays,
            confidence = profile.stepsCalibrationConfidence,
            manual = profile.stepsManualCoefficient > 0,
        )
        val rows = ArrayList<StepsComparisonRow>()
        val motions = ArrayList<Double>()
        for ((day, phone) in phoneDays.take(10)) {           // scan extra to fill 7 after motion gaps
            val mid = runCatching {
                LocalDate.parse(day).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
            }.getOrNull() ?: continue
            val grav = vm.repo.gravitySamples("my-whoop", mid, mid + 86_400 - 1)
            val motion = StepsEstimateEngine.dayMotionIntensity(grav)
            val est = StepsEstimateEngine.estimate(motion, cal) ?: continue
            motions.add(motion)
            rows.add(StepsComparisonRow(day, est, phone))
            if (rows.size >= 7) break
        }
        comparison = rows
        if (motions.isNotEmpty()) sampleMotion = motions.sorted()[motions.size / 2]
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Palette.surfaceBase) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(onClose)
            Hairline()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
            ) {
                ExplainerCard()
                if (loaded && sampleMotion == null) NoMotionNote()
                // #589: the matched-day count (phone-counted days we could pair with strap motion — the
                // engine's "usable overlapping days") drives the "Need N more days…" countdown. In the
                // not-calibrated state the comparison build early-returns on coeff <= 0, so this is 0 and
                // the headline reads the full MIN_CALIBRATION_DAYS — exactly the Swift behaviour.
                CurrentFitCard(profile, matchedDays = comparison.size)
                ComparisonCard(comparison)
                ManualAdjustCard(
                    profile = profile,
                    stepperMax = stepperMax,
                    sampleMotion = sampleMotion,
                    onProfileChanged = onProfileChanged,
                )
                WalkCalibrationEntryCard(onStart = { showWalk = true })
            }
            Hairline()
            Footer(onClose)
        }
    }

    if (showWalk) {
        Dialog(
            onDismissRequest = { showWalk = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            StepsWalkCalibrationScreen(
                vm = vm,
                profile = profile,
                onProfileChanged = onProfileChanged,
                onClose = { showWalk = false },
            )
        }
    }
}

// MARK: - Header / footer

@Composable
private fun Header(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Overline("Steps estimate", color = Palette.textTertiary)
            Text(uiString(R.string.l10n_steps_calibration_screen_calibrate_your_steps_38b4e814), style = NoopType.display(26f), color = Palette.textPrimary)
            Text(uiString(R.string.l10n_steps_calibration_screen_whoop_4_0_motion_steps_a63239dc), style = NoopType.caption, color = Palette.textSecondary)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, contentDescription = uiString(R.string.l10n_steps_calibration_screen_close_bbfa773e), tint = Palette.textTertiary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Footer(onClose: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.surfaceBase),
        ) {
            Text(uiString(R.string.l10n_steps_calibration_screen_done_e9b450d1), modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
}

@Composable
private fun Hairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Palette.hairline),
    )
}

// MARK: - Cards

/** The honest "it's an estimate, not a step counter" framing — reused verbatim from the engine doc. */
@Composable
private fun ExplainerCard() {
    NoopCard(padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(20.dp))
                Text(uiString(R.string.l10n_steps_calibration_screen_how_this_works_b895a8c3), style = NoopType.headline, color = Palette.textPrimary)
            }
            Text(
                uiString(R.string.l10n_steps_calibration_screen_noop_estimates_your_steps_from_your_d569bc31),
                style = NoopType.subhead,
                color = Palette.textSecondary,
            )
            Text(
                uiString(R.string.l10n_steps_calibration_screen_on_the_days_your_phone_also_2f65a14c) +
                    "steps, then applies that to the strap-only days. The more matching days it has, the " +
                    "more it trusts the estimate.",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
        }
    }
}

/** Shown when the strap has banked NO motion yet (sampleMotion == null) — the real reason a fresh
 *  WHOOP 4.0 reads zero steps (#37 bringiton321). Steps come from the strap's synced motion history,
 *  so without a backfill there's nothing to estimate from — calibration can't help until it syncs. */
@Composable
private fun NoMotionNote() {
    NoopCard(padding = 20.dp, tint = Palette.metricAmber) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.SyncProblem, contentDescription = null, tint = Palette.metricAmber, modifier = Modifier.size(20.dp))
                Text(uiString(R.string.l10n_steps_calibration_screen_no_motion_synced_yet_65106670), style = NoopType.headline, color = Palette.textPrimary)
            }
            Text(
                uiString(R.string.l10n_steps_calibration_screen_we_re_not_seeing_any_motion_6ac8e092) +
                    "banked motion history, so your strap needs to sync that history before NOOP has " +
                    "anything to count.",
                style = NoopType.subhead,
                color = Palette.textSecondary,
            )
            Text(
                uiString(R.string.l10n_steps_calibration_screen_open_noop_near_your_strap_and_e08ddd6d) +
                    "first run). Once a day or two of motion lands, your step estimate and the calibration " +
                    "below will start to fill in.",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
        }
    }
}

/** The current calibration read-out: coefficient, sample days, and a Low/Medium/High confidence — or
 *  an honest "what we still need" prompt when nothing's fit and no manual value is set. */
@Composable
private fun CurrentFitCard(profile: ProfileStore, matchedDays: Int) {
    NoopCard(padding = 20.dp, tint = Palette.accent) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline("Current calibration")
            if (profile.stepsCalibrationCoefficient > 0 || profile.stepsManualCoefficient > 0) {
                val coeff = if (profile.stepsManualCoefficient > 0) {
                    profile.stepsManualCoefficient
                } else {
                    profile.stepsCalibrationCoefficient
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(String.format(Locale.US, "%.1f", coeff), style = NoopType.number(30f), color = Palette.accent)
                    Text(uiString(R.string.l10n_steps_calibration_screen_steps_per_motion_unit_a2c2ac56), style = NoopType.footnote, color = Palette.textTertiary, modifier = Modifier.padding(bottom = 4.dp))
                }
                if (profile.stepsManualCoefficient > 0) {
                    StatLine("Source", "Manual (you set this by hand)")
                } else {
                    val days = profile.stepsCalibrationSampleDays
                    StatLine("Fitted from", "$days day${if (days == 1) "" else "s"} your phone also counted")
                    StatLine(
                        "Confidence",
                        "${StepsCalibrationFormat.confidenceLabel(profile.stepsCalibrationConfidence)} · " +
                            "${(profile.stepsCalibrationConfidence * 100).roundToInt()}%",
                    )
                }
            } else {
                Text(uiString(R.string.l10n_steps_calibration_screen_not_calibrated_yet_30abe0d0), style = NoopType.bodyNumber, color = Palette.textPrimary)
                // #589: a concrete countdown instead of a vague "a few days". Headline comes straight from
                // the engine's NeedsMoreDays state so the wording matches the Today steps tile + the Swift card.
                Text(
                    StepsEstimateEngine.CalibrationStatus
                        .NeedsMoreDays(have = matchedDays, need = StepsEstimateEngine.MIN_CALIBRATION_DAYS)
                        .headline,
                    style = NoopType.bodyNumber,
                    color = Palette.accent,
                )
                Text(
                    uiString(R.string.l10n_steps_calibration_screen_these_are_the_days_where_your_ae5c9c2c),
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
        }
    }
}

/** The accuracy table: recent days with BOTH an estimate and a phone count, side by side, so the user
 *  can SEE how close the estimate runs. Empty until enough both-have days exist. */
@Composable
private fun ComparisonCard(rows: List<StepsComparisonRow>) {
    NoopCard(padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline("Estimated vs your phone")
            if (rows.isEmpty()) {
                Text(
                    uiString(R.string.l10n_steps_calibration_screen_no_days_yet_where_both_noop_71d6005b) +
                        "few days alongside the strap, they'll appear here so you can see how close the " +
                        "estimate is.",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(uiString(R.string.l10n_steps_calibration_screen_day_987b9ced), style = NoopType.caption, color = Palette.textTertiary, modifier = Modifier.weight(1f))
                    Text(uiString(R.string.l10n_steps_calibration_screen_est_18b405fb), style = NoopType.caption, color = Palette.textTertiary, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
                    Text(uiString(R.string.l10n_steps_calibration_screen_phone_77064d52), style = NoopType.caption, color = Palette.textTertiary, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
                    Text("Δ", style = NoopType.caption, color = Palette.textTertiary, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
                }
                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription =
                                uiString(R.string.l10n_steps_calibration_screen_shortday_row_day_estimated_row_estimated_f2d71597, shortDay(row.day), row.estimated, row.actual) +
                                    "steps, ${row.errorPct.roundToInt()} percent difference"
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(shortDay(row.day), style = NoopType.footnote, color = Palette.textSecondary, modifier = Modifier.weight(1f))
                        Text(grouped(row.estimated), style = NoopType.captionNumber, color = Palette.textPrimary, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
                        Text(grouped(row.actual), style = NoopType.captionNumber, color = Palette.textPrimary, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
                        Text(
                            String.format(Locale.US, "%+.0f%%", row.errorPct),
                            style = NoopType.captionNumber,
                            color = if (abs(row.errorPct) <= 15) Palette.metricCyan else Palette.statusWarning,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(52.dp),
                        )
                    }
                }
                Text(
                    uiString(R.string.l10n_steps_calibration_screen_these_days_are_excluded_from_the_6bedabbf),
                    style = NoopType.caption,
                    color = Palette.textTertiary,
                )
            }
        }
    }
}

/** One step of the manual coefficient stepper (#698): a slider spanning the full 0..[stepperMax] range
 *  made a specific one-decimal value (e.g. nudging an auto 1.4 down to 1.2) practically undraggable —
 *  the whole usable precision was compressed into a handful of drag pixels. A fixed 0.1 tick, like the
 *  Profile card's age/weight steppers, makes that exact. */
private const val STEPS_COEFFICIENT_STEP = 0.1

/** Manual override: a stepper (mirrors the Profile card's age/weight [StepperField]s) bound directly to
 *  [ProfileStore.stepsManualCoefficient], with a live preview of what a typical recent day would
 *  estimate at the current setting. 0 means auto-fit; stepping down to 0 returns to it. Nudges start
 *  from whichever value is currently EFFECTIVE (the auto fit, until first overridden), so reaching a
 *  nearby value like 1.2 from an auto-fitted 1.4 is two taps, not a drag from zero (#698). */
@Composable
private fun ManualAdjustCard(
    profile: ProfileStore,
    stepperMax: Double,
    sampleMotion: Double?,
    onProfileChanged: () -> Unit,
) {
    val manual = profile.stepsManualCoefficient
    val effective = if (manual > 0) manual else profile.stepsCalibrationCoefficient

    fun step(delta: Double) {
        val next = (Math.round((effective + delta) * 10) / 10.0).coerceIn(0.0, stepperMax)
        profile.stepsManualCoefficient = next
        onProfileChanged()
    }

    NoopCard(padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline("Adjust manually")
            Text(
                uiString(R.string.l10n_steps_calibration_screen_override_the_automatic_fit_with_your_36a7b6fa) +
                    "has no step history to learn from, or the estimate runs consistently high or low. " +
                    "Step all the way down to return to auto.",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (manual > 0) String.format(Locale.US, "%.1f", manual) else "Auto",
                    style = NoopType.number(24f),
                    color = if (manual > 0) Palette.accent else Palette.textSecondary,
                )
                Text(
                    if (manual > 0) "steps / motion unit" else "fit from your phone",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                StepperField(
                    value = if (manual > 0) String.format(Locale.US, "%.1f", manual) else "Auto",
                    accessibility = if (manual > 0) {
                        String.format(Locale.US, "Manual steps coefficient, %.1f steps per motion unit", manual)
                    } else {
                        "Manual steps coefficient, automatic"
                    },
                    onMinus = { step(-STEPS_COEFFICIENT_STEP) },
                    onPlus = { step(STEPS_COEFFICIENT_STEP) },
                )
            }
            // Live preview: a typical recent day re-estimated at the effective (manual or auto) coefficient.
            if (sampleMotion != null && effective > 0) {
                val preview = (sampleMotion * effective).roundToInt()
                StatLine(
                    "A typical recent day",
                    "≈ ${grouped(preview)} steps${if (manual > 0) " at this setting" else " (auto)"}",
                )
            }
            if (manual > 0) {
                Text(
                    uiString(R.string.l10n_steps_calibration_screen_takes_effect_on_the_next_analytics_13205327),
                    style = NoopType.caption,
                    color = Palette.textTertiary,
                )
            }
        }
    }
}

/** Entry point into the "calibrate with a walk" flow below — a deliberate alternative to waiting on
 *  MIN_CALIBRATION_DAYS of incidental phone-step overlap. */
@Composable
private fun WalkCalibrationEntryCard(onStart: () -> Unit) {
    NoopCard(padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline("No phone step history yet?")
            Text(
                "Walk a step count you can count exactly, and NOOP will read back the strap's own motion " +
                    "for that window. Three or more walks give you a rough starter calibration without " +
                    "any phone step data — used only until real phone-counted days are available, which " +
                    "are always more accurate.",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.surfaceInset, contentColor = Palette.textPrimary),
            ) {
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Calibrate with a walk")
            }
        }
    }
}

// ================================================================================================
// MARK: - Calibrate with a walk (Android-only; no Swift/macOS/iOS counterpart)
// ================================================================================================
//
// An alternative bootstrap for StepsEstimateEngine's calibration when there's no phone step history
// to fit against yet (or you just want a faster, more deliberate fit than waiting on incidental
// day-to-day overlap): walk a KNOWN step count with the strap on, read back the strap's OWN motion
// for exactly that window, and solve `k = steps / motion` directly.
//
// WHY THIS ISN'T INSTANT. A WHOOP 4.0's gravity stream is HISTORICAL-OFFLOAD ONLY (see
// HistoricalStreams.kt / StreamPersistence.kt) — there is no live gravity feed while connected, so
// after the walk we still have to trigger a sync ([AppViewModel.syncNow]) and wait for the strap to
// hand over the type-47 records covering [start, end] before anything can be measured.
//
// ONE-SHOT, NOT AVERAGED (by design — see the calibration-scope decision this was built to): a single
// walk sets [ProfileStore.stepsManualCoefficient] outright, the SAME field the "Adjust manually"
// stepper above writes. Nothing here touches the phone-based auto-fit; stepping the manual value back
// to 0 in that stepper returns to auto-fit exactly as before this screen existed.

private const val WALK_DEVICE_ID = "my-whoop"
private const val WALK_DEFAULT_STEPS = 100
private const val WALK_STEP_INCREMENT = 10
private const val WALK_MIN_STEPS = 10
private const val WALK_MAX_STEPS = 2_000
private const val WALK_SYNC_POLL_MS = 1_500L
private const val WALK_SYNC_TIMEOUT_MS = 60_000L

private fun walkNowSeconds(): Long = System.currentTimeMillis() / 1000L

/** The walk flow's state machine. Each phase owns exactly the data the next step needs — no shared
 *  mutable scratch state across phases. */
private sealed interface WalkPhase {
    data object Setup : WalkPhase
    data class Walking(val startTs: Long) : WalkPhase
    data class Confirm(val startTs: Long, val endTs: Long) : WalkPhase
    data class Syncing(val startTs: Long, val endTs: Long, val steps: Int) : WalkPhase
    data class Result(
        val startTs: Long,
        val endTs: Long,
        val steps: Int,
        val motion: Double,
        val coefficient: Double,
    ) : WalkPhase
    data class Failed(val steps: Int, val startTs: Long, val endTs: Long, val message: String) : WalkPhase
}

/** Full-screen dialog content for the walk-calibration flow. Presented from [WalkCalibrationEntryCard]
 *  above, same nested-Dialog idiom [StepsCalibrationScreen] itself uses from Settings. */
@Composable
fun StepsWalkCalibrationScreen(
    vm: AppViewModel,
    profile: ProfileStore,
    onProfileChanged: () -> Unit,
    onClose: () -> Unit,
) {
    // Resume an in-progress walk if the app was backgrounded/killed mid-walk: a saved start with no
    // known end lands on Confirm with "now" as the end. Honest for a short walk; a long-forgotten one
    // is easy to discard and redo from Setup.
    var phase by remember {
        mutableStateOf<WalkPhase>(
            profile.stepsWalkStartTs.takeIf { it > 0 }?.let { start -> WalkPhase.Confirm(start, walkNowSeconds()) }
                ?: WalkPhase.Setup,
        )
    }
    var plannedSteps by remember {
        mutableStateOf(profile.stepsWalkPlannedCount.takeIf { it > 0 } ?: WALK_DEFAULT_STEPS)
    }
    val context = LocalContext.current

    fun clearSavedWalk() {
        profile.stepsWalkStartTs = 0
        profile.stepsWalkPlannedCount = 0
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Palette.surfaceBase) {
        Column(modifier = Modifier.fillMaxSize()) {
            WalkHeader(onClose = { clearSavedWalk(); onClose() })
            Hairline()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
            ) {
                when (val p = phase) {
                    is WalkPhase.Setup -> WalkSetupCard(
                        steps = plannedSteps,
                        onStepsChange = { plannedSteps = it },
                        onStart = {
                            val start = walkNowSeconds()
                            profile.stepsWalkStartTs = start
                            profile.stepsWalkPlannedCount = plannedSteps
                            phase = WalkPhase.Walking(start)
                        },
                    )
                    is WalkPhase.Walking -> WalkInProgressCard(
                        startTs = p.startTs,
                        onDone = { phase = WalkPhase.Confirm(p.startTs, walkNowSeconds()) },
                        onCancel = { clearSavedWalk(); phase = WalkPhase.Setup },
                    )
                    is WalkPhase.Confirm -> WalkConfirmCard(
                        defaultSteps = plannedSteps,
                        durationSeconds = (p.endTs - p.startTs).coerceAtLeast(0),
                        onConfirm = { steps -> phase = WalkPhase.Syncing(p.startTs, p.endTs, steps) },
                        onCancel = { clearSavedWalk(); phase = WalkPhase.Setup },
                    )
                    is WalkPhase.Syncing -> {
                        WalkSyncingCard()
                        LaunchedEffect(p) { phase = runWalkSync(vm, p.startTs, p.endTs, p.steps) }
                    }
                    is WalkPhase.Result -> WalkResultCard(
                        steps = p.steps,
                        motion = p.motion,
                        coefficient = p.coefficient,
                        onSave = {
                            // Adds ONE data point to the shared calibration pool (StepsCalibrationPointStore)
                            // — the SAME pool Health-Connect-windowed points feed — rather than overwriting
                            // the manual override outright. IntelligenceEngine's weighted-median fit then
                            // folds this walk in alongside every other point, so one walk can't dominate any
                            // more than one bad day already can't. The "Adjust manually" stepper above still
                            // exists as a separate, literal override for anyone who wants to bypass fitting.
                            StepsCalibrationPointStore.addAll(
                                context,
                                listOf(
                                    StepsCalibrationPoint(
                                        windowStart = p.startTs,
                                        windowEnd = p.endTs,
                                        motion = p.motion,
                                        steps = p.steps.toDouble(),
                                        source = StepsCalibrationSource.WALK,
                                    ),
                                ),
                            )
                            clearSavedWalk()
                            // The periodic loop's HR-fingerprint gate wouldn't otherwise notice this changed
                            // (no new HR data arrived), so force the fit to reflect it right away.
                            vm.rescoreStepsCalibration()
                            onProfileChanged()
                            onClose()
                        },
                        onDiscard = { clearSavedWalk(); phase = WalkPhase.Setup },
                    )
                    is WalkPhase.Failed -> WalkFailedCard(
                        message = p.message,
                        onRetry = { phase = WalkPhase.Syncing(p.startTs, p.endTs, p.steps) },
                        onCancel = { clearSavedWalk(); phase = WalkPhase.Setup },
                    )
                }
            }
        }
    }
}

/** Trigger a manual sync and poll for gravity data covering [startTs, endTs] up to a timeout.
 *  Suspends the caller (called from a [LaunchedEffect]); never throws. */
private suspend fun runWalkSync(vm: AppViewModel, startTs: Long, endTs: Long, steps: Int): WalkPhase {
    if (!vm.live.value.connected) {
        return WalkPhase.Failed(
            steps, startTs, endTs,
            "Your WHOOP isn't connected right now. Reconnect it, then tap Retry.",
        )
    }
    vm.syncNow()
    val deadline = System.currentTimeMillis() + WALK_SYNC_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
        val grav = runCatching { vm.repo.gravitySamples(WALK_DEVICE_ID, startTs, endTs) }
            .getOrDefault(emptyList())
            .sortedBy { it.ts }
        val motion = StepsEstimateEngine.dayMotionIntensity(grav)
        if (grav.size >= 2 && motion >= StepsEstimateEngine.MIN_MOTION_FOR_FIT) {
            return WalkPhase.Result(startTs, endTs, steps, motion, steps / motion)
        }
        delay(WALK_SYNC_POLL_MS)
    }
    return WalkPhase.Failed(
        steps, startTs, endTs,
        "We didn't see enough motion data for that time window yet. Make sure your WHOOP has synced " +
            "(check the Devices screen), then tap Retry.",
    )
}

@Composable
private fun WalkHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Overline("Steps estimate", color = Palette.textTertiary)
            Text("Calibrate with a walk", style = NoopType.display(26f), color = Palette.textPrimary)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Palette.textTertiary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun WalkSetupCard(steps: Int, onStepsChange: (Int) -> Unit, onStart: () -> Unit) {
    NoopCard(padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline("Walk a known number of steps")
            Text(
                "Wear your WHOOP, then walk a step count you can count accurately (a treadmill display " +
                    "or your phone's own pedometer works well). Try to keep other movement to a minimum " +
                    "for the rest of this window, so the motion NOOP measures is mostly your walk.",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$steps", style = NoopType.number(24f), color = Palette.textPrimary)
                Text(
                    "steps planned",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                StepperField(
                    value = "$steps",
                    accessibility = "Planned step count, $steps",
                    onMinus = { onStepsChange((steps - WALK_STEP_INCREMENT).coerceAtLeast(WALK_MIN_STEPS)) },
                    onPlus = { onStepsChange((steps + WALK_STEP_INCREMENT).coerceAtMost(WALK_MAX_STEPS)) },
                )
            }
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.surfaceBase),
            ) { Text("Start walk") }
        }
    }
}

@Composable
private fun WalkInProgressCard(startTs: Long, onDone: () -> Unit, onCancel: () -> Unit) {
    var elapsed by remember { mutableStateOf((walkNowSeconds() - startTs).coerceAtLeast(0)) }
    LaunchedEffect(startTs) {
        while (true) {
            elapsed = (walkNowSeconds() - startTs).coerceAtLeast(0)
            delay(1_000)
        }
    }
    NoopCard(padding = 20.dp, tint = Palette.accent) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline("Walking")
            Text(
                "Take your steps now. Tap \"I'm done\" the moment you finish — the window between " +
                    "Start and Done is what NOOP will measure.",
                style = NoopType.subhead,
                color = Palette.textSecondary,
            )
            Text(
                String.format(Locale.US, "%d:%02d elapsed", elapsed / 60, elapsed % 60),
                style = NoopType.number(28f),
                color = Palette.accent,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.surfaceBase),
                ) { Text("I'm done") }
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.surfaceInset, contentColor = Palette.textSecondary),
                ) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun WalkConfirmCard(
    defaultSteps: Int,
    durationSeconds: Long,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    var steps by remember { mutableStateOf(defaultSteps) }
    NoopCard(padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline("How many steps did you actually walk?")
            StatLine(
                "Walk duration",
                String.format(Locale.US, "%d:%02d", durationSeconds / 60, durationSeconds % 60),
            )
            Text(
                "Enter the real count if it wasn't exactly what you planned — accuracy here matters " +
                    "more than anything else in this flow.",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$steps", style = NoopType.number(24f), color = Palette.textPrimary)
                Text(
                    "steps",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                StepperField(
                    value = "$steps",
                    accessibility = "Actual step count, $steps",
                    onMinus = { steps = (steps - WALK_STEP_INCREMENT).coerceAtLeast(WALK_MIN_STEPS) },
                    onPlus = { steps = (steps + WALK_STEP_INCREMENT).coerceAtMost(WALK_MAX_STEPS) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onConfirm(steps) },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.surfaceBase),
                ) { Text("Calculate") }
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.surfaceInset, contentColor = Palette.textSecondary),
                ) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun WalkSyncingCard() {
    NoopCard(padding = 20.dp) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Overline("Syncing your WHOOP")
            CircularProgressIndicator(color = Palette.accent)
            Text(
                "Waiting for your strap to hand over the motion data for your walk. This can take a " +
                    "little while — keep the app open and your WHOOP nearby.",
                style = NoopType.footnote,
                color = Palette.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WalkResultCard(
    steps: Int,
    motion: Double,
    coefficient: Double,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    NoopCard(padding = 20.dp, tint = Palette.accent) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline("Walk measured")
            StatLine("Steps you walked", grouped(steps))
            StatLine("Strap motion measured", String.format(Locale.US, "%.2f", motion))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(String.format(Locale.US, "%.1f", coefficient), style = NoopType.number(30f), color = Palette.accent)
                Text(
                    "steps per motion unit — this walk alone",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                "This adds one data point to your starter calibration — it won't overwrite anything by " +
                    "itself. Do at least three walks on different days before NOOP will fit from them.\n\n" +
                    "A starter calibration is measured while you are walking, but it gets applied to your " +
                    "whole day, which also contains movement that isn't stepping — so expect it to read " +
                    "high, and expect it to be labelled low confidence. Once you have a few days where " +
                    "your phone counted steps alongside the strap, NOOP switches to that instead, and " +
                    "these walks stop being used.",
                style = NoopType.footnote,
                color = Palette.textTertiary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.surfaceBase),
                ) { Text("Add to calibration") }
                Button(
                    onClick = onDiscard,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.surfaceInset, contentColor = Palette.textSecondary),
                ) { Text("Discard") }
            }
        }
    }
}

@Composable
private fun WalkFailedCard(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    NoopCard(padding = 20.dp, tint = Palette.statusWarning) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline("Couldn't calibrate from that walk")
            Text(message, style = NoopType.subhead, color = Palette.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.surfaceBase),
                ) { Text("Retry") }
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.surfaceInset, contentColor = Palette.textSecondary),
                ) { Text("Cancel") }
            }
        }
    }
}

/** A small "label … value" line shared by the fit + preview cards. */
@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, style = NoopType.footnote, color = Palette.textTertiary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = NoopType.footnote, color = Palette.textSecondary, textAlign = TextAlign.End)
    }
}

// MARK: - Formatting

private fun grouped(n: Int): String =
    if (abs(n) >= 1000) String.format(Locale.US, "%,d", n) else "$n"

/** "yyyy-MM-dd" → "EEE d MMM" for the table's day column. */
private fun shortDay(key: String): String = runCatching {
    LocalDate.parse(key).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US))
}.getOrDefault(key)
