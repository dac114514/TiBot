package com.faster.tibot.ui.chats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faster.tibot.ui.theme.avatarGradient

/**
 * Telegram 风格渐变头像
 * - 基于 chatId 哈希选 8 对 Telegram 官方渐变色之一 (背景稳定不变)
 * - 中心显示首字符 (自动 uppercase, 空字符串回退 "?")
 * - 支持 1-2 字符 (如群组 "Project Team" → "PT")
 * - 兼容 emoji label (take(2) 对 emoji 也是安全的 1-2 个码元)
 */
@Composable
fun TgAvatar(
    chatId: Long,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    fontSize: TextUnit = 22.sp,
    imagePath: String? = null,
) {
    val (start, end) = avatarGradient(chatId)
    val displayLabel = if (label.isBlank()) "?" else label.take(2).uppercase()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(start, end))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayLabel,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
        )
    }
}
