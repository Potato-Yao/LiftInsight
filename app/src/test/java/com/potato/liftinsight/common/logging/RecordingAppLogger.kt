package com.potato.liftinsight.common.logging

data class LogEntry(
    val level: String,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)

class RecordingAppLogger : AppLogger {
    private val entries = mutableListOf<LogEntry>()

    override fun debug(tag: String, message: String) {
        entries += LogEntry(level = "debug", tag = tag, message = message)
    }

    override fun warn(tag: String, message: String) {
        entries += LogEntry(level = "warn", tag = tag, message = message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        entries += LogEntry(level = "error", tag = tag, message = message, throwable = throwable)
    }

    fun entries(): List<LogEntry> {
        return entries.toList()
    }
}
