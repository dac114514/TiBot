package com.faster.tibot.ui.autoreply

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.mqtt.MqttManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class AutoReplyRuleUi(
    val ruleId: String,
    val keyword: String,
    val reply: String,
    val matchType: String = "exact", // exact, contains, regex, command
    val enabled: Boolean = true,
)

class AutoReplyViewModel(application: Application) : AndroidViewModel(application) {
    private val mqtt = MqttManager.getInstance()

    private val _rules = MutableStateFlow(listOf<AutoReplyRuleUi>())
    val rules = _rules.asStateFlow()

    init {
        viewModelScope.launch { mqtt.connect() }

        // Subscribe to rule list updates
        mqtt.subscribe("tibot/autoreply/list")

        // Listen for incoming MQTT messages
        viewModelScope.launch {
            mqtt.messages.collect { event ->
                when (event.topic) {
                    "tibot/autoreply/list" -> handleRuleList(event.payload)
                }
            }
        }

        // Request current rules from backend
        mqtt.publish("tibot/autoreply/get", JSONObject().apply {
            put("action", "get_all")
        }.toString())

        // Rules are loaded from backend via MQTT, no hardcoded fallback
    }

    private fun handleRuleList(payload: String) {
        try {
            val json = JSONObject(payload)
            val rulesArray = json.optJSONArray("rules") ?: return
            val ruleList = mutableListOf<AutoReplyRuleUi>()
            for (i in 0 until rulesArray.length()) {
                val ruleObj = rulesArray.getJSONObject(i)
                ruleList.add(
                    AutoReplyRuleUi(
                        ruleId = ruleObj.optString("rule_id", ruleObj.optString("id", i.toString())),
                        keyword = ruleObj.optString("keyword", ""),
                        reply = ruleObj.optString("reply", ""),
                        matchType = ruleObj.optString("match_type", "exact"),
                        enabled = ruleObj.optBoolean("enabled", true),
                    )
                )
            }
            if (ruleList.isNotEmpty()) {
                _rules.value = ruleList
            }
        } catch (_: Exception) {
            // Ignore malformed JSON
        }
    }

    fun toggleRule(ruleId: String) {
        // Optimistically update local state
        _rules.value = _rules.value.map {
            if (it.ruleId == ruleId) it.copy(enabled = !it.enabled) else it
        }

        // Publish toggle to backend
        val rule = _rules.value.find { it.ruleId == ruleId } ?: return
        val envelope = JSONObject().apply {
            put("action", "set")
            put("rule_id", ruleId)
            put("keyword", rule.keyword)
            put("reply", rule.reply)
            put("match_type", rule.matchType)
            put("enabled", rule.enabled)
        }
        mqtt.publish("tibot/autoreply/set", envelope.toString())
    }

    fun deleteRule(ruleId: String) {
        // Optimistically remove from local state
        _rules.value = _rules.value.filter { it.ruleId != ruleId }

        // Publish delete to backend
        val envelope = JSONObject().apply {
            put("action", "delete")
            put("rule_id", ruleId)
        }
        mqtt.publish("tibot/autoreply/delete", envelope.toString())
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

        // Publish new rule to backend
        val envelope = JSONObject().apply {
            put("action", "set")
            put("rule_id", ruleId)
            put("keyword", keyword)
            put("reply", reply)
            put("match_type", matchType)
            put("enabled", true)
        }
        mqtt.publish("tibot/autoreply/set", envelope.toString())
    }

    override fun onCleared() {
        super.onCleared()
        mqtt.unsubscribe("tibot/autoreply/list")
    }
}
