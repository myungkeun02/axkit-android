package dev.axkit.app.signals

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.axkit.core.SignalItem
import dev.axkit.core.SignalKind
import java.time.LocalDate
import java.time.ZoneId

/** 걸음 수 (optional): today's total from Health Connect, at most once an hour and only when it changed. */
object StepsSignals {
    val readSteps = HealthPermission.getReadPermission(StepsRecord::class)
    const val background = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    fun available(context: Context) = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client(context: Context) = if (available(context)) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    /** Read steps, and the background read the 15-minute check needs (when this Health Connect has it). */
    fun wanted(context: Context): Set<String> {
        val c = client(context) ?: return setOf(readSteps)
        val bg = runCatching {
            c.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        }.getOrDefault(false)
        return if (bg) setOf(readSteps, background) else setOf(readSteps)
    }

    suspend fun granted(context: Context): Boolean {
        val c = client(context) ?: return false
        return runCatching { readSteps in c.permissionController.getGrantedPermissions() }.getOrDefault(false)
    }

    suspend fun today(context: Context): SignalItem? {
        val c = client(context) ?: return null
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone)
        val start = day.atStartOfDay(zone).toInstant()
        val now = java.time.Instant.now()
        val total = runCatching {
            c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, now)))[StepsRecord.COUNT_TOTAL]
        }.getOrNull() ?: return null
        return SignalItem(kind = "steps", at = now.toEpochMilli(), steps = total, date = day.toString())
    }

    suspend fun maybeRecord(context: Context) {
        if (!PhoneSignals.isOn(context, SignalKind.Steps)) return
        val prefs = context.getSharedPreferences("signals", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("steps.checked", 0) < 60 * 60_000L) return
        val item = today(context) ?: return
        prefs.edit().putLong("steps.checked", now).apply()
        val key = "${item.date}:${item.steps}"
        if (prefs.getString("steps.last", null) == key) return
        prefs.edit().putString("steps.last", key).apply()
        PhoneSignals.record(context, item)
    }
}
