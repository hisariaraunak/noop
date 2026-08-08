package com.noop.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noop.data.WorkoutRow

/**
 * Full-screen workout detail, reached from both Today's single "Latest Workouts" tile and the Workouts
 * screen's "All Sessions" row tap — one consistent detail view everywhere (previously the Workouts screen
 * alone opened a [androidx.compose.material3.ModalBottomSheet]; Today's tile wasn't tappable at all).
 * The content itself ([WorkoutDetailBody]) is unchanged from that sheet.
 *
 * Looked up by (deviceId, startTs) — the [WorkoutRow] natural key — from the SAME live [AppViewModel.workouts]
 * list every other workout surface reads, so an edit/dismiss/delete made elsewhere is reflected here too
 * without a separate fetch. A row that's gone by the time this resolves (e.g. deleted mid-navigation) shows
 * an honest empty note rather than a blank screen.
 *
 * 2026-08 bug fix: that premise didn't hold for Today's own tile — it sources its row from
 * `viewModel.repo.fillWorkoutHrFromStrap(...)` directly (TodayScreen.kt), never through
 * [AppViewModel.workouts]/[AppViewModel.loadWorkouts]. So navigating here from Today always hit an
 * EMPTY [AppViewModel.workouts] and showed "no longer available" for every workout, every time —
 * confirmed live via logcat (`rowsCount=0` on every lookup). [CoupledScreen] and [JournalScreen] hit
 * the identical class of bug earlier and fixed it the same way: kick [AppViewModel.loadWorkouts] on
 * entry rather than assume some other screen already populated it. While that load is in flight,
 * `rows` is empty for a reason that has nothing to do with the specific workout being missing, so a
 * loading spinner replaces the "no longer available" text until the first real answer comes back.
 *
 * 2026-08 audit: no ScreenScaffold title/subtitle — [WorkoutDetailBody] opens with its own icon +
 * sport-name + date header (plus the source badge), so a second title/subtitle here would just
 * repeat it. Matches the `title = null` pattern Today uses for the same reason (its own compact
 * header replaces the standard one), including the tightened `topPadding`.
 */
@Composable
fun WorkoutDetailScreen(vm: AppViewModel, deviceId: String, startTs: Long) {
    LaunchedEffect(Unit) { vm.loadWorkouts() }
    val rows by vm.workouts.collectAsState()
    val row = rows.firstOrNull { it.deviceId == deviceId && it.startTs == startTs }

    ScreenScaffold(
        title = null,
        subtitle = null,
        topPadding = 12.dp,
    ) {
        when {
            row != null -> WorkoutDetailBody(vm = vm, row = row)
            // An empty list means the load kicked off above hasn't resolved yet, not that a row we
            // just tapped from a visible tile has vanished — show a spinner, not a false "gone" claim.
            rows.isEmpty() -> CircularProgressIndicator(color = Palette.accent, modifier = Modifier.size(22.dp))
            else -> Text(
                "This workout is no longer available.",
                style = NoopType.footnote,
                color = Palette.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
