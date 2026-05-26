package com.pascal.claudemobile.data

import android.content.Context
import com.pascal.claudemobile.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
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
        .readTimeout(600, TimeUnit.SECONDS)
        .build()

    /**
     * POST /message. The proxy may emit:
     *   {"type":"session","id":sid}  — captured tab's session id (once)
     *   {"type":"text","content":...} — streaming text chunk (repeats)
     *   {"type":"error","content":...}
     *   {"type":"done"}
     */
    fun sendMessage(
        text: String,
        sessionId: String?,
        onSession: (String) -> Unit,
        onChunk: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        Logger.log("HTTP", "POST $serverUrl/message session=${sessionId ?: "<new>"} len=${text.length}")

        val body = JSONObject().apply {
            put("text", text)
            if (sessionId != null) put("session_id", sessionId)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$serverUrl/message")
            .addHeader("X-API-Key", apiKey)
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            Logger.log("HTTP", "Response code=${response.code}")
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(500) ?: ""
                Logger.log("HTTP", "Error body: $errBody")
                onError("Server returned ${response.code}")
                return
            }
            val reader = response.body?.byteStream()?.bufferedReader()
                ?: run { onError("Empty response body"); return }

            reader.forEachLine { line ->
                if (!line.startsWith("data: ")) return@forEachLine
                try {
                    val json = JSONObject(line.removePrefix("data: "))
                    when (json.getString("type")) {
                        "text" -> onChunk(json.getString("content"))
                        "session" -> onSession(json.getString("id"))
                        "done" -> onDone()
                        "error" -> onError(json.optString("content", "Unknown error"))
                    }
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            Logger.log("HTTP", "EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            onError(e.message ?: "Connection failed")
        }
    }

    /** GET /health — no auth required. Returns a human-readable status string for logging. */
    fun checkHealth(): String {
        return try {
            val request = Request.Builder()
                .url("$serverUrl/health")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                "Proxy OK — $body"
            } else {
                "Proxy /health returned ${response.code}: $body"
            }
        } catch (e: Exception) {
            "Proxy unreachable: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    fun listSessions(): List<SessionListEntry> {
        Logger.log("HTTP", "GET $serverUrl/sessions")
        val request = Request.Builder()
            .url("$serverUrl/sessions")
            .addHeader("X-API-Key", apiKey)
            .get()
            .build()
        return try {
            val response = client.newCall(request).execute()
            val code = response.code
            if (!response.isSuccessful) {
                val hint = when (code) {
                    502 -> "Proxy offline — restart Task Scheduler tasks"
                    401 -> "Wrong API key — check Settings"
                    504 -> "Tunnel timeout — devtunnel may be down"
                    else -> "Unexpected error"
                }
                val body = response.body?.string()?.take(200).orEmpty()
                Logger.log("HTTP", "Sessions failed code=$code ($hint) body=$body")
                return emptyList()
            }
            Logger.log("HTTP", "Sessions response code=$code")
            val arr = JSONArray(response.body?.string().orEmpty())
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        SessionListEntry(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            modified = obj.getLong("modified"),
                            turns = obj.optInt("turns", 0),
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Logger.log("HTTP", "listSessions EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }

    fun getSessionMessages(sessionId: String): List<Message> {
        Logger.log("HTTP", "GET $serverUrl/sessions/$sessionId")
        val request = Request.Builder()
            .url("$serverUrl/sessions/$sessionId")
            .addHeader("X-API-Key", apiKey)
            .get()
            .build()
        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val obj = JSONObject(response.body?.string().orEmpty())
            val arr = obj.optJSONArray("messages") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val role = if (m.optString("role") == "user") Role.USER else Role.ASSISTANT
                    add(Message(role = role, content = m.optString("content")))
                }
            }
        } catch (e: Exception) {
            Logger.log("HTTP", "getSessionMessages EXCEPTION: ${e.message}")
            emptyList()
        }
    }
}
