package com.example.timekeep_work.screen

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.timekeep_work.Agent

@Composable
fun Screen(
    assetViewModel: AssetViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val agent = remember { Agent(name = "Adrian Baloi", email = "abaloi@newforma.com", id = 36) }
    val assetState by assetViewModel.uiState.collectAsStateWithLifecycle()
    val serialInput by assetViewModel.serialInput.collectAsStateWithLifecycle()
    val managementState by assetViewModel.managementUiState.collectAsStateWithLifecycle()

    fun returnToScanner() {
        assetViewModel.onStartAnotherScan()
        if (!navController.popBackStack(Routes.Scan, inclusive = false)) {
            navController.navigate(Routes.Scan)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Login,
    ) {
        composable(Routes.Login) {
            SignedOutScreen(
                onSignInClick = {
                    assetViewModel.onSignedIn()
                    navController.navigate(Routes.Scan) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Scan) {
            if (assetState == AssetUiState.Scanning) {
                ScanningScreen(
                    agent = agent,
                    onCancel = assetViewModel::onScanCancelled,
                    onSerialDetected = {
                        val identifier = AssetViewModel.DEMO_SCANNED_IDENTIFIER
                        assetViewModel.onSerialSubmitted(identifier)
                        navController.navigate(Routes.candidates(identifier))
                    },
                )
            } else {
                SignedInScreen(
                    agent = agent,
                    onSignOutClick = {
                        assetViewModel.onSignOut()
                        navController.navigate(Routes.Login) {
                            popUpTo(Routes.Scan) { inclusive = true }
                        }
                    },
                    onScanClick = assetViewModel::onScanRequested,
                    onManualEntryClick = { navController.navigate(Routes.ManualEntry) },
                )
            }
        }

        composable(Routes.ManualEntry) {
            ManualSerialEntryScreen(
                agent = agent,
                serial = serialInput,
                onSerialChange = assetViewModel::onSerialChanged,
                onBack = { navController.popBackStack() },
                onSearch = {
                    val identifier = serialInput.trim()
                    assetViewModel.onSerialSubmitted(identifier)
                    navController.navigate(Routes.candidates(identifier))
                },
            )
        }

        composable(
            route = Routes.Candidates,
            arguments = listOf(navArgument(Routes.IdentifierArgument) { type = NavType.StringType }),
        ) { backStackEntry ->
            val identifier = backStackEntry.arguments
                ?.getString(Routes.IdentifierArgument)
                .orEmpty()

            LaunchedEffect(identifier) {
                assetViewModel.onCandidatesShown(identifier)
            }

            when (val state = assetState) {
                AssetUiState.Ready,
                AssetUiState.Scanning,
                AssetUiState.Searching,
                -> SearchingScreen(
                    agent = agent,
                    serial = identifier,
                    onCancel = { returnToScanner() },
                )

                is AssetUiState.NoMatch -> NoMatchingAssetScreen(
                    agent = agent,
                    serial = state.identifier,
                    onScanAgain = { returnToScanner() },
                    onManualEntry = {
                        if (!navController.popBackStack(Routes.ManualEntry, inclusive = false)) {
                            navController.navigate(Routes.ManualEntry)
                        }
                    },
                )

                is AssetUiState.MatchFound -> OneMatchingAssetScreen(
                    agent = agent,
                    asset = state.asset,
                    onReviewAsset = {
                        assetViewModel.onAssetSelected(state.asset.id)
                        assetViewModel.onManagementActionRequested()
                        navController.navigate(Routes.managementAction(state.asset.id))
                    },
                    onWrongAsset = { returnToScanner() },
                )

                is AssetUiState.MultipleMatches -> MultipleAssetsScreen(
                    agent = agent,
                    assets = state.assets,
                    onSelect = { assetId ->
                        assetViewModel.onAssetSelected(assetId)
                        assetViewModel.onManagementActionRequested()
                        navController.navigate(Routes.managementAction(assetId))
                    },
                    onScanAgain = { returnToScanner() },
                )

                is AssetUiState.Failure -> WorkflowFailureScreen(
                    agent = agent,
                    title = "Asset search failed",
                    message = state.message,
                    primaryLabel = if (state.retryable) "Try again" else "Back to scanner",
                    onPrimary = {
                        if (state.retryable) assetViewModel.onRetry() else returnToScanner()
                    },
                    onSecondary = { returnToScanner() },
                )
            }
        }

        composable(
            route = Routes.ManagementAction,
            arguments = listOf(navArgument(Routes.AssetIdArgument) { type = NavType.IntType }),
        ) { backStackEntry ->
            val assetId = backStackEntry.arguments?.getInt(Routes.AssetIdArgument) ?: return@composable

            BackHandler {
                assetViewModel.onReturnToCandidates()
                navController.popBackStack()
            }

            LaunchedEffect(assetId) {
                assetViewModel.onAssetSelected(assetId)
            }

            val asset = (assetState as? AssetUiState.MatchFound)?.asset?.takeIf { it.id == assetId }
            val failure = managementState.failure
            when {
                failure != null -> WorkflowFailureScreen(
                    agent = agent,
                    title = "Update failed",
                    message = failure.message,
                    primaryLabel = if (failure.retryable) "Try again" else "Back to asset",
                    onPrimary = {
                        if (failure.retryable) {
                            assetViewModel.onRetry()
                        } else {
                            assetViewModel.onManagementActionRequested()
                        }
                    },
                    onSecondary = { returnToScanner() },
                )

                asset == null -> AssetLoadingScreen(
                    agent = agent,
                    assetId = assetId,
                    onBack = {
                        assetViewModel.onReturnToCandidates()
                        navController.popBackStack()
                    },
                )

                managementState.phase == ManagementPhase.Success -> SuccessfulUpdateScreen(
                    agent = agent,
                    asset = asset,
                    newStatus = managementState.selectedStatus,
                    onDone = { returnToScanner() },
                    onViewAsset = assetViewModel::onManagementActionRequested,
                )

                else -> ManagementActionScreen(
                    agent = agent,
                    asset = asset,
                    selectedStatus = managementState.selectedStatus,
                    reviewed = managementState.reviewed,
                    updating = managementState.phase == ManagementPhase.Updating,
                    onStatusChange = assetViewModel::onStatusSelected,
                    onReviewedChange = assetViewModel::onReviewedChanged,
                    onBack = {
                        assetViewModel.onReturnToCandidates()
                        navController.popBackStack()
                    },
                    onContinue = assetViewModel::onReviewUpdateRequested,
                    confirmation = if (managementState.phase == ManagementPhase.Confirming) {
                        ConfirmationState(
                            onDismiss = assetViewModel::onConfirmationDismissed,
                            onConfirm = assetViewModel::onConfirmUpdate,
                        )
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private object Routes {
    const val Login = "login"
    const val Scan = "scan"
    const val ManualEntry = "scan/manual"
    const val IdentifierArgument = "identifier"
    const val AssetIdArgument = "assetId"
    const val Candidates = "candidates/{$IdentifierArgument}"
    const val ManagementAction = "asset/{$AssetIdArgument}/management"

    fun candidates(identifier: String): String = "candidates/${Uri.encode(identifier)}"

    fun managementAction(assetId: Int): String = "asset/$assetId/management"
}
