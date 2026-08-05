import Foundation

/// Merge imported and on-device-computed sleep sessions for display and export.
public enum SleepMerge {
    /// Merge imported + computed sleep, preserving EVERY session.
    ///
    /// A day with two sessions (e.g. a main night and an afternoon nap, or two nights ending the same
    /// local day) must keep BOTH — the previous per-day dictionary overwrote on collision and silently
    /// dropped one (#715). Imported sessions take precedence per day: if any imported session ends on a
    /// given local day, the computed sessions for that day yield to it (the existing imported-over-computed
    /// rule); on days with no imported session the computed sessions stand. Result is sorted by start time.
    ///
    /// Richness exception: a sparse import (no stage data on ANY of its sessions that day) must not
    /// clobber a computed day that HAS stage data — otherwise a stage-less WHOOP/Apple re-import blanks
    /// the stage breakdown for a night the strap fully staged. Days where the import carries stages, or
    /// where neither side does, keep the imported-over-computed rule unchanged. (Swift twin of the
    /// Android HealthConnectImporter richness fix, ryanbr/noop#240.)
    ///
    /// Second richness exception (thin-import guard): the first exception only fires when the import has
    /// ZERO stage data, but a Health Connect gap-fill sleep record always carries stages (however sparse
    /// its underlying capture), so it never qualified — and it can land under the SAME imported id real
    /// WHOOP data uses if its "already covered" write-time guard races the strap's own sync, then win
    /// every day forever. A staged computed day that covers at least `computedOverrideMinRatio`x the
    /// imported day's captured span is unambiguously the fuller capture, so it wins too — while a
    /// comparable-or-fuller genuine import (the common case) is untouched.
    ///
    /// - Parameter endDay: maps a session to its canonical LOCAL end-day key (callers inject their
    ///   timezone-aware keyer so this stays pure and testable).
    public static func merge(imported: [CachedSleepSession],
                             computed: [CachedSleepSession],
                             endDay: (CachedSleepSession) -> String) -> [CachedSleepSession] {
        var importedByDay: [String: [CachedSleepSession]] = [:]
        for s in imported { importedByDay[endDay(s), default: []].append(s) }
        var computedByDay: [String: [CachedSleepSession]] = [:]
        for s in computed { computedByDay[endDay(s), default: []].append(s) }

        var out: [CachedSleepSession] = []
        out.reserveCapacity(imported.count + computed.count)
        for (day, imp) in importedByDay {
            let comp = computedByDay[day]
            let compHasStages = comp?.contains(where: hasStages) ?? false
            let staysStageless = compHasStages && !imp.contains(where: hasStages)
            let materiallyFuller = compHasStages &&
                totalDurationMin(comp!) >= computedOverrideMinRatio * totalDurationMin(imp)
            if let comp, staysStageless || materiallyFuller {
                out.append(contentsOf: comp)   // richer (or materially fuller) computed day survives
            } else {
                out.append(contentsOf: imp)    // imported wins its day (unchanged rule)
            }
        }
        for (day, comp) in computedByDay where importedByDay[day] == nil {
            out.append(contentsOf: comp)
        }
        return out.sorted { $0.startTs < $1.startTs }
    }

    /// See the second richness exception in `merge`'s doc comment.
    private static let computedOverrideMinRatio = 1.5

    /// Total effective-span minutes across a day's sessions (fragment windows summed, no bridging).
    private static func totalDurationMin(_ sessions: [CachedSleepSession]) -> Double {
        sessions.reduce(0.0) { $0 + Double(max(0, $1.endTs - $1.effectiveStartTs)) / 60.0 }
    }

    /// True when the session carries a non-empty stage payload; nil, "", and "[]" carry none.
    static func hasStages(_ s: CachedSleepSession) -> Bool {
        guard let json = s.stagesJSON?.trimmingCharacters(in: .whitespacesAndNewlines) else { return false }
        return !json.isEmpty && json != "[]"
    }
}
