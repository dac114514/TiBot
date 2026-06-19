package com.faster.tibot.data.telegram

import com.faster.tibot.data.autoreply.AutoReplyEngine
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.message.MessageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PollingManager(
    private val botClient: TelegramBotClient,
    private val messageStore: MessageStore,
    private val autoReplyEngine: AutoReplyEngine,
    private val settingsRepo: SettingsRepository,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            // Fetch bot info on startup
            val me = botClient.getMe()
            if (me != null) {
                BotState.update(me.firstName, me.userName ?: "")
                settingsRepo.saveBotInfo(me.firstName, me.userName ?: "")
            }

            var offset = settingsRepo.getUpdateOffset()
            var retryDelay = 1000L

            while (isActive) {
                try {
                    val updates = botClient.getUpdates(offset)
                    for (update in updates) {
                        offset = update.updateId + 1
                        settingsRepo.saveUpdateOffset(offset)
                        val msg = update.message ?: continue
                        messageStore.saveMessage(msg)
                        autoReplyEngine.processMessage(msg)
                    }
                    BotState.setOnline(true)
                    retryDelay = 1000L
                } catch (e: Exception) {
                    BotState.setOnline(false)
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
                }
                delay(2000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
