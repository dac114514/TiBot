package com.faster.tibot.ui.autoreply

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// Match type badge colors
private val MatchTypeBlue = Color(0xFF3390ec)
private val MatchTypeGreen = Color(0xFF2ecc71)
private val MatchTypeOrange = Color(0xFFf39c12)
private val MatchTypePurple = Color(0xFF9b59b6)

private fun matchTypeLabel(matchType: String): String = when (matchType) {
    "exact" -> "精确匹配"
    "contains" -> "包含匹配"
    "regex" -> "正则匹配"
    "command" -> "命令匹配"
    else -> matchType
}

private fun matchTypeColor(matchType: String): Color = when (matchType) {
    "exact" -> MatchTypeBlue
    "contains" -> MatchTypeGreen
    "regex" -> MatchTypeOrange
    "command" -> MatchTypePurple
    else -> MatchTypeBlue
}

private fun matchTypeEmoji(matchType: String): String = when (matchType) {
    "exact" -> "🎯"
    "contains" -> "🔍"
    "regex" -> "⚡"
    "command" -> "🤖"
    else -> "💬"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoReplyScreen(vm: AutoReplyViewModel = viewModel()) {
    val rules by vm.rules.collectAsState()
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AutoReplyRuleUi?>(null) }
    var deletingRule by remember { mutableStateOf<AutoReplyRuleUi?>(null) }

    // Filter rules by keyword
    val filteredRules = remember(rules, searchQuery.text) {
        val query = searchQuery.text.trim()
        if (query.isEmpty()) {
            rules
        } else {
            rules.filter { it.keyword.contains(query, ignoreCase = true) }
        }
    }

    // Add rule dialog
    if (showAddDialog) {
        RuleEditDialog(
            title = "添加规则",
            initialKeyword = "",
            initialReply = "",
            initialMatchType = "exact",
            onConfirm = { keyword, reply, matchType ->
                vm.addRule(keyword, reply, matchType)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    // Edit rule dialog
    editingRule?.let { rule ->
        RuleEditDialog(
            title = "编辑规则",
            initialKeyword = rule.keyword,
            initialReply = rule.reply,
            initialMatchType = rule.matchType,
            onConfirm = { keyword, reply, matchType ->
                vm.deleteRule(rule.ruleId)
                vm.addRule(keyword, reply, matchType)
                editingRule = null
            },
            onDismiss = { editingRule = null },
        )
    }

    // Delete confirm dialog
    deletingRule?.let { rule ->
        DeleteConfirmDialog(
            rule = rule,
            onConfirm = {
                vm.deleteRule(rule.ruleId)
                deletingRule = null
            },
            onDismiss = { deletingRule = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自动回复", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加规则",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Search bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
            )

            Spacer(Modifier.height(12.dp))

            if (filteredRules.isEmpty()) {
                // Empty state
                EmptyState(
                    hasSearch = searchQuery.text.isNotBlank(),
                )
            } else {
                // Rule list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = filteredRules,
                        key = { it.ruleId },
                    ) { rule ->
                        RuleCard(
                            rule = rule,
                            onClick = { editingRule = rule },
                            onToggle = { vm.toggleRule(rule.ruleId) },
                            onDelete = { deletingRule = rule },
                        )
                    }
                    // Bottom spacer for FAB area
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "搜索关键词...",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun RuleCard(
    rule: AutoReplyRuleUi,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Emoji icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (rule.enabled)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = matchTypeEmoji(rule.matchType),
                    fontSize = 20.sp,
                )
            }

            Spacer(Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.keyword,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (rule.enabled)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    // Match type badge
                    MatchTypeBadge(matchType = rule.matchType)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = rule.reply,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rule.enabled)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Toggle switch
            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.padding(start = 4.dp),
            )
            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除规则",
                    tint = Color(0xFFe74c3c).copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun MatchTypeBadge(matchType: String) {
    val bgColor = matchTypeColor(matchType)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = matchTypeLabel(matchType),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = bgColor,
            fontSize = 11.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditDialog(
    title: String,
    initialKeyword: String,
    initialReply: String,
    initialMatchType: String,
    onConfirm: (keyword: String, reply: String, matchType: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyword by remember { mutableStateOf(TextFieldValue(initialKeyword)) }
    var reply by remember { mutableStateOf(TextFieldValue(initialReply)) }
    var selectedMatchType by remember { mutableStateOf(initialMatchType) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val matchTypeOptions = listOf(
        "exact" to "精确匹配",
        "contains" to "包含匹配",
        "regex" to "正则匹配",
        "command" to "命令匹配",
    )

    val isValid = keyword.text.isNotBlank() && reply.text.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("关键词") },
                    placeholder = { Text("输入触发关键词") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reply,
                    onValueChange = { reply = it },
                    label = { Text("回复内容") },
                    placeholder = { Text("输入自动回复内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(12.dp))
                // Match type dropdown
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = matchTypeLabel(selectedMatchType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("匹配类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(8.dp),
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                    ) {
                        matchTypeOptions.forEach { (type, label) ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = matchTypeEmoji(type),
                                            fontSize = 16.sp,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(text = label)
                                    }
                                },
                                onClick = {
                                    selectedMatchType = type
                                    dropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(keyword.text.trim(), reply.text.trim(), selectedMatchType)
                },
                enabled = isValid,
            ) {
                Text(
                    text = "保存",
                    color = if (isValid)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(
    rule: AutoReplyRuleUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("确认删除", fontWeight = FontWeight.Bold)
        },
        text = {
            Text("确定要删除规则「${rule.keyword}」吗？此操作不可撤销。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "删除",
                    color = Color(0xFFe74c3c),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun EmptyState(hasSearch: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (hasSearch) Icons.Filled.SearchOff else Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (hasSearch) "没有匹配的规则" else "暂无自动回复规则",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (hasSearch) "试试其他关键词" else "点击右上角 + 添加新规则",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}
