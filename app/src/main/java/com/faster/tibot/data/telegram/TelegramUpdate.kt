package com.faster.tibot.data.telegram

import org.json.JSONObject

data class TelegramMessage(
    val messageId: Long,
    val chatId: Long,
    val chatTitle: String,
    val text: String,
    val fromName: String,
    val date: Long,
    val fileName: String = "",
    val fromId: Long = 0L,
    val chatType: String = "private",
    val isOutgoing: Boolean = false,
    val isBlocked: Boolean = false,
    val fileId: String = "",
    val fileSize: Long = 0L,
    val mimeType: String = "",
    val mediaType: String = "text",
    val localFilePath: String = "",
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("messageId", messageId)
            put("chatId", chatId)
            put("chatTitle", chatTitle)
            put("text", text)
            put("fromName", fromName)
            put("date", date)
            put("fileName", fileName)
            put("fromId", fromId)
            put("chatType", chatType)
            put("isOutgoing", isOutgoing)
            put("isBlocked", isBlocked)
            put("fileId", fileId)
            put("fileSize", fileSize)
            put("mimeType", mimeType)
            put("mediaType", mediaType)
            put("localFilePath", localFilePath)
        }
    }

    companion object {
        fun fromJson(o: JSONObject): TelegramMessage {
            return TelegramMessage(
                messageId = o.optLong("messageId", 0),
                chatId = o.optLong("chatId", 0),
                chatTitle = o.optString("chatTitle", ""),
                text = o.optString("text", ""),
                fromName = o.optString("fromName", ""),
                date = o.optLong("date", 0),
                fileName = o.optString("fileName", ""),
                fromId = o.optLong("fromId", 0L),
                chatType = o.optString("chatType", "private"),
                isOutgoing = o.optBoolean("isOutgoing", false),
                isBlocked = o.optBoolean("isBlocked", false),
                fileId = o.optString("fileId", ""),
                fileSize = o.optLong("fileSize", 0L),
                mimeType = o.optString("mimeType", ""),
                mediaType = o.optString("mediaType", "text"),
                localFilePath = o.optString("localFilePath", ""),
            )
        }
    }
}

data class TelegramUpdate(
    val updateId: Long,
    val message: TelegramMessage?,
)
