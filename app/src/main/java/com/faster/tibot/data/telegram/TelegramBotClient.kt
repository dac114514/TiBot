package com.faster.tibot.data.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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
            val json = JSONObject(resp.body()!!.string())
            if (!json.getBoolean("ok")) return@withContext null
            val user = json.getJSONObject("result")
            BotUser(
                id = user.optLong("id"),
                firstName = user.optString("first_name", ""),
                userName = user.optString("username"),
            )
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

    suspend fun sendMessage(chatId: Long, text: String) = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
            }
            val req = Request.Builder().url("$baseUrl/sendMessage")
                .post(body.toString().toRequestBody(jsonMedia)).build()
            client.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

    suspend fun sendMessage(chatId: Long, text: String, replyTo: Long) =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text)
                    put("reply_to_message_id", replyTo)
                }
                val req = Request.Builder().url("$baseUrl/sendMessage")
                    .post(body.toString().toRequestBody(jsonMedia)).build()
                client.newCall(req).execute().close()
            } catch (_: Exception) {
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
        return TelegramMessage(
            messageId = json.optLong("message_id", 0),
            chatId = chat?.optLong("id", 0) ?: 0,
            chatTitle = chat?.optString("title")
                ?: chat?.optString("first_name")
                ?: chat?.optString("username")
                ?: "",
            text = json.optString("text", json.optString("caption", "")),
            fromName = from?.optString("first_name")
                ?: from?.optString("username")
                ?: "",
            date = json.optLong("date", 0),
        )
    }
}
