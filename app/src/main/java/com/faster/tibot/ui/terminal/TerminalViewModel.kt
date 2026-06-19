package com.faster.tibot.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.mqtt.MqttManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class TerminalViewModel : ViewModel() {
    private val mqtt = MqttManager.getInstance()
    private val _history = MutableStateFlow(
        listOf("# TiBot Terminal", "# Ubuntu 24.04 LTS (proot)", "")
    )
    val history = _history.asStateFlow()
    private var pendingCommand: String? = null
    private var timeoutJob: Job? = null

    init {
        mqtt.subscribe("tibot/cmd/result")
        viewModelScope.launch {
            mqtt.messages.collect { event ->
                if (event.topic == "tibot/cmd/result") {
                    handleCommandResult(event.payload)
                }
            }
        }
    }

    fun executeCommand(cmd: String) {
        pendingCommand = cmd
        _history.value = _history.value + "$ $cmd" + "⏳ 执行中..."

        val payload = JSONObject().apply { put("command", cmd) }
        mqtt.publish("tibot/cmd/exec", payload.toString())

        // 30s timeout
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(30_000)
            if (pendingCommand == cmd) {
                _history.value = _history.value + "⚠ 命令超时 (30s)"
                pendingCommand = null
            }
        }
    }

    private fun handleCommandResult(payload: String) {
        val json = JSONObject(payload)
        val cmd = json.optString("command", pendingCommand ?: "")
        if (cmd != pendingCommand) return

        timeoutJob?.cancel()
        pendingCommand = null

        val stdout = json.optString("stdout", "")
        val stderr = json.optString("stderr", "")
        val returncode = json.optInt("returncode", -1)
        val error = json.optString("error", "")

        val newLines = mutableListOf<String>()
        if (error.isNotEmpty()) {
            newLines.add("Error: $error")
        } else {
            if (stdout.isNotEmpty()) newLines.add(stdout.trimEnd())
            if (stderr.isNotEmpty()) newLines.add("stderr: ${stderr.trimEnd()}")
            if (returncode != 0) newLines.add("(exit code: $returncode)")
        }
        _history.value = _history.value + newLines
    }

    fun clearHistory() {
        _history.value = listOf("# TiBot Terminal", "# Ubuntu 24.04 LTS (proot)", "")
    }

    override fun onCleared() {
        super.onCleared()
        mqtt.unsubscribe("tibot/cmd/result")
    }
}
