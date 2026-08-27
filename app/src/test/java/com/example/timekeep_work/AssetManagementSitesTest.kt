package com.example.timekeep_work

import org.junit.Assert.assertEquals
import org.junit.Test

class AssetManagementSitesTest {
    @Test
    fun `site allowlist filters unrelated sites and preserves canonical order`() {
        val haloSites = AssetManagementSites.allowedNames
            .reversed()
            .mapIndexed { index, name -> AssetSite(index + 1, name.lowercase()) }
            .plus(AssetSite(100, "Toronto Office"))

        val result = AssetManagementSites.restrict(haloSites)

        assertEquals(AssetManagementSites.allowedNames, result.map(AssetSite::name))
    }
}
