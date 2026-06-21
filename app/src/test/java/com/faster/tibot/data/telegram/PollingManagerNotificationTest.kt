package com.faster.tibot.data.telegram

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.faster.tibot.data.autoreply.AutoReplyEngine
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.message.MessageStore
import com.faster.tibot.service.NotificationFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * PollingManager 的消息通知触发 (R1-A / B5) 协调测试。
 *
 * 核心契约:
 * - 收到 authorized + incoming 消息时, 调用 NotificationFactory.showMessageNotification
 * - 若 notificationsEnabled = false, 不通知
 * - 若 chatId 在 perChatMute 中, 不通知
 *
 * 注: MessageStore/SettingsRepository 用 mock, NotificationFactory 用 mockkObject。
 *      TelegramBotClient / AutoReplyEngine / FileDownloader 都是 mock,
 *      我们只关心通知触发逻辑。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class PollingManagerNotificationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appContext: Context
    private lateinit var botClient: TelegramBotClient
    private lateinit var store: MessageStore
    private lateinit var engine: AutoReplyEngine
    private lateinit var settings: SettingsRepository
    private lateinit var downloader: FileDownloader

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        appContext = ApplicationProvider.getApplicationContext()
        botClient = mockk(relaxed = true)
        store = mockk(relaxed = true)
        engine = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        downloader = mockk(relaxed = true)
        mockkObject(NotificationFactory)
        // 默认 getMe 返回 null, getUpdates 返回空 — 测试场景手动 stub
        every { botClient.getMe() } returns null
        every { botClient.getUpdates(any()) } returns emptyList()
        every { settings.getUpdateOffset() } returns 0L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(NotificationFactory)
    }

    @Test
    fun `authorized incoming message triggers showMessageNotification`() = runTest(testDispatcher) {
        // access mode = all, 单条 authorized incoming 消息
        every { settings.accessMode } returns flowOf("all")
        every { settings.adminIds } returns flowOf(emptyList())
        every { settings.notificationsEnabled } returns flowOf(true)
        every { settings.perChatMute } returns flowOf(emptySet())
        every { botClient.getUpdates(any()) } returnsMany listOf(
            listOf(update(updateId = 1, chatId = 111, messageId = 50, fromId = 999)),
            emptyList(),
        )

        val pm = PollingManager(botClient, store, engine, settings, downloader, appContext)
        pm.start(this)
        advanceUntilIdle()

        verify {
            NotificationFactory.showMessageNotification(
                context = appContext,
                senderName = any(),
                text = any(),
                chatId = 111L,
            )
        }
    }

    @Test
    fun `muted chat does not trigger notification`() = runTest(testDispatcher) {
        every { settings.accessMode } returns flowOf("all")
        every { settings.adminIds } returns flowOf(emptyList())
        every { settings.notificationsEnabled } returns flowOf(true)
        every { settings.perChatMute } returns flowOf(setOf(111L))  // 该 chat 静音
        every { botClient.getUpdates(any()) } returnsMany listOf(
            listOf(update(updateId = 1, chatId = 111, messageId = 50, fromId = 999)),
            emptyList(),
        )

        val pm = PollingManager(botClient, store, engine, settings, downloader, appContext)
        pm.start(this)
        advanceUntilIdle()

        verify(exactly = 0) {
            NotificationFactory.showMessageNotification(any(), any(), any(), any())
        }
    }

    @Test
    fun `notificationsEnabled false does not trigger notification`() = runTest(testDispatcher) {
        every { settings.accessMode } returns flowOf("all")
        every { settings.adminIds } returns flowOf(emptyList())
        every { settings.notificationsEnabled } returns flowOf(false)
        every { settings.perChatMute } returns flowOf(emptySet())
        every { botClient.getUpdates(any()) } returnsMany listOf(
            listOf(update(updateId = 1, chatId = 222, messageId = 60, fromId = 999)),
            emptyList(),
        )

        val pm = PollingManager(botClient, store, engine, settings, downloader, appContext)
        pm.start(this)
        advanceUntilIdle()

        verify(exactly = 0) {
            NotificationFactory.showMessageNotification(any(), any(), any(), any())
        }
    }

    @Test
    fun `unauthorized message does not trigger notification`() = runTest(testDispatcher) {
        // admin mode, fromId 不在 admin list → unauthorized
        every { settings.accessMode } returns flowOf("admin")
        every { settings.adminIds } returns flowOf(listOf(123L))
        every { settings.notificationsEnabled } returns flowOf(true)
        every { settings.perChatMute } returns flowOf(emptySet())
        every { botClient.getUpdates(any()) } returnsMany listOf(
            listOf(update(updateId = 1, chatId = 333, messageId = 70, fromId = 999)),
            emptyList(),
        )

        val pm = PollingManager(botClient, store, engine, settings, downloader, appContext)
        pm.start(this)
        advanceUntilIdle()

        verify(exactly = 0) {
            NotificationFactory.showMessageNotification(any(), any(), any(), any())
        }
    }

    private fun update(updateId: Long, chatId: Long, messageId: Long, fromId: Long): TelegramUpdate {
        return TelegramUpdate(
            updateId = updateId,
            message = TelegramMessage(
                messageId = messageId,
                chatId = chatId,
                chatTitle = "Test",
                text = "hello",
                fromName = "tester",
                date = 1_700_000_000L,
                fromId = fromId,
                chatType = "private",
            ),
        )
    }
}
