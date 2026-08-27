package com.example.timekeep_work

/**
 * App-level view of a Halo asset.
 *
 * Most fields are nullable because their availability depends on the tenant's asset
 * configuration. [matchedIdentifier] records the value that produced this result so the
 * workflow can preserve context when the user moves between candidate and detail screens.
 */
data class Asset(
    val id: Int,
    val hostname: String?,
    val serialNumber: String?,
    val assetTag: String?,
    val manufacturer: String?,
    val model: String?,
    val assignedUser: String?,
    val status: String?,
    val location: String?,
    val lastUpdated: String? = null,
    val matchedIdentifier: String = serialNumber.orEmpty(),
)
