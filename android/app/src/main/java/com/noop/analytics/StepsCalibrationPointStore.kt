package com.noop.analytics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// ================================================================================================
// MARK: - Steps calibration point store (Android-only; no Swift/macOS/iOS counterpart)
// ================================================================================================
//
// Persists BOOTSTRAP (motion, steps) calibration points: each carries the exact
// [windowStart, windowEnd] the motion was measured over, matched to a step count known to cover that
// same window. Two producers feed it:
//   - HealthConnectImporter: for each raw StepsRecord (which carries its own real start/end — Health
//     Connect never hands back a bare daily total, importers just used to collapse it into one), strap
//     motion is measured over that EXACT window and paired with that record's step count. Fully
//     automatic; runs on every Health Connect import.
//   - StepsWalkCalibrationScreen: a deliberate "walk N known steps" session, same shape.
//
// WHAT THESE ARE FOR, AND WHAT THEY ARE NOT. They exist to give a user with NO usable whole-day
// phone-vs-strap overlap *something* rather than a permanent "not calibrated". IntelligenceEngine
// therefore uses them ONLY as a fallback and NEVER blends them with its whole-day points, because the
// two fit different quantities:
//
//     k_day    = steps / (walking motion + non-stepping motion)     ← what a whole day contains
//     k_window = steps /  walking motion                            ← what these points measure
//
// so k_window > k_day by construction. Averaging them would bias every estimate, and since the fit's
// weighted median weights each point by its motion volume, a whole day's motion would also swamp a
// ten-minute walk's — the walk would be silently outvoted rather than meaningfully consulted. Because
// a bootstrap k is applied to whole-day motion it runs HIGH, which is why IntelligenceEngine caps the
// reported confidence at StepsEstimateEngine.BOOTSTRAP_MAX_CONFIDENCE ("low") no matter how internally
// consistent the points were. Consistency is not accuracy.

/** One calibration point: [motion] measured over exactly [windowStart, windowEnd), paired with [steps]
 *  known to have occurred in that same window. [source] is informational only (surfaced for debugging/
 *  the Settings screen); the fit itself doesn't distinguish origins. */
data class StepsCalibrationPoint(
    val windowStart: Long,
    val windowEnd: Long,
    val motion: Double,
    val steps: Double,
    val source: String,
)

/** [StepsCalibrationPoint.source] values. */
object StepsCalibrationSource {
    const val HEALTH_CONNECT = "health-connect"
    const val WALK = "walk"
}

object StepsCalibrationPointStore {

    private const val PREFS = "noop_steps_calibration_points"
    private const val KEY_POINTS = "points"

    /** Cap on stored points, oldest (by [StepsCalibrationPoint.windowStart]) trimmed first. Bounded so
     *  months of daily Health Connect imports can't grow the JSON blob (and the fit's input) without
     *  limit — StepsEstimateEngine's weighted median doesn't need more than a few hundred points to be
     *  well-fit; beyond that, more points cost (de)serialization time without improving the fit. */
    private const val MAX_POINTS = 400

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** All stored points, oldest first. Never throws — a missing or corrupt blob reads as empty rather
     *  than crashing a calibration pass. */
    fun load(ctx: Context): List<StepsCalibrationPoint> {
        val raw = prefs(ctx).getString(KEY_POINTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                StepsCalibrationPoint(
                    windowStart = o.optLong("s"),
                    windowEnd = o.optLong("e"),
                    motion = o.optDouble("m"),
                    steps = o.optDouble("k"),
                    source = o.optString("src"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Merge [new] points into the stored set, deduped by (windowStart, windowEnd, source) so re-importing
     * an already-covered Health Connect range doesn't pile up duplicate points on every import, then trim
     * to [MAX_POINTS] and persist. A no-op for an empty [new].
     */
    fun addAll(ctx: Context, new: List<StepsCalibrationPoint>) {
        if (new.isEmpty()) return
        val existing = load(ctx)
        val seen = HashSet<Triple<Long, Long, String>>()
        val merged = ArrayList<StepsCalibrationPoint>(existing.size + new.size)
        for (p in existing + new) {
            if (seen.add(Triple(p.windowStart, p.windowEnd, p.source))) merged.add(p)
        }
        merged.sortBy { it.windowStart }
        save(ctx, if (merged.size > MAX_POINTS) merged.takeLast(MAX_POINTS) else merged)
    }

    /** Drop every stored point. */
    fun clear(ctx: Context) = prefs(ctx).edit().remove(KEY_POINTS).apply()

    private fun save(ctx: Context, points: List<StepsCalibrationPoint>) {
        val arr = JSONArray()
        for (p in points) {
            arr.put(
                JSONObject()
                    .put("s", p.windowStart)
                    .put("e", p.windowEnd)
                    .put("m", p.motion)
                    .put("k", p.steps)
                    .put("src", p.source),
            )
        }
        prefs(ctx).edit().putString(KEY_POINTS, arr.toString()).apply()
    }
}

/** Drops window/source — [StepsEstimateEngine.calibrate] only needs motion+steps. */
fun List<StepsCalibrationPoint>.toCalibrationPoints(): List<StepsEstimateEngine.CalibrationPoint> =
    map { StepsEstimateEngine.CalibrationPoint(motion = it.motion, steps = it.steps) }
