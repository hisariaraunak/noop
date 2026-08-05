package com.noop.analytics

/**
 * The pure logic behind a DELETED sleep night's durable tombstone (#65/#33), the Kotlin twin of Swift's
 * `DismissedSleepSpans`.
 *
 * Deleting a DETECTED sleep must suppress its re-detection so the night does not silently come back on the
 * next analyze pass, WITH an undo. Android stores the tombstones in the `dismissedSleep` Room table under
 * the deleted row's OWN `deviceId`; this object owns everything about the read/guard that has no DB I/O,
 * so it runs on the JVM with no Room and stays in lockstep with the Swift twin.
 *
 * HAZARD 1 (#65 3A): the tombstone is written under `session.deviceId` ("my-whoop" for an IMPORTED night,
 * "my-whoop-noop" for a computed one), but the engine guard used to read ONLY the computed id, so a deleted
 * IMPORTED night's tombstone was never consulted and a raw re-detection resurrected it as a computed twin.
 * The fix is the UNION read ([WhoopRepository.dismissedSleeps] reads both ids); this object's overlap test
 * then works whatever id the tombstone was written under.
 */
object DismissedSleepGuard {

    /** True when `[sessionStart, sessionEnd)` time-overlaps ANY dismissed `(start, end)` window: the
     *  engine's re-detection guard predicate. Overlap (not exact startTs) because a re-detected onset
     *  drifts as more raw data arrives. Half-open `<` test, matching the Swift twin and the engine. */
    fun isSuppressed(
        sessionStart: Long,
        sessionEnd: Long,
        dismissedWindows: List<Pair<Long, Long>>,
    ): Boolean = dismissedWindows.any { (start, end) -> sessionStart < end && start < sessionEnd }

    /**
     * Minimum share of a DETECTED session's OWN span that a manually-ADDED session
     * ([com.noop.data.WhoopRepository.addManualNap]: `userEdited = 1`, `startTsAdjusted = null`) must
     * cover before it suppresses that session's re-detection.
     *
     * A hand-CORRECTED night ([com.noop.data.WhoopRepository.updateSleepSessionTimes], which always
     * stamps a non-null `startTsAdjusted`) IS the detected night's own twin, so it keeps the
     * unconditional bare-overlap suppression above — that is exactly the #318 double-count guard.
     *
     * A manually-ADDED session is NOT a twin: it exists precisely because detection MISSED that window,
     * so it has no detected counterpart to collide with (its `startTs` differs, and both rows coexist
     * happily). Under the bare-overlap rule a short logged nap sitting INSIDE a real night suppressed
     * the WHOLE night from the sleepSession table for good: the day still SCORED correctly (analyzeDay
     * reads freshly-detected sessions, never the table, so the daily total and the Sleep tab's trend
     * chart stayed right) while the Sleep tab's hero — which reads the stored rows — showed only the
     * nap, and no amount of re-syncing could heal it (the nap is `userEdited`, so [SleepSessionDedup]
     * never drops it either). Requiring real coverage keeps the intended "don't re-detect over what the
     * user logged" behaviour for a genuine same-window add, while a contained fragment leaves the
     * night alone.
     */
    const val ADDED_SESSION_MIN_COVERAGE: Double = 0.5

    /** True when ANY [addedWindows] entry covers at least [minCoverage] of `[sessionStart, sessionEnd)`
     *  — the suppression test for manually-ADDED sessions (see [ADDED_SESSION_MIN_COVERAGE]). A
     *  zero/negative-span session is never suppressed. Pure + deterministic. */
    fun isCoveredByAdded(
        sessionStart: Long,
        sessionEnd: Long,
        addedWindows: List<Pair<Long, Long>>,
        minCoverage: Double = ADDED_SESSION_MIN_COVERAGE,
    ): Boolean {
        val span = sessionEnd - sessionStart
        if (span <= 0L) return false
        return addedWindows.any { (start, end) ->
            val overlap = minOf(sessionEnd, end) - maxOf(sessionStart, start)
            overlap > 0L && overlap.toDouble() >= minCoverage * span.toDouble()
        }
    }

    /** Drop every session in [sessions] that overlaps a dismissed window: the engine's `sleepKept`
     *  filter, as a pure function so a JVM test can pin it. [windowOf] projects a session to its
     *  `(start, end)` detected window. */
    fun <T> keeping(
        sessions: List<T>,
        dismissedWindows: List<Pair<Long, Long>>,
        windowOf: (T) -> Pair<Long, Long>,
    ): List<T> = sessions.filterNot {
        val (s, e) = windowOf(it)
        isSuppressed(s, e, dismissedWindows)
    }

    /** Whether deleting a night writes a suppression tombstone (#65). A DETECTED night is tombstoned so
     *  the recompute does not regenerate it. A user-created/edited (`userEdited`) night (a hand-corrected
     *  night or a manually-added nap) is deleted WITHOUT a tombstone: it is never re-detected, so
     *  suppressing its window would needlessly block a real future night overlapping it. */
    fun writesTombstoneOnDelete(userEdited: Boolean): Boolean = !userEdited
}
