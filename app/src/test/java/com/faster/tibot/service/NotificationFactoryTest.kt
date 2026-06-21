package com.faster.tibot.service

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * NotificationFactory 的消息通知 channel + showMessageNotification (R1-A / B5) 单元测试。
 *
 * 核心契约:
 * - 调用 ensureChannel 后, 存在 "tibot_messages" channel (IMPORTANCE_HIGH)
 * - showMessageNotification 调通后, NotificationManager 中有活动通知
 * - notif ID 在同 chatId 下稳定 (可更新, 不重复创建)
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class NotificationFactoryTest {

    private lateinit var ctx: Context
    private lateinit var nm: NotificationManager

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 清理 channel 状态 (Robolectric 持久化)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.deleteNotificationChannel(NotificationFactory.MESSAGES_CHANNEL_ID)
        }
    }

    @Test
    fun `ensureChannel creates tibot_messages with HIGH importance`() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        NotificationFactory.ensureChannel(ctx)

        val ch = nm.getNotificationChannel(NotificationFactory.MESSAGES_CHANNEL_ID)
        assertNotNull("messages channel should exist", ch)
        assertEquals(
            "messages channel should be HIGH importance",
            NotificationManager.IMPORTANCE_HIGH,
            ch!!.importance,
        )
        assertTrue("messages channel should show badge", ch.canShowBadge())
    }

    @Test
    fun `showMessageNotification posts active notification`() {
        NotificationFactory.showMessageNotification(
            context = ctx,
            senderName = "Alice",
            text = "Hello, world!",
            chatId = 5555_0001L,
        )

        val active = nm.activeNotifications
        assertTrue("expected at least one active notification, got $active", active.isNotEmpty())
        val found = active.any { it.id == NotificationFactory.messageNotifIdForTest(5555_0001L) }
        assertTrue("expected notification for chatId 5555_0001", found)
    }

    @Test
    fun `showMessageNotification with same chatId reuses same notif id`() {
        val expectedId = NotificationFactory.messageNotifIdForTest(5555_0002L)

        NotificationFactory.showMessageNotification(ctx, "Bob", "msg1", 5555_0002L)
        NotificationFactory.showMessageNotification(ctx, "Bob", "msg2", 5555_0002L)

        val active = nm.activeNotifications
        // 同一 chatId 多次调用 → 同一 notifId (replace, 不增加)
        val matching = active.filter { it.id == expectedId }
        assertEquals("same chatId should reuse notif id", 1, matching.size)
    }

    @Test
    fun `showMessageNotification different chatIds use different notif ids`() {
        NotificationFactory.showMessageNotification(ctx, "X", "a", 5555_0010L)
        NotificationFactory.showMessageNotification(ctx, "Y", "b", 5555_0011L)

        val idA = NotificationFactory.messageNotifIdForTest(5555_0010L)
        val idB = NotificationFactory.messageNotifIdForTest(5555_0011L)
        assertTrue("different chatIds should map to different notif ids", idA != idB)
    }
}
