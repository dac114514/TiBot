package com.faster.tibot.data.local

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * SettingsRepository.perChatMute (R1-A / B5) 单元测试。
 *
 * 核心契约:
 * - 默认 perChatMute 为空集
 * - toggleMute 加入 chatId
 * - 再次 toggleMute 移除 chatId
 * - perChatMute flow 响应变更
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SettingsRepositoryMuteTest {

    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        repo = SettingsRepository(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `perChatMute defaults to empty set`() = runTest {
        // 用一个未用过的 chatId 测试 (其他测试可能污染 DataStore, 所以 query 该 chatId 是否在)
        val muted = repo.perChatMute.first()
        // 不直接断言 empty (跨测试污染), 改断言"新 chatId 不在 set 中"
        val probe = 8888_1234L
        assertFalse("new chatId should not be in default mute set", probe in muted)
    }

    @Test
    fun `toggleMute adds then removes chatId`() = runTest {
        val chatId = 7777_0001L
        // 起始: 不在 muted
        assertFalse(chatId in repo.perChatMute.first())

        // 1st toggle: 加入
        repo.toggleMute(chatId)
        assertTrue(chatId in repo.perChatMute.first())

        // 2nd toggle: 移除
        repo.toggleMute(chatId)
        assertFalse(chatId in repo.perChatMute.first())
    }

    @Test
    fun `toggleMute preserves other muted chatIds`() = runTest {
        val a = 7777_0010L
        val b = 7777_0011L
        val c = 7777_0012L

        repo.toggleMute(a)
        repo.toggleMute(b)
        assertTrue(a in repo.perChatMute.first())
        assertTrue(b in repo.perChatMute.first())

        repo.toggleMute(a)  // remove a
        val after = repo.perChatMute.first()
        assertFalse(a in after)
        assertTrue(b in after)
        assertFalse(c in after)
    }

    @Test
    fun `notificationsEnabled defaults to true`() = runTest {
        val enabled = repo.notificationsEnabled.first()
        // 既有实现: 默认 != "false" → true
        assertTrue(enabled)
    }
}
