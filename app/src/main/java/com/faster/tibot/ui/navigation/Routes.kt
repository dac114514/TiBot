package com.faster.tibot.ui.navigation

object Routes {
    const val CHATS = "chats"
    const val CHAT_DETAIL = "chat_detail/{chatId}"
    const val AUTO_REPLY = "auto_reply"
    const val TERMINAL = "terminal"
    const val SETTINGS = "settings"
    const val WIZARD = "wizard"

    fun chatDetail(chatId: Long) = "chat_detail/$chatId"
}
