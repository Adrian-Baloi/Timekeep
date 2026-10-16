package com.example.timekeep_work.network.dto

import com.example.timekeep_work.Agent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Converts Halo's loosely shaped Agent/me payload into the small identity model used by the UI.
 * Name fields are tried in preference order because Halo tenants do not populate them uniformly.
 */
fun JsonElement.toAgent(): Agent {
    val payload = this as? JsonObject
        ?: throw SerializationException("Halo Agent/me response was not a JSON object.")
    val firstName = payload.string("firstname")
    val surname = payload.string("surname")
    val name = payload.string("name")
        ?: listOfNotNull(firstName, surname).joinToString(" ").ifBlank { null }
        ?: payload.string("email")
        ?: "Halo agent"
    val id = (payload["id"] as? JsonPrimitive)?.intOrNull
        ?: throw SerializationException("Halo Agent/me response did not include a numeric id.")

    return Agent(
        name = name,
        email = payload.string("email") ?: payload.string("googleemail").orEmpty(),
        id = id,
    )
}

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
