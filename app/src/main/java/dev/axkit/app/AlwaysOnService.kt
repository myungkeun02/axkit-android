package dev.axkit.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * "항상 연결해 두기" (opt-in): an ongoing notification keeps the app's process, so the event stream stays open and new
 * 결재 and messages notify at once — the stand-in for push until the Mac can send real push (FCM or UnifiedPush).
 */
class AlwaysOnService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "맥과 연결 유지", NotificationManager.IMPORTANCE_MIN).apply {
                description = "항상 연결해 두기를 켰을 때만 보여요"
                setShowBadge(false)
            })
        }
        val open = PendingIntent.getActivity(
            this, 9, Intent(Intent.ACTION_VIEW, Uri.parse("axkit://open/more?v=settings")).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("맥과 연결해 두는 중")
            .setContentText("새 결재와 메시지를 바로 알려 드려요. 설정에서 끌 수 있어요.")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = when {
            Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }
        runCatching { ServiceCompat.startForeground(this, 8, notification, type) }
        return START_STICKY
    }

    companion object {
        private const val CHANNEL = "always-on"
        fun start(context: Context) { runCatching { context.startForegroundService(Intent(context, AlwaysOnService::class.java)) } }
        fun stop(context: Context) { context.stopService(Intent(context, AlwaysOnService::class.java)) }
    }
}
