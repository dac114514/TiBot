package com.faster.tibot.ui.autoreply

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AutoReplyRuleUi(
    val ruleId: String,
    val keyword: String,
    val reply: String,
    val matchType: String = "exact", // exact, contains, regex, command
    val enabled: Boolean = true,
)

class AutoReplyViewModel(application: Application) : AndroidViewModel(application) {
    private val _rules = MutableStateFlow(listOf<AutoReplyRuleUi>())
    val rules = _rules.asStateFlow()

    // TODO: Wire up AutoReplyEngine and MessageStore from Task 3 / Task 2
    // private val autoReplyEngine = AutoReplyEngine(botClient)
    // private val messageStore = MessageStore(application)

    fun toggleRule(ruleId: String) {
        _rules.value = _rules.value.map {
            if (it.ruleId == ruleId) it.copy(enabled = !it.enabled) else it
        }
    }

    fun deleteRule(ruleId: String) {
        _rules.value = _rules.value.filter { it.ruleId != ruleId }
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
    }
}
