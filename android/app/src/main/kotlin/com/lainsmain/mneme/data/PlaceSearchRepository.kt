package com.lainsmain.mneme.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PlaceSuggestion(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

/** Uses the geocoder supplied by Android or the device manufacturer. */
class PlaceSearchRepository(context: Context) {
    private val applicationContext = context.applicationContext

    suspend fun search(query: String, language: String, limit: Int = 6): Result<List<PlaceSuggestion>> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireGeocoder()
                val geocoder = Geocoder(applicationContext, locale(language))
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query.trim(), limit.coerceIn(1, 10))
                    .orEmpty()
                    .map { it.toSuggestion() }
                    .distinctBy { Triple(it.name, it.latitude, it.longitude) }
            }.mapFailure()
        }

    suspend fun reverse(
        latitude: Double,
        longitude: Double,
        language: String,
    ): Result<PlaceSuggestion?> = withContext(Dispatchers.IO) {
        runCatching {
            require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                "Invalid map coordinates."
            }
            requireGeocoder()
            val geocoder = Geocoder(applicationContext, locale(language))
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, 1)
                .orEmpty()
                .firstOrNull()
                ?.toSuggestion()
        }.mapFailure()
    }

    private fun requireGeocoder() {
        check(Geocoder.isPresent()) {
            "Location search is unavailable on this device. You can still choose a point on the map."
        }
    }

    private fun locale(language: String): Locale =
        Locale.forLanguageTag(language).takeUnless { it.language.isBlank() } ?: Locale.getDefault()

    private fun Address.toSuggestion(): PlaceSuggestion {
        val fullAddress = getAddressLine(0).orEmpty()
        val conciseName = listOfNotNull(
            featureName?.takeUnless { it.isMostlyNumeric() },
            premises,
            thoroughfare,
            subLocality,
            locality,
            subAdminArea,
            adminArea,
            countryName,
        ).firstOrNull { it.isNotBlank() } ?: fullAddress.ifBlank {
            "${"%.5f".format(Locale.ROOT, latitude)}, ${"%.5f".format(Locale.ROOT, longitude)}"
        }
        return PlaceSuggestion(
            name = conciseName,
            address = fullAddress.takeUnless { it.equals(conciseName, ignoreCase = true) }.orEmpty(),
            latitude = latitude,
            longitude = longitude,
        )
    }

    private fun String.isMostlyNumeric(): Boolean {
        val meaningful = filter(Char::isLetterOrDigit)
        return meaningful.isNotEmpty() && meaningful.none(Char::isLetter)
    }

    private fun <T> Result<T>.mapFailure(): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { error ->
            Result.failure(
                when (error) {
                    is IllegalArgumentException,
                    is IllegalStateException,
                    -> error
                    is IOException -> IOException("The device location service could not be reached.", error)
                    else -> IOException("Could not search locations on this device.", error)
                },
            )
        },
    )
}
