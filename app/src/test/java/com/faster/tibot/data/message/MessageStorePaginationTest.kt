package com.faster.tibot.data.message

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.faster.tibot.data.telegram.TelegramMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * MessageStore 的分页 API (R1-B / B2) 单元测试。
 *
 * 核心契约:
 * - getMessages(chatId, limit, offset) 按 date 升序的存储顺序返回
 * - offset = 0 → 末尾 limit 条 (最新)
 * - offset = N → 跳过最新 N 条, 再取 limit 条 (更老)
 * - offset 越界 (超过总数) → 返回空列表, 不抛异常
 * - limit 超过剩余 → 返回剩余条数
 * - 旧 API getMessages(chatId) 不受影响 (返回全部)
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class MessageStorePaginationTest {

    private lateinit var store: MessageStore

    @Before
    fun setUp() {
        store = MessageStore(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `getMessages with default offset returns latest limit messages`() = runTest {
        val chatId = 8001_0001L
        // 存 10 条, date 递增 (1..10)
        for (i in 1..10) {
            store.saveMessage(msg(chatId, messageId = i.toLong(), text = "m$i", date = 1_700_000_000L + i))
        }

        // 默认 offset=0, limit=200 → 返回全部 10 条
        val page = store.getMessages(chatId, limit = 200, offset = 0)
        assertEquals(10, page.size)
        // date 升序
        assertEquals("m1", page.first().text)
        assertEquals("m10", page.last().text)
    }

    @Test
    fun `getMessages with offset skips newest N messages`() = runTest {
        val chatId = 8001_0002L
        for (i in 1..10) {
            store.saveMessage(msg(chatId, messageId = i.toLong(), text = "m$i", date = 1_700_000_000L + i))
        }

        // offset=3, limit=4 → 跳过最新 3 条 (m8, m9, m10), 取 limit=4 条 (m4..m7)
        val page = store.getMessages(chatId, limit = 4, offset = 3)
        assertEquals(4, page.size)
        assertEquals("m4", page[0].text)
        assertEquals("m5", page[1].text)
        assertEquals("m6", page[2].text)
        assertEquals("m7", page[3].text)
    }

    @Test
    fun `getMessages with offset equal total returns empty list`() = runTest {
        val chatId = 8001_0003L
        for (i in 1..5) {
            store.saveMessage(msg(chatId, messageId = i.toLong(), text = "m$i", date = 1_700_000_000L + i))
        }
        val page = store.getMessages(chatId, limit = 200, offset = 5)
        assertTrue(page.isEmpty())
    }

    @Test
    fun `getMessages with offset greater than total returns empty list`() = runTest {
        val chatId = 8001_0004L
        for (i in 1..5) {
            store.saveMessage(msg(chatId, messageId = i.toLong(), text = "m$i", date = 1_700_000_000L + i))
        }
        val page = store.getMessages(chatId, limit = 200, offset = 100)
        assertTrue(page.isEmpty())
    }

    @Test
    fun `getMessages with limit larger than remaining returns remaining`() = runTest {
        val chatId = 8001_0005L
        for (i in 1..5) {
            store.saveMessage(msg(chatId, messageId = i.toLong(), text = "m$i", date = 1_700_000_000L + i))
        }
        // offset=3, limit=200 → 跳过最新 3 条, 只剩 2 条
        val page = store.getMessages(chatId, limit = 200, offset = 3)
        assertEquals(2, page.size)
        assertEquals("m1", page[0].text)
        assertEquals("m2", page[1].text)
    }

    @Test
    fun `getMessages paginates through full chat`() = runTest {
        val chatId = 8001_0006L
        for (i in 1..10) {
            store.saveMessage(msg(chatId, messageId = i.toLong(), text = "m$i", date = 1_700_000_000L + i))
        }

        // 第一页: 最新 4 条 (m7..m10)
        val p1 = store.getMessages(chatId, limit = 4, offset = 0)
        assertEquals(listOf("m7", "m8", "m9", "m10"), p1.map { it.text })

        // 第二页: 跳过最新 4 条, 取 m3..m6
        val p2 = store.getMessages(chatId, limit = 4, offset = 4)
        assertEquals(listOf("m3", "m4", "m5", "m6"), p2.map { it.text })

        // 第三页: 跳过最新 8 条, 取 m1..m2
        val p3 = store.getMessages(chatId, limit = 4, offset = 8)
        assertEquals(listOf("m1", "m2"), p3.map { it.text })

        // 第四页: 跳过 10 条, 越界, 空
        val p4 = store.getMessages(chatId, limit = 4, offset = 10)
        assertTrue(p4.isEmpty())
    }

    @Test
    fun `getMessages on empty chat returns empty list`() = runTest {
        val chatId = 8001_0007L
        val page = store.getMessages(chatId, limit = 200, offset = 0)
        assertTrue(page.isEmpty())
    }

    @Test
    fun `legacy getMessages chatId still returns all messages`() = runTest {
        val chatId = 8001_0008L
        for (i in 1..7) {
            store.saveMessage(msg(chatId, messageId = i.toLong(), text = "m$i", date = 1_700_000_000L + i))
        }
        val all = store.getMessages(chatId)
        assertEquals(7, all.size)
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
