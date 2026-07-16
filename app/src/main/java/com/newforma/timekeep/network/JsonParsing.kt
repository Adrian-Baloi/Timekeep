package com.newforma.timekeep.network

import com.newforma.timekeep.model.HaloAgentSummary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun JsonObject.toHaloAgentSummary(): HaloAgentSummary {
    val displayName = stringValue("name")
        ?: listOfNotNull(stringValue("firstname"), stringValue("surname"))
            .joinToString(" ")
            .ifBlank { null }
        ?: stringValue("email")
        ?: "Authenticated agent"

    return HaloAgentSummary(
        id = stringValue("id") ?: elementValue("id")?.displayText(),
        displayName = displayName,
        email = stringValue("email") ?: stringValue("googleemail"),
        jobTitle = stringValue("jobtitle"),
        initials = stringValue("initials"),
        primaryTeam = firstDisplayValue("teams") ?: elementValue("team")?.displayText(),
    )
}

private fun JsonObject.stringValue(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    return primitive.contentOrNull
}

private fun JsonObject.elementValue(name: String): JsonElement? = this[name]

private fun JsonObject.firstDisplayValue(name: String): String? {
    val array = this[name] as? JsonArray ?: return null
    return array.firstNotNullOfOrNull { it.displayText() }
}

private fun JsonElement.displayText(): String? {
    return when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonObject -> stringValue("name") ?: stringValue("label") ?: stringValue("text")
        else -> null
    }
}
