package com.faster.tibot.ui.autoreply

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.autoreply.AutoReplyEngine
import com.faster.tibot.data.autoreply.AutoReplyRule
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.telegram.TelegramBotClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AutoReplyRuleUi(
    val ruleId: String,
    val keyword: String,
    val reply: String,
    val matchType: String = "exact", // exact, contains, regex, command
    val enabled: Boolean = true,
)

class AutoReplyViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)
    private var engine: AutoReplyEngine? = null

    private val _rules = MutableStateFlow(listOf<AutoReplyRuleUi>())
    val rules = _rules.asStateFlow()

    init {
        viewModelScope.launch {
            val token = settingsRepo.botToken.first()
            val client = if (token.isNotBlank()) TelegramBotClient(token) else null
            engine = client?.let { AutoReplyEngine.getInstance(application, it) }
            _rules.value = engine?.loadRules()?.map { it.toUi() } ?: emptyList()
        }
    }

    fun toggleRule(ruleId: String) {
        _rules.value = _rules.value.map {
            if (it.ruleId == ruleId) it.copy(enabled = !it.enabled) else it
        }
        viewModelScope.launch {
            engine?.saveRules(_rules.value.map { it.toEngine() })
        }
    }

    fun deleteRule(ruleId: String) {
        _rules.value = _rules.value.filter { it.ruleId != ruleId }
        viewModelScope.launch {
            engine?.saveRules(_rules.value.map { it.toEngine() })
        }
    }

    fun addRule(keyword: String, reply: String, matchType: String) {
        val ruleId = System.currentTimeMillis().toString()
        val newRule = AutoReplyRuleUi(
            ruleId = ruleId,
            keyword = keyword,
            reply = reply,
            matchType = matchType,
        )
        _rules.value = _rules.value + newRule
        viewModelScope.launch {
            engine?.saveRules(_rules.value.map { it.toEngine() })
        }
    }
}

private fun AutoReplyRule.toUi() = AutoReplyRuleUi(ruleId, keyword, reply, matchType, enabled)
private fun AutoReplyRuleUi.toEngine() = AutoReplyRule(ruleId, keyword, reply, matchType, enabled)
