package com.faster.tibot.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TerminalScreen(vm: TerminalViewModel = viewModel()) {
    val history by vm.history.collectAsState()
    var input by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(history.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "终端",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { vm.clearHistory() }) {
                Text("清屏", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            history.forEach { entry ->
                Text(
                    text = entry,
                    color = when {
                        entry.startsWith("$ ") -> MaterialTheme.colorScheme.primary
                        entry.startsWith("# ") -> MaterialTheme.colorScheme.onSurfaceVariant
                        entry.startsWith("⏳") -> MaterialTheme.colorScheme.secondary
                        entry.contains("error", ignoreCase = true) ||
                            entry.contains("Error") -> MaterialTheme.colorScheme.error
                        else -> Color(0xFFe8f0fe)
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp).background(MaterialTheme.colorScheme.background),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$ ",
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            )
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = Color(0xFFe8f0fe),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (input.isNotBlank()) {
                        vm.executeCommand(input.trim())
                        input = ""
                    }
                }),
                placeholder = {
                    Text(
                        "输入命令...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                singleLine = true,
            )
        }
    }
}
