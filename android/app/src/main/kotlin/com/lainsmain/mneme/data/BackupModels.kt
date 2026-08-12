package com.lainsmain.mneme.data

import kotlinx.serialization.Serializable

@Serializable
data class VaultManifest(
    val format: String,
    val deviceId: String,
    val revision: Long,
    val createdAtEpochMillis: Long,
    val pages: List<VaultPage>,
    val attachments: List<VaultAttachment>,
    val monthlyRecaps: List<VaultMonthlyRecap> = emptyList(),
)

@Serializable
data class VaultPage(
    val id: String,
    val date: String,
    val document: String,
    val plainText: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val revision: Long,
    val deletedAtEpochMillis: Long? = null,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationIsManual: Boolean = false,
    val isFavorite: Boolean = false,
)

@Serializable
data class VaultAttachment(
    val id: String,
    val pageId: String,
    val objectHash: String,
    val plaintextSha256: String,
    val originalFileName: String? = null,
    val mimeType: String,
    val byteSize: Long,
    val width: Int? = null,
    val height: Int? = null,
    val capturedAtEpochMillis: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val exposureTime: String? = null,
    val aperture: String? = null,
    val iso: Int? = null,
    val focalLength: String? = null,
    val caption: String = "",
    val sortOrder: Int,
    val normalizedExif: String = "{}",
    val createdAtEpochMillis: Long,
)

@Serializable
data class VaultMonthlyRecap(
    val id: String,
    val yearMonth: String,
    val document: String,
    val plainText: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val revision: Long,
    val deletedAtEpochMillis: Long? = null,
)

@Serializable
data class RemoteManifestPointer(
    val deviceId: String,
    val revision: Long,
    val objectHash: String,
    val updatedAt: String,
    val referencesComplete: Boolean = false,
    val historical: Boolean = false,
)

@Serializable
data class ManifestReferencesRequest(val objectHashes: List<String>)

@Serializable
data class ServerStorageStats(
    val objectCount: Long,
    val objectBytes: Long,
    val manifestHistoryCount: Long,
    val completeManifestCount: Long,
    val incompleteManifestCount: Long,
)

data class ServerOverview(
    val snapshots: List<RemoteManifestPointer>,
    val storage: ServerStorageStats,
)

@Serializable
data class GarbageCollectionRequest(val keepManifestsPerDevice: Int = 30)

@Serializable
data class GarbageCollectionResult(
    val prunedManifestCount: Long,
    val deletedObjectCount: Long,
    val reclaimedBytes: Long,
    val storage: ServerStorageStats,
)
