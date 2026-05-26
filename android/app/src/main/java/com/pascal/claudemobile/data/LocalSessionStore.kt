package com.pascal.claudemobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * File-per-session JSON cache in app-internal storage.
 *
 * Mirrors the on-disk layout Claude Code uses on the laptop so synced
 * conversations build up over time. Each session lives at
 * `filesDir/sessions/<id>.json` and contains both the list metadata
 * (so the history drawer renders without a network round-trip) and
 * the full message history (so resuming a session works offline).
 */
class LocalSessionStore(context: Context) {

    private val dir: File = File(context.filesDir, "sessions").apply { mkdirs() }

    fun save(entry: SessionListEntry, messages: List<Message>) {
        val payload = JSONObject().apply {
            put("id", entry.id)
            put("title", entry.title)
            put("modified", entry.modified)
            put("turns", entry.turns)
            put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().apply {
                        put("role", if (m.role == Role.USER) "user" else "assistant")
                        put("content", m.content)
                    })
                }
            })
        }
        File(dir, "${entry.id}.json").writeText(payload.toString())
    }

    fun listEntries(): List<SessionListEntry> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { readEntry(it) }
            ?.sortedByDescending { it.modified }
            ?: emptyList()

    fun loadMessages(sessionId: String): List<Message> {
        val file = File(dir, "$sessionId.json")
        if (!file.exists()) return emptyList()
        return try {
            val obj = JSONObject(file.readText())
            val arr = obj.optJSONArray("messages") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val role = if (m.optString("role") == "user") Role.USER else Role.ASSISTANT
                    add(Message(role = role, content = m.optString("content")))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun localModified(sessionId: String): Long? {
        val obj = readJson(File(dir, "$sessionId.json")) ?: return null
        return obj.optLong("modified", -1L).takeIf { it >= 0 }
    }

    /** Delete sessions whose `modified` is older than [olderThanEpochSeconds]. Returns count deleted. */
    fun purgeOlderThan(olderThanEpochSeconds: Long): Int {
        var n = 0
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { f ->
            val entry = readEntry(f) ?: return@forEach
            if (entry.modified < olderThanEpochSeconds && f.delete()) n++
        }
        return n
    }

    private fun readEntry(f: File): SessionListEntry? {
        val obj = readJson(f) ?: return null
        val id = obj.optString("id").ifEmpty { return null }
        return SessionListEntry(
            id = id,
            title = obj.optString("title", "(untitled)"),
            modified = obj.optLong("modified", 0L),
            turns = obj.optInt("turns", 0),
        )
    }

    private fun readJson(f: File): JSONObject? = try {
        if (f.exists()) JSONObject(f.readText()) else null
    } catch (_: Exception) {
        null
    }
}
