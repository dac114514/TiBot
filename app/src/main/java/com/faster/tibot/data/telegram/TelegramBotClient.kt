package com.faster.tibot.data.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class BotUser(val id: Long, val firstName: String, val userName: String?)

class TelegramBotClient(private val token: String) {
    private val baseUrl = "https://api.telegram.org/bot$token"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun validate(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/getMe").get().build()
            val resp = client.newCall(req).execute()
            resp.use { res ->
                res.isSuccessful && JSONObject(res.body!!.string()).getBoolean("ok")
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getMe(): BotUser? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/getMe").get().build()
            val resp = client.newCall(req).execute()
            val json = JSONObject(resp.body!!.string())
            if (!json.getBoolean("ok")) return@withContext null
            val user = json.getJSONObject("result")
            val botUser = BotUser(
                id = user.optLong("id"),
                firstName = user.optString("first_name", ""),
                userName = user.optString("username"),
            )
            BotState.update(
                firstName = botUser.firstName,
                username = botUser.userName ?: "",
                botId = botUser.id,
            )
            botUser
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getUpdates(offset: Long = 0, timeout: Int = 20): List<TelegramUpdate> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("offset", offset)
                    put("timeout", timeout)
                    put("allowed_updates", JSONArray(listOf("message")))
                }
                val req = Request.Builder().url("$baseUrl/getUpdates")
                    .post(body.toString().toRequestBody(jsonMedia)).build()
                val resp = client.newCall(req).execute()
                resp.use { res ->
                    val json = JSONObject(res.body!!.string())
                    if (!json.getBoolean("ok")) return@withContext emptyList()
                    val arr = json.getJSONArray("result")
                    (0 until arr.length()).map { i -> parseUpdate(arr.getJSONObject(i)) }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun getFile(fileId: String): TelegramFile? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("file_id", fileId) }
            val req = Request.Builder().url("$baseUrl/getFile")
                .post(body.toString().toRequestBody(jsonMedia)).build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val bodyText = res.body?.string().orEmpty()
                val json = JSONObject(bodyText)
                if (!json.optBoolean("ok", false)) return@withContext null
                val result = json.optJSONObject("result") ?: return@withContext null
                TelegramFile(
                    fileId = result.optString("file_id", fileId),
                    filePath = result.optString("file_path", ""),
                    fileSize = result.optLong("file_size", 0L),
                    mimeType = null,
                )
            }
        } catch (_: Exception) { null }
    }

    suspend fun downloadFile(filePath: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (filePath.isBlank()) return@withContext false
            val url = "https://api.telegram.org/file/bot$token/$filePath"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext false
                val src = res.body?.byteStream() ?: return@withContext false
                dest.parentFile?.mkdirs()
                dest.outputStream().use { out -> src.copyTo(out, 8 * 1024) }
                dest.length() > 0
            }
        } catch (_: Exception) { false }
    }

    suspend fun sendMessage(
        chatId: Long,
        text: String,
        replyToMessageId: Long? = null,
    ): Result<Long?> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
                if (replyToMessageId != null && replyToMessageId > 0L) {
                    put("reply_to_message_id", replyToMessageId)
                }
            }
            val req = Request.Builder().url("$baseUrl/sendMessage")
                .post(body.toString().toRequestBody(jsonMedia)).build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("HTTP ${res.code}")
                val bodyText = res.body?.string().orEmpty()
                val json = JSONObject(bodyText)
                if (!json.optBoolean("ok", false)) error("sendMessage failed: $bodyText")
                json.getJSONObject("result").optLong("message_id")
            }
        }
    }

    suspend fun sendDocument(
        chatId: Long,
        filePath: String,
        caption: String = "",
    ): Result<Long?> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(filePath)
            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart(
                    "document",
                    file.name,
                    file.asRequestBody("application/octet-stream".toMediaType())
                )
                .apply { if (caption.isNotBlank()) addFormDataPart("caption", caption) }
                .build()
            val req = Request.Builder().url("$baseUrl/sendDocument")
                .post(requestBody).build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("HTTP ${res.code}")
                val bodyText = res.body?.string().orEmpty()
                val json = JSONObject(bodyText)
                if (!json.optBoolean("ok", false)) error("sendDocument failed: $bodyText")
                json.getJSONObject("result").optLong("message_id")
            }
        }
    }

    private fun parseUpdate(json: JSONObject): TelegramUpdate {
        val updateId = json.getLong("update_id")
        val message = parseMessage(json.optJSONObject("message"))
        return TelegramUpdate(updateId, message)
    }

    private fun parseMessage(json: JSONObject?): TelegramMessage? {
        if (json == null) return null
        val chat = json.optJSONObject("chat")
        val from = json.optJSONObject("from")
        val chatType = chat?.optString("type", "private") ?: "private"
        val messageId = json.optLong("message_id", 0)
        val chatId = chat?.optLong("id", 0) ?: 0L
        val chatTitle = chat?.optString("title")
            ?: chat?.optString("first_name")
            ?: chat?.optString("username")
            ?: ""
        val text = json.optString("text").takeIf { it.isNotBlank() }
            ?: json.optString("caption").takeIf { it.isNotBlank() }
            ?: ""
        val fromName = from?.optString("first_name")?.takeIf { it.isNotEmpty() }
            ?: from?.optString("username")?.takeIf { it.isNotEmpty() }
            ?: json.optString("author_signature", "").takeIf { it.isNotEmpty() }
            ?: ""
        val date = json.optLong("date", 0)
        val fromId = from?.optLong("id", 0L) ?: 0L

        val media = extractMedia(json)

        return TelegramMessage(
            messageId = messageId,
            chatId = chatId,
            chatTitle = chatTitle,
            text = text,
            fromName = fromName,
            date = date,
            fileName = media.fileName,
            fromId = fromId,
            chatType = chatType,
            isOutgoing = false,
            isBlocked = false,
            fileId = media.fileId,
            fileSize = media.fileSize,
            mimeType = media.mimeType,
            mediaType = media.mediaType,
            localFilePath = "",
        )
    }

    private data class ExtractedMedia(
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val mediaType: String,
    )

    private fun extractMedia(json: JSONObject): ExtractedMedia {
        json.optJSONObject("document")?.let { d ->
            return ExtractedMedia(
                fileId = d.optString("file_id", ""),
                fileName = d.optString("file_name", ""),
                fileSize = d.optLong("file_size", 0L),
                mimeType = d.optString("mime_type", ""),
                mediaType = "document",
            )
        }
        json.optJSONArray("photo")?.let { arr ->
            if (arr.length() > 0) {
                val largest = arr.getJSONObject(arr.length() - 1)
                return ExtractedMedia(
                    fileId = largest.optString("file_id", ""),
                    fileName = "photo_${System.currentTimeMillis()}.jpg",
                    fileSize = largest.optLong("file_size", 0L),
                    mimeType = "image/jpeg",
                    mediaType = "photo",
                )
            }
        }
        json.optJSONObject("video")?.let { v ->
            return ExtractedMedia(
                fileId = v.optString("file_id", ""),
                fileName = v.optString("file_name", ""),
                fileSize = v.optLong("file_size", 0L),
                mimeType = v.optString("mime_type", ""),
                mediaType = "video",
            )
        }
        json.optJSONObject("video_note")?.let { v ->
            return ExtractedMedia(
                fileId = v.optString("file_id", ""),
                fileName = "video_note_${v.optString("file_id", "")}.mp4",
                fileSize = v.optLong("file_size", 0L),
                mimeType = "video/mp4",
                mediaType = "video_note",
            )
        }
        json.optJSONObject("audio")?.let { a ->
            return ExtractedMedia(
                fileId = a.optString("file_id", ""),
                fileName = a.optString("file_name", ""),
                fileSize = a.optLong("file_size", 0L),
                mimeType = a.optString("mime_type", ""),
                mediaType = "audio",
            )
        }
        json.optJSONObject("voice")?.let { v ->
            return ExtractedMedia(
                fileId = v.optString("file_id", ""),
                fileName = "voice_${v.optString("file_id", "")}.ogg",
                fileSize = v.optLong("file_size", 0L),
                mimeType = v.optString("mime_type", "audio/ogg"),
                mediaType = "voice",
            )
        }
        json.optJSONObject("animation")?.let { a ->
            return ExtractedMedia(
                fileId = a.optString("file_id", ""),
                fileName = a.optString("file_name", ""),
                fileSize = a.optLong("file_size", 0L),
                mimeType = a.optString("mime_type", ""),
                mediaType = "animation",
            )
        }
        json.optJSONObject("sticker")?.let { s ->
            return ExtractedMedia(
                fileId = s.optString("file_id", ""),
                fileName = "sticker_${s.optString("file_id", "")}.webp",
                fileSize = s.optLong("file_size", 0L),
                mimeType = "image/webp",
                mediaType = "sticker",
            )
        }
        return ExtractedMedia("", "", 0L, "", "text")
    }
}
