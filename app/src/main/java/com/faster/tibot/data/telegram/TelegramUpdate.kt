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
    val isAutoReply: Boolean = false,
    val fileId: String = "",
    val fileSize: Long = 0L,
    val mimeType: String = "",
    val mediaType: String = "text",
    val localFilePath: String = "",
    val ruleId: String = "",
    val replyToMessageId: Long = 0L,
    /**
     * 该消息是否被编辑过 (R1-B / B4 引入)。
     * 默认 false — 老数据无此字段, 向前兼容。
     * 编辑成功后, MessageStore.editMessage 会把此位置 true。
     */
    val isEdited: Boolean = false,
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
            put("isAutoReply", isAutoReply)
            put("fileId", fileId)
            put("fileSize", fileSize)
            put("mimeType", mimeType)
            put("mediaType", mediaType)
            put("localFilePath", localFilePath)
            put("ruleId", ruleId)
            put("replyToMessageId", replyToMessageId)
            put("isEdited", isEdited)
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
                isAutoReply = o.optBoolean("isAutoReply", false),
                fileId = o.optString("fileId", ""),
                fileSize = o.optLong("fileSize", 0L),
                mimeType = o.optString("mimeType", ""),
                mediaType = o.optString("mediaType", "text"),
                localFilePath = o.optString("localFilePath", ""),
                ruleId = o.optString("ruleId", ""),
                replyToMessageId = o.optLong("replyToMessageId", 0L),
                isEdited = o.optBoolean("isEdited", false),
            )
        }
    }
}

data class TelegramUpdate(
    val updateId: Long,
    val message: TelegramMessage?,
)
