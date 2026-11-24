package dev.axkit.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/** Keeps a recording going with the screen off (iOS: background audio). Shows "녹음 중" while it runs. */
class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel("recording") == null) {
            manager.createNotificationChannel(NotificationChannel("recording", "녹음", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(this, "recording")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("녹음 중")
            .setContentText("회의록을 만들려고 녹음하고 있어요")
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        runCatching { ServiceCompat.startForeground(this, 7, notification, type) }
        return START_NOT_STICKY
    }

    companion object {
        fun start(context: Context) { runCatching { context.startForegroundService(Intent(context, RecordingService::class.java)) } }
        fun stop(context: Context) { context.stopService(Intent(context, RecordingService::class.java)) }
    }
}
