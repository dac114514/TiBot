package com.faster.tibot.ui.chats

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.faster.tibot.data.message.MessageStore
import com.faster.tibot.data.telegram.TelegramMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * ChatsViewModel 的 markRead / markAllRead (R1-A / B1) 协调测试。
 *
 * 核心契约:
 * - selectChat(chatId) 后, 该 chat 的 unreadCount 变为 0
 * - markAllRead() 后, 所有 chat 的 unreadCount 变为 0
 * - ViewModel 持独立 store 实例 (单测隔离), 每个测试用唯一 chatId
 *
 * 注: 用真实 MessageStore (Robolectric) 验证 ViewModel 是否正确驱动数据层。
 * 这比 mock 更稳 — 行为正确性体现在数据层, 不在 mock 调用次数。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ChatsViewModelMarkReadTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var ctx: Application
    private lateinit var store: MessageStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ctx = ApplicationProvider.getApplicationContext()
        store = MessageStore(ctx)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectChat marks latest message as read`() = runTest(testDispatcher) {
        val chatId = 6666_0001L
        store.saveMessage(msg(chatId, 100, "a"))
        store.saveMessage(msg(chatId, 101, "b"))
        // 起始: unread = 101
        assertEquals(101, store.getUnreadCount(chatId))

        val vm = ChatsViewModel(ctx)
        vm.selectChat(chatId)
        advanceUntilIdle()

        // 进入 chat → 未读清零
        assertEquals(0, store.getUnreadCount(chatId))
    }

    @Test
    fun `selectChat on chat with no messages does not throw`() = runTest(testDispatcher) {
        val chatId = 6666_0002L
        // 该 chat 从未有过消息
        val vm = ChatsViewModel(ctx)
        vm.selectChat(chatId)
        advanceUntilIdle()
        // 仍应正常完成, unread 0
        assertEquals(0, store.getUnreadCount(chatId))
    }

    @Test
    fun `markAllRead clears unread for every chat`() = runTest(testDispatcher) {
        val c1 = 6666_0010L
        val c2 = 6666_0011L
        val c3 = 6666_0012L
        store.saveMessage(msg(c1, 10, "a"))
        store.saveMessage(msg(c1, 11, "b"))
        store.saveMessage(msg(c2, 20, "c"))
        store.saveMessage(msg(c3, 30, "d"))
        store.saveMessage(msg(c3, 31, "e"))
        store.saveMessage(msg(c3, 32, "f"))

        assertEquals(11, store.getUnreadCount(c1))
        assertEquals(20, store.getUnreadCount(c2))
        assertEquals(32, store.getUnreadCount(c3))

        val vm = ChatsViewModel(ctx)
        vm.markAllRead()
        advanceUntilIdle()

        assertEquals(0, store.getUnreadCount(c1))
        assertEquals(0, store.getUnreadCount(c2))
        assertEquals(0, store.getUnreadCount(c3))
    }

    @Test
    fun `markAllRead on empty store is a no-op`() = runTest(testDispatcher) {
        val vm = ChatsViewModel(ctx)
        vm.markAllRead()  // 不能崩
        advanceUntilIdle()
        // 不变
        assertEquals(0, store.getAllChats().size)
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
