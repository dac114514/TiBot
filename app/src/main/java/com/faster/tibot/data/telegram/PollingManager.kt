package com.faster.tibot.data.telegram

import com.faster.tibot.data.autoreply.AutoReplyEngine
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
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            var offset = 0L
            while (isActive) {
                val updates = botClient.getUpdates(offset)
                for (update in updates) {
                    offset = update.updateId + 1
                    val msg = update.message ?: continue
                    messageStore.saveMessage(msg)
                    autoReplyEngine.processMessage(msg)
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
