package com.heartguard.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.heartguard.MainActivity
import com.heartguard.R
import com.heartguard.data.local.AppDatabase
import java.time.LocalDate
import java.time.ZoneId

class HeartGuardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeartGuardWidget()
}

class HeartGuardWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val pendingMedicationCount = loadPendingMedicationCount(context)
        val medicationStatusText = if (pendingMedicationCount > 0) {
            context.getString(R.string.widget_pending_medication_count, pendingMedicationCount)
        } else {
            context.getString(R.string.widget_no_pending_medication)
        }
        val titleText = context.getString(R.string.widget_today_status)
        val emergencyButtonText = context.getString(R.string.widget_emergency_call)
        val emergencyIntent = emergencyScreenIntent(context)

        provideContent {
            GlanceTheme {
                WidgetContent(
                    titleText = titleText,
                    medicationStatusText = medicationStatusText,
                    emergencyButtonText = emergencyButtonText,
                    emergencyIntent = emergencyIntent,
                )
            }
        }
    }
}

suspend fun updateHeartGuardWidgets(context: Context) {
    HeartGuardWidget().updateAll(context.applicationContext)
}

@androidx.compose.runtime.Composable
private fun WidgetContent(
    titleText: String,
    medicationStatusText: String,
    emergencyButtonText: String,
    emergencyIntent: Intent,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(16.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = titleText,
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(modifier = GlanceModifier.height(16.dp))

        Text(
            text = medicationStatusText,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(modifier = GlanceModifier.defaultWeight())

        Button(
            text = emergencyButtonText,
            onClick = actionStartActivity(emergencyIntent),
            modifier = GlanceModifier
                .fillMaxWidth()
                .wrapContentHeight(),
        )
    }
}

private suspend fun loadPendingMedicationCount(context: Context): Int {
    val todayRange = currentDayMillisRange()
    return AppDatabase.getInstance(context)
        .appDao()
        .getTodayPendingMedicationCount(
            startOfDay = todayRange.startOfDay,
            endOfDay = todayRange.endOfDay,
            targetEpochDay = todayRange.epochDay,
        )
}

private fun emergencyScreenIntent(context: Context): Intent {
    return Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_OPEN_EMERGENCY
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

private data class DayMillisRange(
    val startOfDay: Long,
    val endOfDay: Long,
    val epochDay: Long,
)

private fun currentDayMillisRange(): DayMillisRange {
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    val startOfDay = today
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()
    val endOfDay = today
        .plusDays(1)
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()
    return DayMillisRange(
        startOfDay = startOfDay,
        endOfDay = endOfDay,
        epochDay = today.toEpochDay(),
    )
}
