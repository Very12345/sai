package com.phoneagent.provider

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun ModelMessage.openAiResponsesContent(): JsonElement = buildJsonArray {
    normalizedParts().forEach { part -> when (part) {
        is ModelContentPart.Text -> add(buildJsonObject {
            put("type", if (role == MessageRole.ASSISTANT) "output_text" else "input_text")
            put("text", part.text)
        })
        is ModelContentPart.Image -> add(buildJsonObject {
            put("type", "input_image")
            put("detail", part.detail)
            put("image_url", part.remoteUrl ?: "data:${part.mimeType};base64,${part.base64Data.orEmpty()}")
        })
        is ModelContentPart.Audio -> add(buildJsonObject {
            put("type", "input_file")
            put("filename", "voice.${part.format}")
            put("file_data", "data:${part.mimeType};base64,${part.base64Data}")
        })
        is ModelContentPart.FileAttachment -> add(buildJsonObject {
            put("type", "input_file")
            put("filename", part.fileName)
            put("file_data", "data:${part.mimeType};base64,${part.base64Data}")
        })
    } }
}

internal fun ModelMessage.openAiChatContent(): JsonElement {
    val parts = normalizedParts()
    if (parts.all { it is ModelContentPart.Text }) return kotlinx.serialization.json.JsonPrimitive(textContent())
    return buildJsonArray {
        parts.forEach { part -> when (part) {
            is ModelContentPart.Text -> add(buildJsonObject { put("type", "text"); put("text", part.text) })
            is ModelContentPart.Image -> add(buildJsonObject {
                put("type", "image_url")
                put("image_url", buildJsonObject {
                    put("url", part.remoteUrl ?: "data:${part.mimeType};base64,${part.base64Data.orEmpty()}")
                    put("detail", part.detail)
                })
            })
            is ModelContentPart.Audio -> add(buildJsonObject {
                put("type", "input_audio")
                put("input_audio", buildJsonObject {
                    put("data", part.base64Data)
                    put("format", part.format)
                })
            })
            is ModelContentPart.FileAttachment -> add(buildJsonObject {
                put("type", "file")
                put("file", buildJsonObject {
                    put("filename", part.fileName)
                    put("file_data", "data:${part.mimeType};base64,${part.base64Data}")
                })
            })
        } }
    }
}

internal fun ModelMessage.anthropicContent(): JsonElement = buildJsonArray {
    normalizedParts().forEach { part -> when (part) {
        is ModelContentPart.Text -> add(buildJsonObject { put("type", "text"); put("text", part.text) })
        is ModelContentPart.Image -> add(buildJsonObject {
            put("type", "image")
            if (part.base64Data != null) put("source", buildJsonObject {
                put("type", "base64"); put("media_type", part.mimeType); put("data", part.base64Data)
            }) else put("source", buildJsonObject { put("type", "url"); put("url", part.remoteUrl.orEmpty()) })
        })
        is ModelContentPart.Audio -> add(buildJsonObject {
            put("type", "document")
            put("source", buildJsonObject {
                put("type", "base64"); put("media_type", part.mimeType); put("data", part.base64Data)
            })
            put("title", "voice.${part.format}")
        })
        is ModelContentPart.FileAttachment -> add(buildJsonObject {
            put("type", "document")
            put("source", buildJsonObject {
                put("type", "base64"); put("media_type", part.mimeType); put("data", part.base64Data)
            })
            put("title", part.fileName)
        })
    } }
}

internal fun ModelMessage.geminiParts(): JsonElement = buildJsonArray {
    normalizedParts().forEach { part -> when (part) {
        is ModelContentPart.Text -> add(buildJsonObject { put("text", part.text) })
        is ModelContentPart.Image -> if (part.base64Data != null) add(buildJsonObject {
            put("inlineData", buildJsonObject { put("mimeType", part.mimeType); put("data", part.base64Data) })
        }) else add(buildJsonObject { put("fileData", buildJsonObject {
            put("mimeType", part.mimeType); put("fileUri", part.remoteUrl.orEmpty())
        }) })
        is ModelContentPart.Audio -> add(buildJsonObject {
            put("inlineData", buildJsonObject { put("mimeType", part.mimeType); put("data", part.base64Data) })
        })
        is ModelContentPart.FileAttachment -> add(buildJsonObject {
            put("inlineData", buildJsonObject { put("mimeType", part.mimeType); put("data", part.base64Data) })
        })
    } }
}
