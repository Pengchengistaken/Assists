package com.ven.assists.simple

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * 保活用前台服务。
 *
 * 从 [android.content.Context.startForegroundService] 启动后必须尽快 [startForeground]，
 * 否则会触发 [android.app.ForegroundServiceDidNotStartInTimeException]。
 * 此前仅在 API 34+ 的 [onCreate] 中提升前台，导致 Android 8–13 超时崩溃。
 */
class ForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 每次 startForegroundService 都须在超时内再次声明前台
        promoteToForeground()
        return START_STICKY
    }

    private fun promoteToForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(),
            type,
        )
    }

    private fun createNotification(): Notification {
        val channelId = "assists_channel"
        val channelName = "Assists服务"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("服务运行中")
            .setContentText("Assists保持运行中…")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}
