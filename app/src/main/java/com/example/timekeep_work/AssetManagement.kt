package com.example.timekeep_work

import java.util.Locale

/** A Halo user who can be assigned to an asset during deployment. */
data class AssignableUser(
    val id: Int,
    val name: String,
    val email: String? = null,
    val isAgent: Boolean = false,
) {
    init {
        require(id > 0) { "id must be positive." }
        require(name.isNotBlank()) { "name must not be blank." }
    }
}

/** A Halo site offered as a supported asset location. */
data class AssetSite(
    val id: Int,
    val name: String,
    val clientName: String? = null,
) {
    init {
        require(id > 0) { "id must be positive." }
        require(name.isNotBlank()) { "name must not be blank." }
    }
}

/**
 * Applies the business-approved site allow list to the sites returned by Halo.
 *
 * The allow-list order is also the display order, and its spelling becomes the canonical
 * label shown in the UI.
 */
object AssetManagementSites {
    val allowedNames: List<String> = listOf(
        "Boston Office",
        "EleVia Office",
        "Levis Office",
        "Levis Office (Bimone)",
        "London Office",
        "Manchester Office",
        "Montreal Office",
        "Remote | CA",
        "Remote | International",
        "Remote | US",
    )

    /** Returns only supported sites, in the order defined by [allowedNames]. */
    fun restrict(sites: Collection<AssetSite>): List<AssetSite> {
        val sitesByName = sites.associateBy { it.name.normalizedSiteName() }
        return allowedNames.mapNotNull { allowedName ->
            sitesByName[allowedName.normalizedSiteName()]?.copy(name = allowedName)
        }
    }
}

/** Management operations currently exposed by the app. */
enum class AssetManagementAction(val label: String) {
    Deploying("Deploying"),
    ReturningToInventory("Returning to Inventory"),
}

/**
 * Validated domain request for a Halo asset write.
 *
 * Keeping deployment and inventory returns as separate types prevents callers from creating
 * combinations such as a deployment without a user.
 */
sealed interface AssetUpdate {
    val assetId: Int
    val site: AssetSite

    data class Deploy(
        override val assetId: Int,
        override val site: AssetSite,
        val user: AssignableUser,
    ) : AssetUpdate {
        init {
            require(assetId > 0) { "assetId must be positive." }
        }
    }

    data class ReturnToInventory(
        override val assetId: Int,
        override val site: AssetSite,
    ) : AssetUpdate {
        init {
            require(assetId > 0) { "assetId must be positive." }
        }
    }
}

private fun String.normalizedSiteName(): String = trim().lowercase(Locale.ROOT)
