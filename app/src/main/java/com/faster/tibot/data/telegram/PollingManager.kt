package com.faster.tibot.data.telegram

import com.faster.tibot.data.autoreply.AutoReplyEngine
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.message.MessageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PollingManager(
    private val botClient: TelegramBotClient,
    private val messageStore: MessageStore,
    private val autoReplyEngine: AutoReplyEngine,
    private val settingsRepo: SettingsRepository,
    private val fileDownloader: FileDownloader,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            BotState.clearError()
            val me = botClient.getMe()
            if (me != null) {
                BotState.update(me.firstName, me.userName ?: "")
                settingsRepo.saveBotInfo(me.firstName, me.userName ?: "")
            } else if (BotState.info.value.errorReason == null) {
                BotState.setError("Token 无效或网络不可达")
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

                        val mode = settingsRepo.accessMode.first()
                        val admin = settingsRepo.adminId.first()
                        val authorized = isAuthorized(msg, mode, admin)

                        val toSave = if (authorized) msg else msg.copy(isBlocked = true)
                        messageStore.saveMessage(toSave)

                        if (authorized) {
                            autoReplyEngine.processMessage(msg)
                            if (msg.fileId.isNotBlank()) {
                                val deferred = fileDownloader.ensureDownloaded(msg, scope)
                                deferred.invokeOnCompletion { result ->
                                    val path = result.getOrNull() ?: return@invokeOnCompletion
                                    if (path != "too_large" && path.isNotBlank()) {
                                        scope.launch {
                                            messageStore.updateLocalFilePath(msg.chatId, msg.messageId, path)
                                        }
                                    }
                                }
                            }
                        }
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

    private fun isAuthorized(msg: TelegramMessage, accessMode: String, adminId: Long): Boolean {
        if (accessMode == "all") return true
        if (adminId == 0L) return false

        val fromId = msg.fromId
        return when (msg.chatType) {
            "private" -> fromId == adminId
            "channel" -> true
            "group", "supergroup" -> fromId == adminId
            else -> false
        }
    }
}
