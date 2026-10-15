package com.lck07.lumen.core.xtream

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/*
  Xtream servers are loose with types: the same field arrives as a number, a
  string, "", null or missing depending on the panel. These readers accept
  any of those and return null when there is nothing usable.
*/
internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

internal fun JsonObject.int(key: String): Int? = str(key)?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }

internal fun JsonObject.double(key: String): Double? = str(key)?.replace(',', '.')?.toDoubleOrNull()

/** Unix seconds (as number or string) to an Instant. */
internal fun JsonObject.epochSeconds(key: String): Instant? = str(key)?.toLongOrNull()?.takeIf { it > 0 }?.let(Instant::ofEpochSecond)

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/** A list of strings that some servers send as an array and others as a single string. */
internal fun JsonObject.strings(key: String): List<String> = when (val v = this[key]) {
    is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
    is JsonPrimitive -> listOfNotNull(v.content.trim().takeIf { it.isNotEmpty() && it != "null" })
    else -> emptyList()
}

/** A top-level response that should be a list but may be an object keyed by index, or empty. */
internal fun JsonElement.objects(): List<JsonObject> = when (this) {
    is JsonArray -> filterIsInstance<JsonObject>()
    is JsonObject -> values.filterIsInstance<JsonObject>()
    else -> emptyList()
}
