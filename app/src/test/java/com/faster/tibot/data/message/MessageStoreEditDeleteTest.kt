package com.faster.tibot.data.message

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.faster.tibot.data.telegram.TelegramMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * MessageStore 的消息编辑/删除 API (R1-B / B4) 单元测试。
 *
 * 核心契约:
 * - editMessage(chatId, messageId, newText) 找到对应 messageId 的消息, 更新 text
 *   并设 isEdited = true
 * - editMessage 在 messageId 不存在时返回 failure (Result.failure)
 * - deleteMessage(chatId, messageId) 找到对应 messageId 的消息, 从存储移除
 * - deleteMessage 在 messageId 不存在时返回 failure (Result.failure)
 * - 不影响其他消息
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class MessageStoreEditDeleteTest {

    private lateinit var store: MessageStore

    @Before
    fun setUp() {
        store = MessageStore(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `editMessage updates text of existing message`() = runTest {
        val chatId = 8002_0001L
        store.saveMessage(msg(chatId, 1, "original"))
        store.saveMessage(msg(chatId, 2, "second"))

        val result = store.editMessage(chatId, messageId = 1, newText = "edited!")
        assertTrue("edit should succeed", result.isSuccess)

        val all = store.getMessages(chatId)
        val edited = all.first { it.messageId == 1L }
        assertEquals("edited!", edited.text)
        // 其他消息不变
        assertEquals("second", all.first { it.messageId == 2L }.text)
    }

    @Test
    fun `editMessage sets isEdited flag to true`() = runTest {
        val chatId = 8002_0002L
        store.saveMessage(msg(chatId, 1, "original"))

        store.editMessage(chatId, messageId = 1, newText = "edited")
        val edited = store.getMessages(chatId).first { it.messageId == 1L }
        assertTrue("isEdited should be true after edit", edited.isEdited)
    }

    @Test
    fun `editMessage returns failure when messageId not found`() = runTest {
        val chatId = 8002_0003L
        store.saveMessage(msg(chatId, 1, "only one"))

        val result = store.editMessage(chatId, messageId = 999, newText = "nope")
        assertTrue("edit should fail when messageId absent", result.isFailure)
    }

    @Test
    fun `editMessage preserves other message fields`() = runTest {
        val chatId = 8002_0004L
        store.saveMessage(
            TelegramMessage(
                messageId = 1L,
                chatId = chatId,
                chatTitle = "t",
                text = "old",
                fromName = "alice",
                date = 1_700_000_000L,
                isOutgoing = true,
                mediaType = "text",
            )
        )
        store.editMessage(chatId, messageId = 1, newText = "new")
        val m = store.getMessages(chatId).first()
        assertEquals("new", m.text)
        assertEquals("alice", m.fromName)
        assertTrue(m.isOutgoing)
        assertEquals(1_700_000_000L, m.date)
    }

    @Test
    fun `deleteMessage removes message from storage`() = runTest {
        val chatId = 8002_0010L
        store.saveMessage(msg(chatId, 1, "first"))
        store.saveMessage(msg(chatId, 2, "second"))
        store.saveMessage(msg(chatId, 3, "third"))

        val result = store.deleteMessage(chatId, messageId = 2)
        assertTrue("delete should succeed", result.isSuccess)

        val all = store.getMessages(chatId)
        assertEquals(2, all.size)
        assertFalse("messageId 2 should be gone", all.any { it.messageId == 2L })
        assertTrue("messageId 1 should remain", all.any { it.messageId == 1L })
        assertTrue("messageId 3 should remain", all.any { it.messageId == 3L })
    }

    @Test
    fun `deleteMessage returns failure when messageId not found`() = runTest {
        val chatId = 8002_0011L
        store.saveMessage(msg(chatId, 1, "only one"))

        val result = store.deleteMessage(chatId, messageId = 999)
        assertTrue("delete should fail when messageId absent", result.isFailure)

        // 现有消息不被影响
        assertEquals(1, store.getMessages(chatId).size)
    }

    @Test
    fun `deleteMessage on empty chat returns failure`() = runTest {
        val chatId = 8002_0012L
        val result = store.deleteMessage(chatId, messageId = 1)
        assertTrue("delete should fail on empty chat", result.isFailure)
    }

    private fun msg(chatId: Long, messageId: Long, text: String) = TelegramMessage(
        messageId = messageId,
        chatId = chatId,
        chatTitle = "t-$chatId",
        text = text,
        fromName = "tester",
        date = 1_700_000_000L + messageId,
    )
}
