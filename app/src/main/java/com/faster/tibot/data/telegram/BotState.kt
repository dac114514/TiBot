package com.faster.tibot.data.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BotState {
    data class BotInfo(
        val firstName: String = "",
        val username: String = "",
        val botId: Long = 0L,
        val isOnline: Boolean = false,
        val errorReason: String? = null,
    )

    private val _info = MutableStateFlow(BotInfo())
    val info: StateFlow<BotInfo> = _info.asStateFlow()

    @Volatile
    var startTimeEpoch: Long = 0L
        private set

    fun update(firstName: String, username: String, botId: Long) {
        _info.value = BotInfo(firstName, username, botId, true, null)
        if (startTimeEpoch == 0L) startTimeEpoch = System.currentTimeMillis()
    }

    fun setOnline(online: Boolean) {
        _info.value = _info.value.copy(isOnline = online)
    }

    fun setError(reason: String) {
        _info.value = _info.value.copy(isOnline = false, errorReason = reason)
    }

    fun clearError() {
        _info.value = _info.value.copy(errorReason = null)
    }

    fun reset() {
        startTimeEpoch = 0L
        _info.value = BotInfo()
    }
}
