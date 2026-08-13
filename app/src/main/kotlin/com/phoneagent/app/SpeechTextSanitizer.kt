package com.phoneagent.app

import android.icu.text.UnicodeSet

/** Text shown in the conversation is untouched; only the TTS copy is reduced and redacted. */
object SpeechTextSanitizer {
    private val emojiSet by lazy {
        UnicodeSet("[[:Emoji_Presentation:][:Extended_Pictographic:]\\uFE0E\\uFE0F\\u200D]").freeze()
    }

    fun clean(text: String, sentenceLimit: Int = 2): String {
        val redacted = text
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("(?m)^\\s*(?:diff --git|@@|[+]{3}|-{3}).*$"), " ")
            .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("(?i)(?:api[_-]?key|authorization|bearer|token|secret)\\s*[:=]\\s*\\S+"), " ")
            .replace(Regex("[`#*_>\\[\\]{}]"), " ")
        val withoutEmoji = StringBuilder(redacted.length)
        redacted.codePoints().forEach { codePoint ->
            if (!emojiSet.contains(codePoint) && codePoint != 0x20E3) withoutEmoji.appendCodePoint(codePoint)
        }
        return withoutEmoji.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(Regex("(?<=[。！？.!?])\\s*"))
            .filter(String::isNotBlank)
            .take(sentenceLimit)
            .joinToString(" ")
    }
}
