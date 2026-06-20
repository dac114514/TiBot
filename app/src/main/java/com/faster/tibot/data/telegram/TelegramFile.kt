package com.faster.tibot.data.telegram

data class TelegramFile(
    val fileId: String,
    val filePath: String,
    val fileSize: Long,
    val mimeType: String? = null,
)
