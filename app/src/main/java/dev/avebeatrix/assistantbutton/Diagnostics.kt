package dev.avebeatrix.assistantbutton

import android.content.Context
import android.util.Log
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal object Diagnostics {
    private const val TAG = "AssistantButton"
    private const val HISTORY_LIMIT = 60
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun write(context: Context, message: String) {
        Log.i(TAG, message)
        val settings = Preferences(context)
        val line = "${LocalTime.now().format(timeFormat)} $message"
        val lines = settings.history.lineSequence().filter(String::isNotEmpty).toList()
        settings.history = (lines + line).takeLast(HISTORY_LIMIT).joinToString("\n")
    }
}
