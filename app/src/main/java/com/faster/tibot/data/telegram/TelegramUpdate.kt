package com.faster.tibot.data.telegram

data class TelegramMessage(
    val messageId: Long,
    val chatId: Long,
    val chatTitle: String,
    val text: String,
    val fromName: String,
    val date: Long,
    val fileName: String = "",
)

data class TelegramUpdate(
    val updateId: Long,
    val message: TelegramMessage?,
)
