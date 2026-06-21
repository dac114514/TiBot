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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * ChatsViewModel 的消息分页 (R1-B / B2) 协调测试。
 *
 * 核心契约:
 * - selectChat 后, 初次加载最新 200 条
 * - loadOlderMessages 拿到更老的消息 prepend 到列表前面
 * - 没有更老的消息时, loadOlderMessages 是 no-op
 * - 新消息到达 (saveMessage 后) 自动 append 到列表末尾
 * - 初次加载未满 200 条时 (总消息 < 200), 加载全部
 * - 兼容 R1-A: selectChat 仍然 markRead 最新一条
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ChatsViewModelPaginationTest {

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
    fun `selectChat loads first page of latest 200 messages`() = runTest(testDispatcher) {
        val chatId = 8003_0001L
        // 存 250 条, date 递增
        for (i in 1..250) {
            store.saveMessage(msg(chatId, i.toLong(), "m$i", date = 1_700_000_000L + i))
        }

        val vm = ChatsViewModel(ctx)
        vm.selectChat(chatId)
        advanceUntilIdle()

        val msgs = vm.messages.value
        // 初次加载最新 200 条 (m51..m250)
        assertEquals(200, msgs.size)
        // 按 date 升序
        assertEquals("m51", msgs.first().text)
        assertEquals("m250", msgs.last().text)
    }

    @Test
    fun `selectChat loads all when total is less than 200`() = runTest(testDispatcher) {
        val chatId = 8003_0002L
        for (i in 1..10) {
            store.saveMessage(msg(chatId, i.toLong(), "m$i", date = 1_700_000_000L + i))
        }

        val vm = ChatsViewModel(ctx)
        vm.selectChat(chatId)
        advanceUntilIdle()

        val msgs = vm.messages.value
        assertEquals(10, msgs.size)
        assertEquals("m1", msgs.first().text)
        assertEquals("m10", msgs.last().text)
    }

    @Test
    fun `loadOlderMessages prepends older messages`() = runTest(testDispatcher) {
        val chatId = 8003_0003L
        for (i in 1..250) {
            store.saveMessage(msg(chatId, i.toLong(), "m$i", date = 1_700_000_000L + i))
        }

        val vm = ChatsViewModel(ctx)
        vm.selectChat(chatId)
        advanceUntilIdle()
        assertEquals(200, vm.messages.value.size)
        assertEquals("m51", vm.messages.value.first().text)

        // 加载更老的
        vm.loadOlderMessages(chatId)
        advanceUntilIdle()

        val msgs = vm.messages.value
        // 现在 m1..m250 全部加载 (250 总)
        assertEquals(250, msgs.size)
        assertEquals("m1", msgs.first().text)
        assertEquals("m250", msgs.last().text)
    }

    @Test
    fun `loadOlderMessages is no-op when no older messages exist`() = runTest(testDispatcher) {
        val chatId = 8003_0004L
        for (i in 1..50) {
            store.saveMessage(msg(chatId, i.toLong(), "m$i", date = 1_700_000_000L + i))
        }

        val vm = ChatsViewModel(ctx)
        vm.selectChat(chatId)
        advanceUntilIdle()
        val before = vm.messages.value.size
        assertEquals(50, before)

        // 加载更老的 (但没有更老的)
        vm.loadOlderMessages(chatId)
        advanceUntilIdle()
        assertEquals(50, vm.messages.value.size)

        // 再来一次
        vm.loadOlderMessages(chatId)
        advanceUntilIdle()
        assertEquals(50, vm.messages.value.size)
    }

    @Test
    fun `new message after selectChat is appended`() = runTest(testDispatcher) {
        val chatId = 8003_0005L
        for (i in 1..5) {
            store.saveMessage(msg(chatId, i.toLong(), "m$i", date = 1_700_000_000L + i))
        }

        val vm = ChatsViewModel(ctx)
        vm.selectChat(chatId)
        advanceUntilIdle()
        assertEquals(5, vm.messages.value.size)
        assertEquals("m5", vm.messages.value.last().text)

        // 新消息到达
        store.saveMessage(msg(chatId, 6, "m6", date = 1_700_000_000L + 6))
        advanceUntilIdle()

        val msgs = vm.messages.value
        assertEquals(6, msgs.size)
        assertEquals("m6", msgs.last().text)
        // 旧消息顺序不变
        assertEquals("m1", msgs.first().text)
    }

    @Test
    fun `loadOlderMessages preserves markRead from selectChat (R1-A compat)`() = runTest(testDispatcher) {
        val chatId = 8003_0006L
        store.saveMessage(msg(chatId, 1, "a"))
        store.saveMessage(msg(chatId, 2, "b"))
        store.saveMessage(msg(chatId, 3, "c"))

        val vm = ChatsViewModel(ctx)
        vm.selectChat(chatId)
        advanceUntilIdle()

        // R1-A 引入的 markRead 仍然工作
        assertEquals(0, store.getUnreadCount(chatId))
        // 初次加载全部
        assertEquals(3, vm.messages.value.size)
    }

    @Test
    fun `selectChat twice on different chats resets messages`() = runTest(testDispatcher) {
        val c1 = 8003_0010L
        val c2 = 8003_0011L
        store.saveMessage(msg(c1, 1, "c1-m1", date = 100L))
        store.saveMessage(msg(c1, 2, "c1-m2", date = 101L))
        store.saveMessage(msg(c2, 10, "c2-m1", date = 200L))

        val vm = ChatsViewModel(ctx)
        vm.selectChat(c1)
        advanceUntilIdle()
        assertEquals(2, vm.messages.value.size)
        assertEquals("c1-m1", vm.messages.value.first().text)

        // 切到 c2, 应该清空 c1 的 messages 然后加载 c2
        vm.selectChat(c2)
        advanceUntilIdle()
        val msgs = vm.messages.value
        assertEquals(1, msgs.size)
        assertEquals("c2-m1", msgs.first().text)
    }

    private fun msg(chatId: Long, messageId: Long, text: String, date: Long) = TelegramMessage(
        messageId = messageId,
        chatId = chatId,
        chatTitle = "t-$chatId",
        text = text,
        fromName = "tester",
        date = date,
    )
}
