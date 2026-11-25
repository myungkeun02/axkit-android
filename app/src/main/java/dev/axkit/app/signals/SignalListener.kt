package dev.axkit.app.signals

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import dev.axkit.core.SeenNotices
import dev.axkit.core.SignalItem
import dev.axkit.core.SignalKind
import dev.axkit.core.Signals

/**
 * 알림 접근: reads each new notice once and hands the wanted ones to [PhoneSignals]. Texts arrive through the messaging
 * app's own notice, so no SMS permission is needed. Ongoing, silent-system and ax-kit's own notices are skipped.
 */
class SignalListener : NotificationListenerService() {
    private val seen = SeenNotices()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName || pkg in systemPackages) return
        val n = sbn.notification ?: return
        if (sbn.isOngoing || !sbn.isClearable) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category in skippedCategories) return

        val smsApp = PhoneSignals.smsApp(this)
        val messaging = Signals.isMessaging(pkg, smsApp)
        val kind = if (messaging) SignalKind.Sms else SignalKind.Notification
        if (!PhoneSignals.isOn(this, kind)) return

        val label = appLabel(pkg)
        if (!PhoneSignals.wants(this, pkg, label)) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val last = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)?.messages?.lastOrNull()?.text }.getOrNull()
        val text = Signals.noticeText(
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            last,
        ) ?: return

        val now = System.currentTimeMillis()
        if (!seen.firstTime("$pkg|$title|$text", now)) return
        val at = n.`when`.takeIf { it in (now - 60 * 60_000L)..(now + 60_000L) } ?: now
        val item = if (messaging) SignalItem(kind = "sms", at = at, from = title, text = text)
        else SignalItem(kind = "notification", at = at, app = label, title = title, text = text)
        PhoneSignals.record(this, item)
        // A notice is a cheap moment to look at the battery too (the 15-minute check may sleep in Doze).
        PhoneSignals.checkBattery(this)
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: pkg

    companion object {
        private val systemPackages = setOf(
            "android", "com.android.systemui", "com.android.settings", "com.android.providers.downloads",
            "com.android.vending", "com.samsung.android.lool", "com.sec.android.app.samsungapps",
        )
        private val skippedCategories = setOf(
            Notification.CATEGORY_SERVICE, Notification.CATEGORY_PROGRESS, Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_SYSTEM, Notification.CATEGORY_CALL, Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_STOPWATCH, Notification.CATEGORY_LOCATION_SHARING, Notification.CATEGORY_MISSED_CALL,
        )
    }
}
