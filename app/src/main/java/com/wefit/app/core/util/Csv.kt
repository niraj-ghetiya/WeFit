package com.wefit.app.core.util

object Csv {
    fun escape(value: String?): String {
        if (value == null) return ""
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    fun row(vararg values: Any?): String = values.joinToString(",") { escape(it?.toString()) }
}
