package com.faster.tibot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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

    // === R1-A / B5 消息通知 channel ===
    const val MESSAGES_CHANNEL_ID = "tibot_messages"
    private const val MESSAGES_CHANNEL_NAME = "TiBot 消息通知"
    private const val MESSAGES_CHANNEL_DESC = "收到新消息时弹出系统通知"

    /**
     * 消息通知 ID 基址。同一 chatId 映射到同一 notif id (实现"覆盖"语义),
     * 不同 chatId 互不冲突。取模避免 chatId 过大溢出 Int。
     *
     * 公开供测试使用 (生产代码请用 [showMessageNotification] 间接拿到)。
     */
    const val MESSAGE_NOTIF_BASE_ID = 2000
    private const val MESSAGE_NOTIF_SLOT_RANGE = 1000

    fun messageNotifIdForTest(chatId: Long): Int = messageNotifId(chatId)

    private fun messageNotifId(chatId: Long): Int =
        MESSAGE_NOTIF_BASE_ID + (chatId.toInt() and 0x7FFFFFFF) % MESSAGE_NOTIF_SLOT_RANGE

    fun ensureChannel(context: Context) {
        ensureRunningChannel(context)
        ensureMessagesChannel(context)
    }

    private fun ensureRunningChannel(context: Context) {
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

    private fun ensureMessagesChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService<NotificationManager>() ?: return
            if (mgr.getNotificationChannel(MESSAGES_CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    MESSAGES_CHANNEL_ID,
                    MESSAGES_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = MESSAGES_CHANNEL_DESC
                    setShowBadge(true)
                    enableLights(true)
                    enableVibration(true)
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

    /**
     * R1-A / B5 入口: 弹一条新消息系统通知。
     *
     * - channel: [MESSAGES_CHANNEL_ID] (HIGH importance, 弹横幅 + 声音)
     * - PendingIntent: 打开 [MainActivity]
     * - notif id: 同一 chatId 复用同一 id (覆盖式更新)
     *
     * 若用户全局禁了通知权限, 静默返回 (不抛异常)。
     */
    fun showMessageNotification(
        context: Context,
        senderName: String,
        text: String,
        chatId: Long,
    ) {
        if (!areNotificationsEnabled(context)) {
            Log.d("NotificationFactory", "notifications disabled, skip chatId=$chatId")
            return
        }
        ensureMessagesChannel(context)
        val notifId = messageNotifId(chatId)
        val contentIntent = PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val displayName = senderName.ifBlank { "新消息" }
        val preview = text.take(80)
        val expanded = text.take(240)
        val notification = NotificationCompat.Builder(context, MESSAGES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(displayName)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }.onFailure { Log.w("NotificationFactory", "notify failed chatId=$chatId: ${it.message}") }
    }

    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
