package com.faster.tibot.ui.autoreply

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AutoReplyRuleUi(
    val ruleId: String,
    val keyword: String,
    val reply: String,
    val matchType: String = "exact", // exact, contains, regex, command
    val enabled: Boolean = true,
)

class AutoReplyViewModel : ViewModel() {
    private val _rules = MutableStateFlow(listOf<AutoReplyRuleUi>())
    val rules = _rules.asStateFlow()

    init {
        // Load sample rules for demo
        _rules.value = listOf(
            AutoReplyRuleUi(ruleId = "1", keyword = "你好", reply = "你好！欢迎咨询！", matchType = "exact", enabled = true),
            AutoReplyRuleUi(ruleId = "2", keyword = "价格", reply = "基础版 ¥99/月", matchType = "contains", enabled = true),
            AutoReplyRuleUi(ruleId = "3", keyword = "联系客服", reply = "请发邮件至...", matchType = "regex", enabled = false),
            AutoReplyRuleUi(ruleId = "4", keyword = "/start", reply = "欢迎使用 TiBot！", matchType = "command", enabled = true),
        )
    }

    fun toggleRule(ruleId: String) {
        _rules.value = _rules.value.map {
            if (it.ruleId == ruleId) it.copy(enabled = !it.enabled) else it
        }
    }

    fun deleteRule(ruleId: String) {
        _rules.value = _rules.value.filter { it.ruleId != ruleId }
    }

    fun addRule(keyword: String, reply: String, matchType: String) {
        val newRule = AutoReplyRuleUi(
            ruleId = System.currentTimeMillis().toString(),
            keyword = keyword,
            reply = reply,
            matchType = matchType,
        )
        _rules.value = _rules.value + newRule
    }
}
