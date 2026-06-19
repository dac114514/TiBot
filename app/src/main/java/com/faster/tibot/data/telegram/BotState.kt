package com.faster.tibot.data.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BotState {
    data class BotInfo(
        val firstName: String = "",
        val username: String = "",
        val isOnline: Boolean = false,
    )

    private val _info = MutableStateFlow(BotInfo())
    val info: StateFlow<BotInfo> = _info.asStateFlow()

    fun update(firstName: String, username: String) {
        _info.value = BotInfo(firstName, username, true)
    }

    fun setOnline(online: Boolean) {
        _info.value = _info.value.copy(isOnline = online)
    }
}
