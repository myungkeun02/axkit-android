package dev.axkit.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.axkit.core.CardEvent
import kotlinx.coroutines.launch

/**
 * The Android side of iOS push (§7.7): a 결재 that arrives while the app is not in front becomes a notification with
 * 승인 / 거절 actions; tapping opens the 결재 tab. Android has no app-icon number API, so the badge is the count on the
 * notification itself (launchers show it as a dot or number).
 */
object Notifier {
    private const val CHANNEL = "cards"
    private const val MESSAGES = "messages"
    private const val URGENT = "urgent"
    const val ACTION_ANSWER = "dev.axkit.app.ANSWER"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "결재", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "비서·봇이 물어보는 것"
            })
        }
        if (manager.getNotificationChannel(MESSAGES) == null) {
            manager.createNotificationChannel(NotificationChannel(MESSAGES, "비서 메시지", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "앱을 보고 있지 않을 때 온 비서·봇의 말"
            })
        }
        if (manager.getNotificationChannel(URGENT) == null) {
            manager.createNotificationChannel(NotificationChannel(URGENT, "급한 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "맡긴 일의 결과, 기차 빈자리, 스미싱 의심 문자처럼 바로 봐야 하는 것"
            })
        }
    }

    private fun openIntent(context: Context, link: String, code: Int) = PendingIntent.getActivity(
        context, code, Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private const val GROUP = "dev.axkit.app.ROOMS"
    private const val SUMMARY_ID = 0x5A5A
    const val ACTION_REPLY = "dev.axkit.app.REPLY"
    const val REPLY_KEY = "reply"
    /** At most this many chat notifications an hour (the Mac already keeps quiet hours and its own budget). */
    const val HOURLY_CAP = 30

    fun roomId(room: String) = ("room:" + room).hashCode()
    private fun noticeId(seq: Int) = ("notice:" + seq).hashCode()

    /** Counts a notification against the hourly cap; false when the cap is reached. */
    @Synchronized
    private fun take(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences("notify", Context.MODE_PRIVATE)
        val recent = prefs.getString("sent", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < 3_600_000 }
        if (recent.size >= HOURLY_CAP) return false
        prefs.edit().putString("sent", (recent + now).joinToString(",")).apply()
        return true
    }

    private fun summary(context: Context) {
        val builder = NotificationCompat.Builder(context, MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setGroup(GROUP).setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, "axkit://open/rooms", SUMMARY_ID))
        runCatching { NotificationManagerCompat.from(context).notify(SUMMARY_ID, builder.build()) }
    }

    /**
     * The newest line of a room while the app is not in front: one notification per room (MessagingStyle, the last few
     * lines kept), alerting once and then updating quietly. A reply action sends through the outbox.
     */
    fun roomLine(context: Context, room: String, roomTitle: String, author: String?, text: String, at: Long = System.currentTimeMillis()) {
        if (!allowed(context)) return
        val body = dev.axkit.core.ChatText.preview(text, 400)
        if (body.isEmpty()) return
        ensureChannel(context)
        val id = roomId(room)
        val manager = NotificationManagerCompat.from(context)
        val existing = runCatching { context.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull { it.id == id } }.getOrNull()
        if (existing == null && !take(context)) return
        val me = androidx.core.app.Person.Builder().setName("나").build()
        val style = existing?.notification?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            ?: NotificationCompat.MessagingStyle(me)
        // One-to-one with the assistant: no conversation title (Android would show "비서: 비서"); team rooms are group chats.
        style.setConversationTitle(if (room.isEmpty()) null else roomTitle).setGroupConversation(room.isNotEmpty())
        style.addMessage(body, at, androidx.core.app.Person.Builder().setName(author ?: roomTitle).build())
        while (style.messages.size > 5) style.messages.removeAt(0)
        val reply = PendingIntent.getBroadcast(
            context, id,
            Intent(context, ReplyReceiver::class.java).setAction(ACTION_REPLY).putExtra("room", room).putExtra("title", roomTitle),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val input = androidx.core.app.RemoteInput.Builder(REPLY_KEY).setLabel("${roomTitle}에게 답하기").build()
        val builder = NotificationCompat.Builder(context, MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setContentIntent(openIntent(context, "axkit://open/room?id=" + Uri.encode(room), id))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(NotificationCompat.Action.Builder(0, "답하기", reply).addRemoteInput(input).setAllowGeneratedReplies(false).build())
        runCatching { manager.notify(id, builder.build()); summary(context) }
    }

    /**
     * A notice (briefing, overnight news, a job's result, an urgent alert): each one its own notification, so the next
     * chat line never covers it. `kind` only picks the channel (urgent ones and job results) and the ⚠️ title.
     */
    fun notice(context: Context, seq: Int, room: String, roomTitle: String, text: String, urgent: Boolean, warn: Boolean) {
        if (!allowed(context)) return
        val body = dev.axkit.core.ChatText.preview(text, 400)
        if (body.isEmpty()) return
        ensureChannel(context)
        if (!warn && !take(context)) return
        val id = noticeId(seq)
        val builder = NotificationCompat.Builder(context, if (urgent) URGENT else MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (warn) "⚠️ $roomTitle" else roomTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openIntent(context, "axkit://open/room?id=" + Uri.encode(room), id))
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setPriority(if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()); summary(context) }
    }

    /** The owner read the room (here or on another device): its chat notification goes. */
    fun cancelRoom(context: Context, room: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(roomId(room))
            // The group's summary goes too once nothing is left under it.
            val left = context.getSystemService(NotificationManager::class.java).activeNotifications
                .count { it.notification.group == GROUP && it.id != SUMMARY_ID && it.id != roomId(room) }
            if (left == 0) NotificationManagerCompat.from(context).cancel(SUMMARY_ID)
        }
    }

    /** After a reply from the notification: show it as sent (or drop the notification). */
    fun replied(context: Context, room: String) { cancelRoom(context, room) }

    /** A plain notice from the app itself (a recording the Mac refused, an answer that did not go through). */
    fun simple(context: Context, key: String, title: String, text: String, link: String) {
        if (!allowed(context)) return
        ensureChannel(context)
        val id = key.hashCode()
        val builder = NotificationCompat.Builder(context, MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(context, link, id))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    fun cancelCard(context: Context, cardId: String) { runCatching { NotificationManagerCompat.from(context).cancel(cardId.hashCode()) } }

    private fun allowed(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun card(context: Context, card: CardEvent) {
        if (!allowed(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, card.cardId.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("axkit://open/approvals")).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(decision: String, code: Int) = PendingIntent.getBroadcast(
            context, card.cardId.hashCode() * 3 + code,
            Intent(context, AnswerReceiver::class.java).setAction(ACTION_ANSWER).putExtra("cardId", card.cardId).putExtra("approve", decision == "approve"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("결재 요청")
            .setContentText(card.summary)
            // The context reads as one or two plain sentences, then the question; shown whole when expanded.
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(card.note?.takeIf { it.isNotBlank() }, card.summary).joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        // 승인/거절 only for plain yes/no cards; cards with several choices open the app.
        val opts = card.options
        if (opts == null || opts.map { it.id }.toSet() == setOf("approve", "deny")) {
            builder.addAction(0, "승인", action("approve", 1)).addAction(0, "거절", action("deny", 2))
        }
        runCatching { NotificationManagerCompat.from(context).notify(card.cardId.hashCode(), builder.build()) }
    }

    /** No open 결재 left: clear the 결재 notifications (message notifications stay until opened). */
    fun updateBadge(context: Context, count: Int) {
        if (count != 0) return
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            for (n in manager.activeNotifications) if (n.notification.channelId == CHANNEL && n.tag == null) manager.cancel(n.id)
        }
    }
}

class AnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cardId = intent.getStringExtra("cardId") ?: return
        val approve = intent.getBooleanExtra("approve", false)
        val model = (context.applicationContext as AxApp).model
        val pending = goAsync()
        model.scope.launch {
            try {
                val ok = model.answerFromNotification(cardId, approve)
                NotificationManagerCompat.from(context).cancel(cardId.hashCode())
                if (!ok) Notifier.simple(
                    context, "fail-$cardId", "처리하지 못했어요",
                    "${if (approve) "승인" else "거절"}이 맥에 닿지 않았어요. 앱에서 다시 해 주세요.", "axkit://open/approvals",
                )
            } finally { pending.finish() }
        }
    }
}

/** "답하기" in a chat notification: the text goes through the outbox like anything typed in the app. */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val room = intent.getStringExtra("room") ?: return
        val text = androidx.core.app.RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.REPLY_KEY)?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        val model = (context.applicationContext as AxApp).model
        val pending = goAsync()
        model.scope.launch {
            try {
                model.sendAndWait(text, room)
                Notifier.replied(context, room)
            } finally { pending.finish() }
        }
    }
}
