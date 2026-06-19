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
import com.faster.tibot.ui.theme.*

@Composable
fun TerminalScreen() {
    var history by remember { mutableStateOf(listOf(
        "# TiBot Terminal v0.1",
        "# Ubuntu 24.04 LTS (proot) | Python 3.12",
        "",
        "$ systemctl status mosquitto",
        "● mosquitto.service - MQTT Broker",
        "   Active: active (running)",
        "",
        "$ python3 --version",
        "Python 3.12.3",
    )) }
    var input by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0a0f16))) {
        // Title bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("终端", color = Color.White, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { history = emptyList() }) {
                Text("清屏", color = TgDarkAccentBlue, fontSize = 13.sp)
            }
        }

        // Terminal output
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            history.forEach { line ->
                val isPrompt = line.startsWith("$ ")
                val isComment = line.startsWith("# ")
                Text(
                    text = line,
                    color = when {
                        isPrompt -> TgDarkSuccess
                        isComment -> TgDarkSecondaryText
                        else -> Color(0xFFe8f0fe)
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                )
            }
        }

        // Input line
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp).background(Color(0xFF0a0f16)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$ ", color = TgDarkSuccess, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = Color(0xFFe8f0fe), fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (input.isNotBlank()) {
                        history = history + "\$ $input" + "⏳ 执行中..."
                        // TODO: send via MQTT tibot/cmd/exec
                        input = ""
                    }
                }),
                placeholder = { Text("输入命令...", color = TgDarkSecondaryText, fontFamily = FontFamily.Monospace) },
                singleLine = true,
            )
        }
    }
}
