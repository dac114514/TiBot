package com.faster.tibot.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionStatus { CONNECTING, ONLINE, OFFLINE, TIMEOUT, CRASHED }

data class BotConnectionState(
    val status: ConnectionStatus = ConnectionStatus.CONNECTING,
    val reason: String = "",
)

object BotConnectionStore {
    private val _state = MutableStateFlow(BotConnectionState())
    val state = _state.asStateFlow()

    val currentStatus: ConnectionStatus get() = _state.value.status

    fun setStatus(status: ConnectionStatus, reason: String = "") {
        _state.value = BotConnectionState(status, reason)
    }
}
