package com.lainsmain.mneme.data

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PlaceSuggestion(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

class PlaceSearchRepository(
    private val settingsRepository: AppSettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun search(query: String, language: String, limit: Int = 6): Result<List<PlaceSuggestion>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val settings = settingsRepository.settings.value
                require(settings.serverConnected && settings.serverUrl.isNotBlank() && settings.serverToken.isNotBlank()) {
                    "Connect your Mneme server in Settings to search places."
                }
                val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
                val encodedLanguage = URLEncoder.encode(language, Charsets.UTF_8.name())
                val connection = URL(
                    "${settings.serverUrl}/v1/places/search?q=$encodedQuery&limit=${limit.coerceIn(1, 10)}&lang=$encodedLanguage",
                ).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.setRequestProperty("Authorization", "Bearer ${settings.serverToken}")
                    connection.setRequestProperty("Accept", "application/geo+json")
                    when (val code = connection.responseCode) {
                        HttpURLConnection.HTTP_OK -> parseSuggestions(
                            connection.inputStream.bufferedReader().use { it.readText() },
                        )
                        HttpURLConnection.HTTP_BAD_GATEWAY,
                        HttpURLConnection.HTTP_UNAVAILABLE,
                        -> error("The self-hosted place index is still starting.")
                        HttpURLConnection.HTTP_UNAUTHORIZED -> error("The server rejected your access token.")
                        else -> error("Place search returned HTTP $code.")
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun parseSuggestions(response: String): List<PlaceSuggestion> =
        json.parseToJsonElement(response).jsonObject["features"]?.jsonArray.orEmpty().mapNotNull { element ->
            val feature = element.jsonObject
            val properties = feature["properties"]?.jsonObject ?: return@mapNotNull null
            val coordinates = feature["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
                ?: return@mapNotNull null
            val longitude = coordinates.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val latitude = coordinates.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val name = properties.string("name") ?: properties.string("street") ?: return@mapNotNull null
            val address = listOfNotNull(
                listOfNotNull(properties.string("street"), properties.string("housenumber"))
                    .joinToString(" ").takeIf { it.isNotBlank() && it != name },
                properties.string("postcode"),
                properties.string("city") ?: properties.string("locality"),
                properties.string("state"),
                properties.string("country"),
            ).distinct().joinToString(", ")
            PlaceSuggestion(name, address, latitude, longitude)
        }.distinctBy { Triple(it.name, it.latitude, it.longitude) }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
}
