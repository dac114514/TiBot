package com.faster.tibot.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.faster.tibot.data.BotConnectionStore
import com.faster.tibot.data.ConnectionStatus
import com.faster.tibot.data.mqtt.MqttManager
import com.faster.tibot.data.proot.ProotManager
import kotlinx.coroutines.*
import org.json.JSONObject

class TiBotForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "tibot_bg_channel"
        const val NOTIFICATION_ID = 1
    }

    private lateinit var prootManager: ProotManager
    private val mqtt = MqttManager.getInstance()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var startupJob: Job? = null
    @Volatile
    private var isShuttingDown = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        prootManager = ProotManager(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Bot 启动中..."))

        if (!prootManager.isRootfsDeployed()) {
            val errMsg = prootManager.lastError.ifEmpty { "rootfs not deployed (sh not found)" }
            Log.e("TiBotService", "RootFS not deployed: $errMsg")
            BotConnectionStore.setStatus(ConnectionStatus.OFFLINE, errMsg)
            stopSelf()
            return START_NOT_STICKY
        }

        Log.i("TiBotService", "RootFS deployed, starting proot container...")
        BotConnectionStore.setStatus(ConnectionStatus.CONNECTING)

        // Cancel any previous startup to avoid concurrent startup sequences
        startupJob?.cancel()
        startupJob = serviceScope.launch {
            // Step 1: Start proot
            Log.i("TiBotService", "Calling prootManager.startProot()...")
            val proc = prootManager.startProot()
            if (proc == null) {
                val errMsg = prootManager.lastError.ifEmpty { "未知错误" }
                Log.e("TiBotService", "proot start returned null: $errMsg")
                updateNotification("Bot 启动失败")
                BotConnectionStore.setStatus(ConnectionStatus.CRASHED, "proot 启动失败: $errMsg")
                stopSelf()
                return@launch
            }
            Log.i("TiBotService", "proot process started successfully")

            // Step 2: Wait for bootstrap to complete (first boot only, up to 10 min)
            val bootstrapTimeout = 600
            if (!prootManager.isBootstrapDone()) {
                Log.i("TiBotService", "Bootstrap not done, waiting (up to ${bootstrapTimeout}s)...")
                for (elapsed in (bootstrapTimeout downTo 0 step 5)) {
                    delay(5_000)
                    if (!prootManager.isRunning()) {
                        val tail = prootManager.getLastOutputLines(8)
                        Log.e("TiBotService", "proot exited during bootstrap")
                        updateNotification("Bot 启动失败")
                        BotConnectionStore.setStatus(ConnectionStatus.CRASHED,
                            "proot exited during bootstrap\n$tail")
                        stopSelf()
                        return@launch
                    }
                    updateNotification("正在安装依赖... (${elapsed}s)")
                    if (prootManager.isBootstrapDone()) break
                }
                if (!prootManager.isBootstrapDone()) {
                    val tail = prootManager.getLastOutputLines(8)
                    Log.e("TiBotService", "bootstrap timeout after ${bootstrapTimeout}s")
                    updateNotification("Bot 启动失败")
                    BotConnectionStore.setStatus(ConnectionStatus.TIMEOUT,
                        "bootstrap 超时 (10min)\n$tail")
                    stopSelf()
                    return@launch
                }
                Log.i("TiBotService", "Bootstrap complete")
            }

            // Step 3: Connect MQTT (proot is ready)
            mqtt.connect()

            // Subscribe to bot status topic before waiting
            mqtt.subscribe("tibot/status")

            // Step 4: Monitor MQTT connection state — don't overwrite terminal states
            launch {
                mqtt.connectionState.collect { connected ->
                    if (!connected) {
                        // Only update to OFFLINE if not already in a terminal state
                        val current = BotConnectionStore.currentStatus
                        if (current != ConnectionStatus.TIMEOUT &&
                            current != ConnectionStatus.CRASHED) {
                            BotConnectionStore.setStatus(ConnectionStatus.OFFLINE, "MQTT 连接断开")
                        }
                        updateNotification("MQTT 连接断开")
                    }
                }
            }

            // Step 5: Wait for bot_running: true with 2 min timeout
            val ready = waitForBotReady(120_000L)
            if (!ready) {
                val isAlive = prootManager.isRunning()
                val prootTail = prootManager.getLastOutputLines(8)
                val errMsg = if (isAlive) {
                    "Bot 启动超时 (2min), alive=true\n$prootTail"
                } else {
                    "Bot 启动超时 (2min), alive=false\n$prootTail"
                }
                Log.e("TiBotService", "waitForBotReady timeout, alive=$isAlive, proot=$prootTail")
                updateNotification("Bot 启动失败")
                BotConnectionStore.setStatus(ConnectionStatus.TIMEOUT, errMsg)
                stopSelf()
                return@launch
            }

            // Step 6: Bot is online!
            BotConnectionStore.setStatus(ConnectionStatus.ONLINE)
            updateNotification("Bot 正在运行")

            // Start heartbeat (15s interval)
            startHeartbeat()

            // Monitor process exit
            monitorProcessExit(proc)
        }

        return START_STICKY
    }

    private suspend fun waitForBotReady(timeoutMs: Long): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val collectorJob = serviceScope.launch(Dispatchers.IO) {
            mqtt.messages.collect { event ->
                if (event.topic == "tibot/status") {
                    try {
                        val json = JSONObject(event.payload)
                        if (json.optBoolean("bot_running", false)) {
                            deferred.complete(true)
                            cancel()
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            collectorJob.cancel()
            false
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(15_000)
                mqtt.publish("tibot/cmd/ping", "{}")
            }
        }
    }

    private fun monitorProcessExit(proc: Process) {
        serviceScope.launch {
            try {
                proc.waitFor()
                // Process exited — unless we are intentionally shutting down, handle restart
                if (isShuttingDown) return@launch
                if (prootManager.canAutoRestart()) {
                    prootManager.incrementRestartCount()
                    BotConnectionStore.setStatus(ConnectionStatus.CONNECTING, "容器重启中")
                    startService(Intent(this@TiBotForegroundService, TiBotForegroundService::class.java))
                } else {
                    BotConnectionStore.setStatus(ConnectionStatus.CRASHED, "容器已崩溃，超过最大重启次数")
                }
            } catch (e: Exception) {
                Log.w("TiBotService", "monitorProcessExit error: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        isShuttingDown = true
        heartbeatJob?.cancel()
        serviceScope.cancel()
        prootManager.stopProot()
        mqtt.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("TiBot")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TiBot 后台服务",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "TiBot 后台运行通知" }
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
    }
}
