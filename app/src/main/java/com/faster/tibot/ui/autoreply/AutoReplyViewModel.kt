package com.faster.tibot.ui.autoreply

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.autoreply.AutoReplyEngine
import com.faster.tibot.data.autoreply.AutoReplyRule
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.message.MessageStore
import com.faster.tibot.data.telegram.TelegramBotClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AutoReplyRuleUi(
    val ruleId: String,
    val keyword: String,
    val reply: String,
    val matchType: String = "exact",
    val enabled: Boolean = true,
    val hitCount: Int = 0,
)

data class TestResult(
    val matched: Boolean,
    val matchedRuleId: String? = null,
    val reply: String? = null,
)

class AutoReplyViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)
    private var engine: AutoReplyEngine? = null

    private val _rules = MutableStateFlow(listOf<AutoReplyRuleUi>())
    val rules = _rules.asStateFlow()

    private val _testInput = MutableStateFlow("")
    val testInput = _testInput.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult = _testResult.asStateFlow()

    init {
        viewModelScope.launch {
            val token = settingsRepo.botToken.first()
            val client = if (token.isNotBlank()) TelegramBotClient(token) else null
            val store = MessageStore(application)
            engine = client?.let { AutoReplyEngine.getInstance(application, it, store) }
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

    fun enableAll() {
        _rules.value = _rules.value.map { it.copy(enabled = true) }
        viewModelScope.launch {
            engine?.saveRules(_rules.value.map { it.toEngine() })
        }
    }

    fun disableAll() {
        _rules.value = _rules.value.map { it.copy(enabled = false) }
        viewModelScope.launch {
            engine?.saveRules(_rules.value.map { it.toEngine() })
        }
    }

    fun clearAllRules() {
        _rules.value = emptyList()
        viewModelScope.launch {
            engine?.saveRules(emptyList())
        }
    }

    fun moveRule(fromIndex: Int, toIndex: Int) {
        val list = _rules.value.toMutableList()
        if (fromIndex < 0 || fromIndex >= list.size || toIndex < 0 || toIndex >= list.size) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _rules.value = list
        viewModelScope.launch {
            engine?.saveRules(list.map { it.toEngine() })
        }
    }

    fun setTestInput(text: String) {
        _testInput.value = text
    }

    fun testRules(text: String? = null) {
        val testText = text ?: _testInput.value
        if (testText.isBlank()) {
            _testResult.value = TestResult(matched = false)
            return
        }
        for (rule in _rules.value) {
            if (!rule.enabled) continue
            val matched = when (rule.matchType) {
                "exact" -> testText.equals(rule.keyword, ignoreCase = true)
                "contains" -> testText.contains(rule.keyword, ignoreCase = true)
                "regex" -> try {
                    Regex(rule.keyword).containsMatchIn(testText)
                } catch (_: Exception) {
                    false
                }
                "command" -> testText.trimStart().startsWith("/${rule.keyword}", ignoreCase = true)
                else -> testText.contains(rule.keyword, ignoreCase = true)
            }
            if (matched) {
                _testResult.value = TestResult(
                    matched = true,
                    matchedRuleId = rule.ruleId,
                    reply = rule.reply,
                )
                return
            }
        }
        _testResult.value = TestResult(matched = false)
    }
}

private fun AutoReplyRule.toUi() = AutoReplyRuleUi(
    ruleId = ruleId,
    keyword = keyword,
    reply = reply,
    matchType = matchType,
    enabled = enabled,
    hitCount = hitCount,
)

private fun AutoReplyRuleUi.toEngine() = AutoReplyRule(
    ruleId = ruleId,
    keyword = keyword,
    reply = reply,
    matchType = matchType,
    enabled = enabled,
    hitCount = hitCount,
)
