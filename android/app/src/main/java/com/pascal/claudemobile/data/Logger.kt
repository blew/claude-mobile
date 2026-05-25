package com.pascal.claudemobile.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory + on-disk log so the user can read recent activity without adb.
 * Logs persist across app launches up to MAX_LINES; file rotates at MAX_BYTES.
 */
object Logger {
    private const val MAX_LINES = 1000
    private const val MAX_BYTES = 512 * 1024 // 512 KB

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    private var logFile: File? = null
    private val lock = Any()

    fun init(context: Context) {
        synchronized(lock) {
            val f = File(context.filesDir, "debug.log")
            logFile = f
            if (f.exists()) {
                _entries.value = f.readLines().takeLast(MAX_LINES)
            }
        }
    }

    fun log(tag: String, message: String) {
        val line = "[${timeFmt.format(Date())}] [$tag] $message"
        Log.d("BlewBridge", line)
        synchronized(lock) {
            val updated = (_entries.value + line).takeLast(MAX_LINES)
            _entries.value = updated
            logFile?.let { f ->
                try {
                    if (f.length() > MAX_BYTES) {
                        f.writeText(updated.joinToString("\n") + "\n")
                    } else {
                        f.appendText("$line\n")
                    }
                } catch (_: Exception) { }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            _entries.value = emptyList()
            logFile?.writeText("")
        }
    }

    fun snapshot(): String = synchronized(lock) { _entries.value.joinToString("\n") }
}
