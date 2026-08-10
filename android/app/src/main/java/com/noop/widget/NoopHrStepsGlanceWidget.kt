package com.noop.widget
import com.noop.ui.uiString

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.noop.R
import com.noop.ui.MainActivity
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * Home-screen widget: live heart rate and today's steps, side by side — the two numbers that
 * change through the day, without the three end-of-day scores the other two widgets already
 * cover. Renders purely from the [WidgetSnapshotStore] SharedPreferences snapshot — no BLE, no
 * DB — so it costs nothing and survives process death. Tapping anywhere opens the app.
 *
 * Colours mirror [NoopGlanceWidget]'s hardcoded Titanium & Gold palette (Glance composes outside
 * our theme): heart rate in the app's metricRose, steps in metricCyan — the SAME two colours
 * Today's Key Metrics tiles already use for these exact fields, so this widget reads as "the
 * live half of Today", not an unrelated design.
 */
class NoopHrStepsGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = runCatching { WidgetSnapshotStore.load(context) }.getOrDefault(WidgetSnapshot())
        val dark = runCatching {
            when (context.getSharedPreferences("noop_prefs", Context.MODE_PRIVATE)
                .getString("theme.appearance", "system")) {
                "light" -> false
                "dark" -> true
                else -> (context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }.getOrDefault(true)
        provideContent { HrStepsWidgetContent(snap, dark) }
    }

    /** See [NoopGlanceWidget.onCompositionError] — same defence-in-depth swap, not a crash fix. */
    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable,
    ) {
        runCatching {
            val rv = android.widget.RemoteViews(context.packageName, R.layout.noop_widget_error)
            android.appwidget.AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, rv)
        }
    }
}

private fun hrStepsSurface(dark: Boolean) = ColorProvider(if (dark) Color(0xFF0A1322) else Color(0xFFF4F1EA))
private fun hrStepsTextSecondary(dark: Boolean) = ColorProvider(if (dark) Color(0xFF8A94A4) else Color(0xFF7C8696))
private fun hrStepsDivider(dark: Boolean) = ColorProvider(if (dark) Color(0xFF1C2636) else Color(0xFFDAD3C2))

/** Heart-rate tint — Palette.metricRose, the same colour Today's Resting-HR tile uses. */
private fun heartColor(dark: Boolean): ColorProvider =
    ColorProvider(if (dark) Color(0xFFE0662F) else Color(0xFFC84E1E))

/** Steps tint — Palette.metricCyan, the same colour Today's Steps tile uses. */
private fun stepsColor(dark: Boolean): ColorProvider =
    ColorProvider(if (dark) Color(0xFF3FA9C9) else Color(0xFF2E92B4))

/** Battery icon colour: Palette.statusPositive at/above 20%, Palette.statusCritical below —
 *  the same low-battery threshold [com.noop.notif.BatteryAlertNotifier] alerts on. */
private fun batteryColor(pct: Int?, dark: Boolean): ColorProvider = ColorProvider(
    when {
        pct == null -> if (dark) Color(0xFF8A94A4) else Color(0xFF7C8696)
        pct < 20 -> if (dark) Color(0xFFE0662F) else Color(0xFFC84E1E)
        else -> if (dark) Color(0xFF03E095) else Color(0xFFB07D17)
    },
)

@Composable
private fun HrStepsWidgetContent(snap: WidgetSnapshot, dark: Boolean) {
    val surface = hrStepsSurface(dark)
    val textSecondary = hrStepsTextSecondary(dark)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(surface)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HrStepsCell(
                iconRes = R.drawable.ic_widget_heart,
                iconDescription = "Heart rate",
                label = "LIVE HR",
                value = snap.heartRate?.toString() ?: "—",
                unit = "bpm",
                color = heartColor(dark),
                textSecondary = textSecondary,
                modifier = GlanceModifier.defaultWeight(),
            )
            Box(
                modifier = GlanceModifier.width(1.dp).height(40.dp).background(hrStepsDivider(dark)),
            ) {}
            HrStepsCell(
                iconRes = R.drawable.ic_widget_steps,
                iconDescription = "Steps",
                label = "STEPS",
                value = snap.steps?.let { NumberFormat.getIntegerInstance(Locale.US).format(it) } ?: "—",
                unit = null,
                color = stepsColor(dark),
                textSecondary = textSecondary,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    snap.connected -> "Connected"
                    snap.updatedAtMs > 0L ->
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snap.updatedAtMs))
                    else -> "Open NOOP to connect"
                },
                style = TextStyle(color = textSecondary, fontSize = 11.sp),
            )
            snap.batteryPct?.let { pct ->
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(text = "·", style = TextStyle(color = textSecondary, fontSize = 11.sp))
                Spacer(modifier = GlanceModifier.width(5.dp))
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_strap_battery),
                    contentDescription = uiString(R.string.l10n_noop_compact_glance_widget_strap_battery_a6c7f09c),
                    modifier = GlanceModifier.width(13.dp).height(13.dp),
                    colorFilter = ColorFilter.tint(batteryColor(pct, dark)),
                )
                Spacer(modifier = GlanceModifier.width(3.dp))
                Text(text = "$pct%", style = TextStyle(color = textSecondary, fontSize = 11.sp))
            }
        }
    }
}

/** One HR/Steps column: icon + small overline label over a big coloured value (+ optional unit),
 *  matching [NoopCompactGlanceWidget]'s icon-cell shape. "—" while that reading isn't available
 *  yet — never a fabricated number. */
@Composable
private fun HrStepsCell(
    iconRes: Int,
    iconDescription: String,
    label: String,
    value: String,
    unit: String?,
    color: ColorProvider,
    textSecondary: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = iconDescription,
                modifier = GlanceModifier.width(13.dp).height(13.dp),
                colorFilter = ColorFilter.tint(color),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = label,
                style = TextStyle(color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = TextStyle(color = color, fontSize = 26.sp, fontWeight = FontWeight.Bold),
            )
            if (unit != null && value != "—") {
                Spacer(modifier = GlanceModifier.width(2.dp))
                Text(
                    text = unit,
                    style = TextStyle(color = textSecondary, fontSize = 11.sp),
                    modifier = GlanceModifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}
