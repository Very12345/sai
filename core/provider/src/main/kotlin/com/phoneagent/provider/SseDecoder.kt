package com.phoneagent.provider

data class SseEvent(
    val event: String? = null,
    val data: String,
    val id: String? = null,
)

class SseDecoder {
    private var event: String? = null
    private var id: String? = null
    private val data = mutableListOf<String>()

    fun accept(line: String): SseEvent? {
        if (line.isEmpty()) return flush()
        if (line.startsWith(':')) return null
        val separator = line.indexOf(':')
        val field = if (separator < 0) line else line.substring(0, separator)
        val value = if (separator < 0) "" else line.substring(separator + 1).removePrefix(" ")
        when (field) {
            "event" -> event = value
            "data" -> data += value
            "id" -> id = value
        }
        return null
    }

    fun finish(): SseEvent? = flush()

    private fun flush(): SseEvent? {
        if (data.isEmpty()) {
            event = null
            id = null
            return null
        }
        return SseEvent(event = event, data = data.joinToString("\n"), id = id).also {
            event = null
            id = null
            data.clear()
        }
    }
}

