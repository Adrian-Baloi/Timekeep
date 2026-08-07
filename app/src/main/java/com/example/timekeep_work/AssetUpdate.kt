package com.example.timekeep_work

data class AssetUpdate(
    val assetId: Int,
    val hostname: String? = null,
    val serialNumber: String? = null,
    val assetTag: String? = null,
    val assignedUserId: Int? = null,
    val statusId: Int? = null,
    val statusName: String? = null,
    val locationId: Int? = null,
) {
    init {
        require(assetId > 0) { "assetId must be positive." }
    }

    val hasChanges: Boolean
        get() = hostname != null ||
            serialNumber != null ||
            assetTag != null ||
            assignedUserId != null ||
            statusId != null ||
            statusName != null ||
            locationId != null
}
