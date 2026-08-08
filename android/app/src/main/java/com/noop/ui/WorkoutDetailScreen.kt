package com.noop.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
 */
@Composable
fun WorkoutDetailScreen(vm: AppViewModel, deviceId: String, startTs: Long) {
    val rows by vm.workouts.collectAsState()
    val row = rows.firstOrNull { it.deviceId == deviceId && it.startTs == startTs }

    // 2026-08 audit: this was the only screen in the app with no subtitle at all. Reuses the same
    // date/time-range formatter (workoutCaption, TodayScreen.kt) Today's own Latest Workout card and
    // the Workouts list already format this exact string with.
    ScreenScaffold(
        title = row?.let { WorkoutEditing.displaySport(it.sport) } ?: "Workout",
        subtitle = row?.let { workoutCaption(it) },
    ) {
        if (row == null) {
            Text(
                "This workout is no longer available.",
                style = NoopType.footnote,
                color = Palette.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            WorkoutDetailBody(vm = vm, row = row)
        }
    }
}
