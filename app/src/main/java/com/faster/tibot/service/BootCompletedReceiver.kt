package com.faster.tibot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.faster.tibot.data.local.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = SettingsRepository(appContext)
                val token = repo.botToken.first()
                if (token.isNotBlank()) {
                    val serviceIntent = Intent(appContext, BotForegroundService::class.java)
                        .putExtra(BotForegroundService.EXTRA_TOKEN, token)
                    ContextCompat.startForegroundService(appContext, serviceIntent)
                    Log.d(TAG, "service started after boot")
                } else {
                    Log.d(TAG, "no token configured, skipping boot start")
                }
            } catch (e: Exception) {
                Log.e(TAG, "boot handler failed: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
