package com.faster.tibot.data.telegram

import android.content.Context
import android.util.Log
import com.faster.tibot.data.autoreply.AutoReplyEngine
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.message.MessageStore
import com.faster.tibot.service.NotificationFactory
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
    /**
     * 用于弹系统通知的 application context (R1-A / B5 引入)。
     * 不持有 Activity context, 避免泄漏。
     */
    private val appContext: Context,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            BotState.clearError()
            val me = botClient.getMe()
            if (me != null) {
                BotState.update(me.firstName, me.userName ?: "", me.id)
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
                        val admins = settingsRepo.adminIds.first()
                        val authorized = isAuthorized(msg, mode, admins)
                        val isAdminOfThisChat = authorized && admins.contains(msg.fromId)
                        val botId = BotState.info.value.botId

                        // 防御性：bot 自己的消息通过 getUpdates 收到时，标记为 outgoing 并跳过 autoReply
                        if (botId != 0L && msg.fromId == botId) {
                            messageStore.saveMessage(msg.copy(isOutgoing = true), isAdminOfThisChat)
                            continue
                        }

                        val toSave = if (authorized) msg else msg.copy(isBlocked = true)
                        messageStore.saveMessage(toSave, isAdminOfThisChat)

                        if (authorized) {
                            autoReplyEngine.processMessage(msg)
                            if (msg.fileId.isNotBlank()) {
                                val deferred = fileDownloader.ensureDownloaded(msg, scope)
                                deferred.invokeOnCompletion {
                                    if (deferred.isCancelled) return@invokeOnCompletion
                                    val path = runCatching { deferred.getCompleted() }.getOrNull() ?: return@invokeOnCompletion
                                    if (path != "too_large" && path.isNotBlank()) {
                                        scope.launch {
                                            messageStore.updateLocalFilePath(msg.chatId, msg.messageId, path)
                                        }
                                    }
                                }
                            }
                            // R1-A / B5: 弹系统通知 (在新协程中跑, 避免阻塞 polling 循环)。
                            // 静默: 全局禁用 / 该 chat 静音 / 出错 都吞异常, 不影响主流程。
                            scope.launch {
                                runCatching {
                                    if (shouldNotify(msg.chatId)) {
                                        val sender = msg.fromName.ifBlank { msg.chatTitle.ifBlank { "新消息" } }
                                        NotificationFactory.showMessageNotification(
                                            context = appContext,
                                            senderName = sender,
                                            text = msg.text,
                                            chatId = msg.chatId,
                                        )
                                    }
                                }.onFailure { Log.w(TAG, "notify failed chatId=${msg.chatId}: ${it.message}") }
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

    private suspend fun shouldNotify(chatId: Long): Boolean {
        val enabled = settingsRepo.notificationsEnabled.first()
        if (!enabled) return false
        val muted = settingsRepo.perChatMute.first()
        if (chatId in muted) return false
        return true
    }

    private fun isAuthorized(msg: TelegramMessage, accessMode: String, adminIds: List<Long>): Boolean {
        if (accessMode == "all") return true
        if (adminIds.isEmpty() || adminIds.all { it == 0L }) return false

        val fromId = msg.fromId
        return when (msg.chatType) {
            "private" -> fromId in adminIds
            "channel" -> true
            "group", "supergroup" -> fromId in adminIds
            else -> false
        }
    }

    private companion object {
        private const val TAG = "PollingManager"
    }
}
