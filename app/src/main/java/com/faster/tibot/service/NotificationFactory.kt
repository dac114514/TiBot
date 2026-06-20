package com.faster.tibot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.faster.tibot.MainActivity
import com.faster.tibot.R
import com.faster.tibot.data.telegram.BotState

object NotificationFactory {
    const val NOTIF_ID = 1001
    private const val CHANNEL_ID = "tibot_running"
    private const val CHANNEL_NAME = "TiBot 运行状态"
    private const val CHANNEL_DESC = "Bot 后台轮询状态"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService<NotificationManager>() ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = CHANNEL_DESC
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    fun build(context: Context, info: BotState.BotInfo): Notification {
        ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = if (info.firstName.isNotBlank()) {
            "${info.firstName} (@${info.username})"
        } else {
            "TiBot"
        }
        val status = when {
            info.errorReason != null -> "⚠ ${info.errorReason}"
            info.isOnline -> "🟢 在线 · 消息收集中"
            else -> "🟡 正在连接…"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }

    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
