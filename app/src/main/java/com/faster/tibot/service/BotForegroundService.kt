package com.faster.tibot.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.faster.tibot.data.autoreply.AutoReplyEngine
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.message.MessageStore
import com.faster.tibot.data.telegram.BotState
import com.faster.tibot.data.telegram.FileDownloader
import com.faster.tibot.data.telegram.PollingManager
import com.faster.tibot.data.telegram.TelegramBotClient
import com.faster.tibot.service.NotificationFactory
import com.faster.tibot.util.FileUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BotForegroundService : Service() {

    private var serviceScope: CoroutineScope? = null
    private var pollingManager: PollingManager? = null
    private var fileDownloader: FileDownloader? = null
    private var messageStore: MessageStore? = null
    private var settingsRepo: SettingsRepository? = null
    private var currentToken: String? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository(applicationContext)
        messageStore = MessageStore(applicationContext)
        serviceScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, e ->
                    Log.e(TAG, "serviceScope crash: ${e.message}", e)
                }
        )
        runCatching { FileUtils.clearOldSentCache(applicationContext) }
            .onFailure { Log.w(TAG, "clearOldSentCache failed: ${it.message}") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = settingsRepo
        if (repo == null) {
            Log.w(TAG, "settingsRepo null, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        val intentToken = intent?.getStringExtra(EXTRA_TOKEN)
        if (intentToken.isNullOrBlank()) {
            Log.w(TAG, "no token in intent, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        val token = intentToken

        val notif = NotificationFactory.build(this, BotState.info.value)
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
            ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

        val store = messageStore
        val scope = serviceScope
        if (store == null || scope == null) {
            Log.w(TAG, "store/scope null, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        if (pollingManager != null && currentToken == token) {
            return START_STICKY
        }

        if (pollingManager == null || currentToken != token) {
            pollingManager?.stop()
            val botClient = TelegramBotClient(token)
            AutoReplyEngine.resetInstance(applicationContext, botClient)
            val engine = AutoReplyEngine.getInstance(applicationContext, botClient)
            val downloader = FileDownloader(botClient, applicationContext)
            fileDownloader = downloader
            pollingManager = PollingManager(
                botClient, store, engine, repo, downloader
            )
            currentToken = token
        }

        scope.launch { pollingManager?.start(this) }

        scope.launch {
            val pending = store.getAllFileNeedingDownload()
            val downloader = fileDownloader ?: return@launch
            pending.forEach { msg ->
                val deferred = downloader.ensureDownloaded(msg, this) ?: return@forEach
                deferred.invokeOnCompletion {
                    if (deferred.isCancelled) return@invokeOnCompletion
                    val path = runCatching { deferred.getCompleted() }.getOrNull() ?: return@invokeOnCompletion
                    if (path != "too_large" && path.isNotBlank()) {
                        serviceScope?.launch {
                            store.updateLocalFilePath(msg.chatId, msg.messageId, path)
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        runCatching {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
            ServiceCompat.startForeground(
                this, NOTIF_ID,
                NotificationFactory.build(this, BotState.info.value), type,
            )
        }.onFailure { Log.w(TAG, "re-promote foreground failed: ${it.message}") }
    }

    override fun onDestroy() {
        pollingManager?.stop()
        serviceScope?.cancel()
        serviceScope = null
        pollingManager = null
        fileDownloader = null
        currentToken = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BotForegroundService"
        const val EXTRA_TOKEN = "extra_token"
        const val WORK_TAG = "tibot_keepalive"

        fun start(context: Context, token: String) {
            val intent = Intent(context, BotForegroundService::class.java)
                .putExtra(EXTRA_TOKEN, token)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
