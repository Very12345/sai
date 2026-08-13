package com.phoneagent.agent.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal fun JsonObject.string(name: String, default: String? = null): String =
    (this[name] as? JsonPrimitive)?.contentOrNull ?: default ?: error("Missing argument: $name")

internal fun JsonObject.int(name: String, default: Int): Int =
    (this[name] as? JsonPrimitive)?.intOrNull ?: default

internal fun JsonObject.boolean(name: String, default: Boolean): Boolean =
    (this[name] as? JsonPrimitive)?.booleanOrNull ?: default

