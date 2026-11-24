package dev.axkit.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import dev.axkit.core.Format
import dev.axkit.core.ProtocolJson
import kotlinx.serialization.Serializable
import java.time.Instant

/** What the widget shows. The app writes it; the widget never talks to the Mac itself (iOS SharedPlace.Snapshot). */
@Serializable
data class WidgetSnapshot(val updatedAt: Long, val openCards: Int, val firstCard: String?, val events: List<Event>) {
    @Serializable data class Event(val title: String, val start: Long?, val allDay: Boolean)
}

object WidgetStore {
    private fun prefs(c: Context) = c.getSharedPreferences("widget", Context.MODE_PRIVATE)
    fun load(c: Context): WidgetSnapshot? = prefs(c).getString("snapshot", null)?.let {
        runCatching { ProtocolJson.decodeFromString(WidgetSnapshot.serializer(), it) }.getOrNull()
    }
    fun save(c: Context, s: WidgetSnapshot?) {
        prefs(c).edit().apply { if (s == null) remove("snapshot") else putString("snapshot", ProtocolJson.encodeToString(WidgetSnapshot.serializer(), s)) }.apply()
        TodayWidget.reload(c)
    }
}

/** Home-screen widget "오늘과 결재": how many 결재 are waiting and today's next plans. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { draw(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        draw(context, manager, id)
    }

    companion object {
        fun reload(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            ids.forEach { draw(context, manager, it) }
        }

        private fun draw(context: Context, manager: AppWidgetManager, id: Int) {
            val small = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) < 200
            val v = RemoteViews(context.packageName, R.layout.widget_today)
            val s = WidgetStore.load(context)
            if (s == null) {
                v.setViewVisibility(R.id.content, View.GONE)
                v.setViewVisibility(R.id.unpaired, View.VISIBLE)
                v.setOnClickPendingIntent(R.id.root, open(context, "axkit://open/home"))
            } else {
                v.setViewVisibility(R.id.content, View.VISIBLE)
                v.setViewVisibility(R.id.unpaired, View.GONE)
                v.setTextViewText(R.id.count, s.openCards.toString())
                v.setTextColor(R.id.count, if (s.openCards > 0) 0xFF2F6BFF.toInt() else 0xFF8A8A8E.toInt())
                v.setTextViewText(R.id.first, if (s.openCards > 0) (s.firstCard ?: "기다리는 중") else "기다리는 것 없음")
                v.setInt(R.id.first, "setMaxLines", if (small) 3 else 2)
                v.setViewVisibility(R.id.today, if (small) View.GONE else View.VISIBLE)
                val lines = s.events.take(3).map { e ->
                    val time = if (e.allDay) "종일" else e.start?.let { Format.clock(Instant.ofEpochMilli(it)) } ?: ""
                    "$time  ${e.title}"
                }
                v.setTextViewText(R.id.events, if (lines.isEmpty()) "남은 일정이 없어요" else lines.joinToString("\n"))
                v.setOnClickPendingIntent(R.id.root, open(context, if (s.openCards > 0) "axkit://open/approvals" else "axkit://open/home"))
            }
            manager.updateAppWidget(id, v)
        }

        private fun open(context: Context, url: String) = PendingIntent.getActivity(
            context, url.hashCode(), Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
