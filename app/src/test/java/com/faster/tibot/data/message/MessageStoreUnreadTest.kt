package com.faster.tibot.data.message

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * MessageStore 的真实未读数 (R1-A / B1) 单元测试。
 *
 * 核心契约:
 * - `markRead(chatId, messageId)` 持久化 lastRead 位置, 之后 unreadCount 应反映该值
 * - `unreadCount = max(0, lastMessageId - lastReadMessageId)`
 * - 新消息到达时, 之前已读过的位置不应回退 (markRead 是单调递增的)
 * - 未 markRead 过的 chat, unreadCount 应等于其 lastMessageId
 *
 * 使用 Robolectric 因为 DataStore + Context 依赖 Android runtime。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class MessageStoreUnreadTest {

    private lateinit var store: MessageStore

    @Before
    fun setUp() {
        store = MessageStore(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        runCatching { ApplicationProvider.getApplicationContext<android.content.Context>().filesDir }
        // DataStore 单例跨测试持久 — 通过 markRead 0 显式重置不必要,每个测试用独立 chatId
    }

    @Test
    fun `unreadCount is zero when no messages exist`() = runTest {
        val unread = store.getUnreadCount(chatId = 9999_0001L)
        assertEquals(0, unread)
    }

    @Test
    fun `unreadCount equals lastMessageId before any markRead`() = runTest {
        val chatId = 9999_0010L
        store.saveMessage(msg(chatId, messageId = 100, text = "hello"))
        store.saveMessage(msg(chatId, messageId = 101, text = "world"))

        val unread = store.getUnreadCount(chatId)
        assertEquals(101, unread)
    }

    @Test
    fun `markRead clears unreadCount when reaching latest`() = runTest {
        val chatId = 9999_0020L
        store.saveMessage(msg(chatId, messageId = 50, text = "a"))
        store.saveMessage(msg(chatId, messageId = 51, text = "b"))

        store.markRead(chatId, messageId = 51)
        assertEquals(0, store.getUnreadCount(chatId))
    }

    @Test
    fun `markRead with older messageId does not regress`() = runTest {
        val chatId = 9999_0030L
        store.saveMessage(msg(chatId, messageId = 200, text = "new"))

        // 先 mark 到 200 (latest)
        store.markRead(chatId, messageId = 200)
        assertEquals(0, store.getUnreadCount(chatId))

        // 再 mark 到一个更早的位置, 应该是 no-op
        store.markRead(chatId, messageId = 150)
        assertEquals(0, store.getUnreadCount(chatId))

        // unreadCount flow 也应保持 0
        val unreadFlow = store.getUnreadCountFlow(chatId).first()
        assertEquals(0, unreadFlow)
    }

    @Test
    fun `unreadCount reflects partial read`() = runTest {
        val chatId = 9999_0040L
        store.saveMessage(msg(chatId, messageId = 10, text = "a"))
        store.saveMessage(msg(chatId, messageId = 11, text = "b"))
        store.saveMessage(msg(chatId, messageId = 12, text = "c"))

        store.markRead(chatId, messageId = 11)
        // lastMessageId=12, lastRead=11 → unread = 1
        assertEquals(1, store.getUnreadCount(chatId))
    }

    @Test
    fun `new incoming message increments unreadCount after markRead`() = runTest {
        val chatId = 9999_0050L
        store.saveMessage(msg(chatId, messageId = 1, text = "first"))
        store.markRead(chatId, messageId = 1)
        assertEquals(0, store.getUnreadCount(chatId))

        // 新消息到达
        store.saveMessage(msg(chatId, messageId = 2, text = "second"))
        assertEquals(1, store.getUnreadCount(chatId))

        store.saveMessage(msg(chatId, messageId = 3, text = "third"))
        assertEquals(2, store.getUnreadCount(chatId))
    }

    @Test
    fun `ChatSummary carries unreadCount and lastMessageId`() = runTest {
        val chatId = 9999_0060L
        store.saveMessage(msg(chatId, messageId = 7, text = "x"))
        store.markRead(chatId, messageId = 7)
        store.saveMessage(msg(chatId, messageId = 8, text = "y"))
        store.saveMessage(msg(chatId, messageId = 9, text = "z"))

        val chat = store.getAllChats().first { it.chatId == chatId }
        assertEquals(9L, chat.lastMessageId)
        assertEquals(2, chat.unreadCount)  // lastMessageId=9 - lastRead=7 = 2
    }

    private fun msg(chatId: Long, messageId: Long, text: String) = TelegramMessage(
        messageId = messageId,
        chatId = chatId,
        chatTitle = "test-$chatId",
        text = text,
        fromName = "tester",
        date = 1_700_000_000L + messageId,
    )
}
