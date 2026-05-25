package com.pascal.claudemobile.data

import android.content.Context
import com.pascal.claudemobile.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ClaudeRepository(context: Context) {

    private val prefs = context.getSharedPreferences("claude_prefs", Context.MODE_PRIVATE)

    val serverUrl: String
        get() = prefs.getString("server_url", BuildConfig.SERVER_URL) ?: BuildConfig.SERVER_URL

    val apiKey: String
        get() = prefs.getString("api_key", BuildConfig.API_KEY) ?: BuildConfig.API_KEY

    fun saveSettings(url: String, key: String) {
        prefs.edit()
            .putString("server_url", url.trimEnd('/'))
            .putString("api_key", key)
            .apply()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    fun sendMessage(
        text: String,
        isNewSession: Boolean,
        onChunk: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val body = JSONObject()
            .put("text", text)
            .put("new_session", isNewSession)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$serverUrl/message")
            .addHeader("X-API-Key", apiKey)
            .post(body)
            .build()

        Logger.log("HTTP", "POST $serverUrl/message new_session=$isNewSession len=${text.length}")

        try {
            val response = client.newCall(request).execute()
            Logger.log("HTTP", "Response code=${response.code}")
            if (!response.isSuccessful) {
                val body = response.body?.string()?.take(500) ?: ""
                Logger.log("HTTP", "Error body: $body")
                onError("Server returned ${response.code}")
                return
            }
            val reader = response.body?.byteStream()?.bufferedReader()
                ?: run {
                    Logger.log("HTTP", "Empty response body")
                    onError("Empty response body"); return
                }

            reader.forEachLine { line ->
                if (line.startsWith("data: ")) {
                    try {
                        val json = JSONObject(line.removePrefix("data: "))
                        when (json.getString("type")) {
                            "text" -> {
                                val content = json.getString("content")
                                Logger.log("SSE", "text chunk: ${content.take(120)}…")
                                onChunk(content)
                            }
                            "done" -> {
                                Logger.log("SSE", "done")
                                onDone()
                            }
                            "error" -> {
                                val err = json.optString("content", "Unknown error")
                                Logger.log("SSE", "error: $err")
                                onError(err)
                            }
                        }
                    } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) {
            Logger.log("HTTP", "EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            onError(e.message ?: "Connection failed")
        }
    }
}
