package com.faster.tibot.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.faster.tibot.data.mqtt.MqttManager

class TiBotForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "tibot_bg_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TiBot")
            .setContentText("Bot 正在后台运行")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        MqttManager.getInstance().disconnect()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TiBot 后台服务",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "TiBot 后台运行通知" }
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
    }
}
