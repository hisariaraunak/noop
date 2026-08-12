package com.noop.features.today.domain

enum class RecommendationLevel { PUSH, BUILD, RECOVER, INCOMPLETE }

data class TodayRecommendation(
    val level: RecommendationLevel,
    val headline: String,
    val action: String,
    val reasons: List<String>,
)

/**
 * Explainable v1 coaching layer. It interprets existing NOOP outputs; it never recalculates Recovery.
 * Thresholds are intentionally simple and can evolve independently of the analytics engine.
 */
object TodayRecommendationEngine {
    fun recommend(today: TodaySnapshot): TodayRecommendation {
        val recovery = today.recovery ?: return incomplete(today)
        val reasons = buildList {
            today.hrvVsBaselinePct?.let {
                when {
                    it >= 5 -> add("HRV is $it% above your baseline")
                    it <= -10 -> add("HRV is ${-it}% below your baseline")
                    else -> Unit
                }
            }
            today.restingHrVsBaselineBpm?.let {
                when {
                    it <= -3 -> add("Resting HR is ${-it} bpm below your baseline")
                    it >= 5 -> add("Resting HR is $it bpm above your baseline")
                    else -> Unit
                }
            }
            today.sleepMinutes?.let {
                if (it < 360) add("Sleep was under 6 hours")
            }
        }

        val sleepLimited = (today.sleepMinutes ?: Double.MAX_VALUE) < 360
        val hrvSuppressed = (today.hrvVsBaselinePct ?: 0) <= -10
        val rhrElevated = (today.restingHrVsBaselineBpm ?: 0) >= 5

        return when {
            recovery >= 67 && !sleepLimited && !hrvSuppressed && !rhrElevated -> TodayRecommendation(
                RecommendationLevel.PUSH,
                "Ready for more",
                "Your signals support a moderate-to-high intensity day. Train harder if it fits your plan.",
                reasons.ifEmpty { listOf("Recovery is in your high range") },
            )
            recovery < 34 || sleepLimited || (hrvSuppressed && rhrElevated) -> TodayRecommendation(
                RecommendationLevel.RECOVER,
                "Make recovery the priority",
                "Keep intensity light today. Favor easy movement, hydration and an earlier night.",
                reasons.ifEmpty { listOf("Recovery is in your low range") },
            )
            else -> TodayRecommendation(
                RecommendationLevel.BUILD,
                "Build, don't empty the tank",
                "A moderate day fits your current signals. Leave some capacity for tomorrow.",
                reasons.ifEmpty { listOf("Recovery is in your middle range") },
            )
        }
    }

    private fun incomplete(today: TodaySnapshot) = TodayRecommendation(
        RecommendationLevel.INCOMPLETE,
        "Still learning today",
        "We need more overnight data before recommending intensity.",
        buildList {
            if (today.hrvMs == null) add("HRV is missing")
            if (today.restingHrBpm == null) add("Resting HR is missing")
            if (today.sleepMinutes == null) add("Sleep data is missing")
        }.ifEmpty { listOf("Recovery has not been calculated yet") },
    )
}
