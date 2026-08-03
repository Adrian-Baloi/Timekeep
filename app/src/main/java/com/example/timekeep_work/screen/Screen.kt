package com.example.timekeep_work.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timekeep_work.Agent
import com.example.timekeep_work.Asset
import com.example.timekeep_work.ui.theme.AppUIPalette

internal enum class AppScreen(val title: String) {
    SIGNED_OUT("Signed out"),
    SIGNED_IN_HOMEPAGE("Ready to scan"),
    MANUAL_ENTRY("Manual serial entry"),
    SEARCHING("Searching"),
    NO_MATCH("No matching asset"),
    ONE_MATCH("One matching asset"),
    MULTIPLE_MATCHES("Multiple candidate assets"),
    ASSET_DETAILS("Asset details"),
    MANAGEMENT_ACTION("Management action form"),
    CONFIRMATION("Confirmation dialog"),
    SUCCESS("Successful update"),
    NETWORK_FAILURE("Network failure"),
    AUTH_FAILURE("Authentication failure"),
    PERMISSION_FAILURE("Permission failure"),
}

@Composable
fun Screen() {
    val agent = remember { Agent(name = "Adrian Baloi", email = "abaloi@newforma.com", id = 36) }
    val assets = remember { fakeAssets() }
    var screen by rememberSaveable { mutableStateOf(AppScreen.SIGNED_OUT) }
    var serial by rememberSaveable { mutableStateOf("PF4X91Q2") }
    var selectedAssetId by rememberSaveable { mutableStateOf(assets.first().id) }
    var reviewed by rememberSaveable { mutableStateOf(false) }
    var pendingStatus by rememberSaveable { mutableStateOf("In use") }
    var showStatePicker by rememberSaveable { mutableStateOf(false) }
    val selectedAsset = assets.first { it.id == selectedAssetId }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            AppScreen.SIGNED_OUT -> SignedOutScreen(onSignInClick = { screen = AppScreen.SIGNED_IN_HOMEPAGE })
            AppScreen.SIGNED_IN_HOMEPAGE -> SignedInScreen(
                agent = agent,
                onSignOutClick = { screen = AppScreen.SIGNED_OUT },
                onScanClick = {
                    serial = assets.first().serialNumber // Simulates having scanned device equal to assets.first()
                    screen = AppScreen.SEARCHING
                },
                onManualEntryClick = { screen = AppScreen.MANUAL_ENTRY },
            )
            AppScreen.MANUAL_ENTRY -> ManualSerialEntryScreen(
                agent = agent,
                serial = serial,
                onSerialChange = { serial = it },
                onBack = { screen = AppScreen.SIGNED_IN_HOMEPAGE },
                onSearch = { screen = AppScreen.SEARCHING },
            )
            AppScreen.SEARCHING -> SearchingScreen(
                agent = agent,
                serial = serial,
                onCancel = { screen = AppScreen.SIGNED_IN_HOMEPAGE },
                onShowFakeResult = {
                    screen = when (serial.trim().uppercase()) {
                        "NONE" -> AppScreen.NO_MATCH
                        "MULTI" -> AppScreen.MULTIPLE_MATCHES
                        else -> AppScreen.ONE_MATCH
                    }
                },
            )
            AppScreen.NO_MATCH -> NoMatchingAssetScreen(
                agent = agent,
                serial = serial,
                onScanAgain = { screen = AppScreen.SIGNED_IN_HOMEPAGE },
                onManualEntry = { screen = AppScreen.MANUAL_ENTRY },
            )
            AppScreen.ONE_MATCH -> OneMatchingAssetScreen(
                agent = agent,
                asset = selectedAsset,
                onReviewAsset = {
                    reviewed = false
                    screen = AppScreen.ASSET_DETAILS
                },
                onWrongAsset = { screen = AppScreen.SIGNED_IN_HOMEPAGE },
            )
            AppScreen.MULTIPLE_MATCHES -> MultipleAssetsScreen(
                agent = agent,
                assets = assets,
                onSelect = {
                    selectedAssetId = it.id
                    reviewed = false
                    screen = AppScreen.ASSET_DETAILS
                },
                onScanAgain = { screen = AppScreen.SIGNED_IN_HOMEPAGE },
            )
            AppScreen.ASSET_DETAILS -> AssetDetailsScreen(
                agent = agent,
                asset = selectedAsset,
                onBack = { screen = AppScreen.ONE_MATCH },
                onScanAgain = { screen = AppScreen.SIGNED_IN_HOMEPAGE },
                onReviewed = {
                    reviewed = true
                    screen = AppScreen.MANAGEMENT_ACTION
                },
            )
            AppScreen.MANAGEMENT_ACTION -> ManagementActionScreen(
                agent = agent,
                asset = selectedAsset,
                selectedStatus = pendingStatus,
                reviewed = reviewed,
                onStatusChange = { pendingStatus = it },
                onReviewedChange = { reviewed = it },
                onBack = { screen = AppScreen.ASSET_DETAILS },
                onContinue = { screen = AppScreen.CONFIRMATION },
            )
            AppScreen.CONFIRMATION -> ManagementActionScreen(
                agent = agent,
                asset = selectedAsset,
                selectedStatus = pendingStatus,
                reviewed = true,
                onStatusChange = { pendingStatus = it },
                onReviewedChange = { reviewed = it },
                onBack = { screen = AppScreen.ASSET_DETAILS },
                onContinue = { },
                confirmation = ConfirmationState(
                    onDismiss = { screen = AppScreen.MANAGEMENT_ACTION },
                    onConfirm = { screen = AppScreen.SUCCESS },
                ),
            )
            AppScreen.SUCCESS -> SuccessfulUpdateScreen(
                agent = agent,
                asset = selectedAsset,
                newStatus = pendingStatus,
                onDone = { screen = AppScreen.SIGNED_IN_HOMEPAGE },
                onViewAsset = { screen = AppScreen.ASSET_DETAILS },
            )
            AppScreen.NETWORK_FAILURE -> WorkflowFailureScreen(
                agent = agent,
                title = "Can't reach Halo ITSM",
                message = "Check your connection and try again. No changes were made.",
                primaryLabel = "Try again",
                onPrimary = { screen = AppScreen.CONFIRMATION },
                onSecondary = { screen = AppScreen.SIGNED_IN_HOMEPAGE },
            )
            AppScreen.AUTH_FAILURE -> AuthenticationFailureScreen(
                email = agent.email,
                onSignIn = { screen = AppScreen.SIGNED_IN_HOMEPAGE },
                onCancel = { screen = AppScreen.SIGNED_OUT },
            )
            AppScreen.PERMISSION_FAILURE -> WorkflowFailureScreen(
                agent = agent,
                title = "Update not permitted",
                message = "Your Halo ITSM account can view this asset but cannot change its status. No changes were made.",
                primaryLabel = "Back to asset",
                onPrimary = { screen = AppScreen.ASSET_DETAILS },
                onSecondary = { screen = AppScreen.SIGNED_IN_HOMEPAGE },
            )
        }

        Button(
            onClick = { showStatePicker = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppUIPalette.TextSecondary,
                contentColor = AppUIPalette.OnPrimary,
            ),
        ) { Text("Fake states") }
    }

    if (showStatePicker) {
        FakeStatePicker(
            current = screen,
            onDismiss = { showStatePicker = false },
            onSelect = {
                reviewed = it == AppScreen.CONFIRMATION || it == AppScreen.SUCCESS
                screen = it
                showStatePicker = false
            },
        )
    }
}

private fun fakeAssets() = listOf(
    Asset(101, "TOR-LT-0142", "PF4X91Q2", "NF-10482", "Lenovo", "ThinkPad T14 Gen 4", "Maya Chen", "In use", "Toronto – 14th floor"),
    Asset(102, "TOR-LT-0198", "PF4X91Q2", "NF-10811", "Lenovo", "ThinkPad T14 Gen 3", "Unassigned", "In stock", "Toronto – IT storage"),
    Asset(103, null, "PF4X91Q2", "NF-10903", "Lenovo", "ThinkPad T14 Gen 4", "Jordan Lee", "Repair", "Toronto – Service desk"),
)
