package com.faster.tibot.ui.theme

import androidx.compose.ui.graphics.Color

// ===== Dark Mode (Telegram official) =====
val TgDarkChatBg = Color(0xFF17212b)
val TgDarkIncomingBubble = Color(0xFF182533)
val TgDarkIncomingBorder = Color(0xFF2a3a4a)
val TgDarkOutgoingBubble = Color(0xFF2b5278)
val TgDarkSurface = Color(0xFF1e2e3e)
val TgDarkHeader = Color(0xFF17212b)
val TgDarkPrimaryText = Color(0xFFffffff)
val TgDarkSecondaryText = Color(0xFF8899aa)
val TgDarkAccentBlue = Color(0xFF3390ec)
val TgDarkBadge = Color(0xFF3390ec)
val TgDarkSuccess = Color(0xFF2ecc71)
val TgDarkDanger = Color(0xFFe74c3c)
val TgDarkDivider = Color(0xFF1e2e3e)

// ===== Light Mode (Telegram official) =====
val TgLightChatBg = Color(0xFFe0e8f0)
val TgLightIncomingBubble = Color(0xFFffffff)
val TgLightOutgoingBubble = Color(0xFFeffddf)
val TgLightSurface = Color(0xFFffffff)
val TgLightHeader = Color(0xFFffffff)
val TgLightPrimaryText = Color(0xFF000000)
val TgLightSecondaryText = Color(0xFF8e8e93)
val TgLightAccentBlue = Color(0xFF3390ec)
val TgLightBadge = Color(0xFF3390ec)
val TgLightSuccess = Color(0xFF2ecc71)
val TgLightDanger = Color(0xFFe74c3c)
val TgLightDivider = Color(0xFFe0e0e0)

// ===== Avatar Gradients (Telegram official palette) =====
val TgAvatarGradients = listOf(
    listOf(Color(0xFF65AADD), Color(0xFF3D8AC4)), // 蓝
    listOf(Color(0xFFE17076), Color(0xFFC75A60)), // 红
    listOf(Color(0xFF7BC862), Color(0xFF5BA84A)), // 绿
    listOf(Color(0xFFE5CA59), Color(0xFFC7AC3A)), // 黄
    listOf(Color(0xFFA695E7), Color(0xFF8475C8)), // 紫
    listOf(Color(0xFFEE7AAE), Color(0xFFD16092)), // 粉
    listOf(Color(0xFF6EC9CB), Color(0xFF4FA9AB)), // 青
    listOf(Color(0xFFFFA559), Color(0xFFE58C40)), // 橙
)

/**
 * 根据 chatId 选一对 (start, end) 渐变色。
 * 与 ChatListScreen 中 avatarColor 保持一致。
 */
fun avatarGradient(chatId: Long): Pair<Color, Color> {
    val pair = TgAvatarGradients[(chatId.toInt() and 0x7FFFFFFF) % TgAvatarGradients.size]
    return pair[0] to pair[1]
}
