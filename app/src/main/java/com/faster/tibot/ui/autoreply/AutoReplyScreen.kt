package com.faster.tibot.ui.autoreply

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@Composable
private fun matchTypeColor(matchType: String): Color = when (matchType) {
    "exact" -> MaterialTheme.colorScheme.primary
    "contains" -> MaterialTheme.colorScheme.tertiary
    "regex" -> MaterialTheme.colorScheme.secondary
    "command" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.primary
}

private fun matchTypeLabel(matchType: String): String = when (matchType) {
    "exact" -> "精确匹配"
    "contains" -> "包含匹配"
    "regex" -> "正则匹配"
    "command" -> "命令匹配"
    else -> matchType
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
    var showTestDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val filteredRules = if (searchQuery.text.isBlank()) {
        rules
    } else {
        rules.filter { it.keyword.contains(searchQuery.text, ignoreCase = true) }
    }

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

    deletingRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { deletingRule = null },
            title = { Text("确认删除", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除规则「${rule.keyword}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteRule(rule.ruleId)
                        deletingRule = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRule = null }) { Text("取消") }
            },
        )
    }

    if (showTestDialog) {
        TestRuleDialog(vm = vm, onDismiss = { showTestDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("自动回复", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                actions = {
                    IconButton(onClick = { showTestDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "测试规则",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "更多",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部启用") },
                                onClick = {
                                    menuExpanded = false
                                    vm.enableAll()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("全部禁用") },
                                onClick = {
                                    menuExpanded = false
                                    vm.disableAll()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("导入规则") },
                                onClick = { menuExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text("导出规则") },
                                onClick = { menuExpanded = false },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("清空全部规则") },
                                onClick = {
                                    menuExpanded = false
                                    vm.clearAllRules()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("添加") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
            )

            Spacer(Modifier.height(12.dp))

            if (filteredRules.isEmpty()) {
                EmptyState(hasSearch = searchQuery.text.isNotBlank())
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredRules, key = { it.ruleId }) { rule ->
                        RuleCard(
                            rule = rule,
                            onClick = { editingRule = rule },
                            onToggle = { vm.toggleRule(rule.ruleId) },
                            onDelete = { deletingRule = rule },
                        )
                    }
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
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = matchTypeEmoji(rule.matchType),
                    fontSize = 20.sp,
                )
            }

            Spacer(Modifier.width(12.dp))

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
                if (rule.hitCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "命中 ${rule.hitCount} 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.padding(start = 4.dp),
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除规则",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
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

@Composable
private fun TestRuleDialog(vm: AutoReplyViewModel, onDismiss: () -> Unit) {
    val input by vm.testInput.collectAsState()
    val result by vm.testResult.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("测试规则匹配", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { vm.setTestInput(it) },
                    label = { Text("模拟消息") },
                    placeholder = { Text("输入要测试的消息内容") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                )
                Spacer(Modifier.height(8.dp))
                result?.let { r ->
                    if (r.matched) {
                        Text(
                            text = "✅ 匹配: ${r.reply}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text = "❌ 无规则匹配",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.testRules() }) { Text("测试") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun EmptyState(hasSearch: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (hasSearch) Icons.Filled.SearchOff else Icons.Filled.AutoAwesome,
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
                text = if (hasSearch) "试试其他关键词" else "点击右下角 + 添加新规则",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
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
        title = { Text(title, fontWeight = FontWeight.Bold) },
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
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = matchTypeLabel(selectedMatchType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("匹配类型") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                        },
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
                onClick = { onConfirm(keyword.text.trim(), reply.text.trim(), selectedMatchType) },
                enabled = isValid,
            ) {
                Text(
                    "保存",
                    color = if (isValid)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
