package com.example.timekeep_work

data class Asset(
    val id: Int,
    val hostname: String?,
    val serialNumber: String,
    val assetTag: String?,
    val manufacturer: String?,
    val model: String?,
    val assignedUser: String?,
    val status: String?,
    val location: String?
)