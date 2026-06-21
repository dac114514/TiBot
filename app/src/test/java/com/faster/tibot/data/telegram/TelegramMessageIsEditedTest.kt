package com.faster.tibot.data.telegram

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TelegramMessage.isEdited 字段 (R1-B 引入) 的序列化测试。
 *
 * 核心契约:
 * - isEdited 默认 false (向老数据兼容)
 * - toJson() 写入 isEdited 字段
 * - fromJson() 读取 isEdited 字段
 * - 老 JSON 没有 isEdited 键时, fromJson 默认 false (不抛异常, 向前兼容)
 */
class TelegramMessageIsEditedTest {

    @Test
    fun `isEdited defaults to false`() {
        val msg = TelegramMessage(
            messageId = 1L,
            chatId = 100L,
            chatTitle = "t",
            text = "hi",
            fromName = "a",
            date = 1700_000_000L,
        )
        assertFalse(msg.isEdited)
    }

    @Test
    fun `isEdited can be set to true`() {
        val msg = TelegramMessage(
            messageId = 1L,
            chatId = 100L,
            chatTitle = "t",
            text = "hi",
            fromName = "a",
            date = 1700_000_000L,
            isEdited = true,
        )
        assertTrue(msg.isEdited)
    }

    @Test
    fun `toJson writes isEdited field`() {
        val msg = TelegramMessage(
            messageId = 1L,
            chatId = 100L,
            chatTitle = "t",
            text = "hi",
            fromName = "a",
            date = 1700_000_000L,
            isEdited = true,
        )
        val json = msg.toJson()
        assertTrue("json should contain isEdited", json.has("isEdited"))
        assertTrue("isEdited should be true", json.getBoolean("isEdited"))
    }

    @Test
    fun `fromJson reads isEdited field`() {
        val json = JSONObject().apply {
            put("messageId", 1L)
            put("chatId", 100L)
            put("chatTitle", "t")
            put("text", "hi")
            put("fromName", "a")
            put("date", 1700_000_000L)
            put("isEdited", true)
        }
        val msg = TelegramMessage.fromJson(json)
        assertTrue(msg.isEdited)
    }

    @Test
    fun `fromJson without isEdited key defaults to false (forward compat)`() {
        // 老数据没有 isEdited 字段, fromJson 不应抛异常, 默认 false
        val json = JSONObject().apply {
            put("messageId", 1L)
            put("chatId", 100L)
            put("chatTitle", "t")
            put("text", "hi")
            put("fromName", "a")
            put("date", 1700_000_000L)
        }
        val msg = TelegramMessage.fromJson(json)
        assertFalse(msg.isEdited)
    }
}
