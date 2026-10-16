package com.example.timekeep_work.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.timekeep_work.data.AppDependencies
import com.example.timekeep_work.scanner.GoogleCodeScannerGateway
import com.example.timekeep_work.scanner.IdentifierNormalizationResult
import com.example.timekeep_work.scanner.IdentifierNormalizer
import com.example.timekeep_work.scanner.InvalidIdentifierReason
import com.example.timekeep_work.scanner.ScanResult
import com.example.timekeep_work.scanner.ScannerGateway

/**
 * Root composable that connects authentication, scanning, lookup state, and navigation.
 * Screen-specific composables remain stateless and receive their data and callbacks from here.
 */
@Composable
fun Screen(
    assetViewModel: AssetViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(AppDependencies.container),
    ),
    scannerGateway: ScannerGateway? = null,
) {
    val navController = rememberNavController()
    var lookupFromManual by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val identifierNormalizer = remember { IdentifierNormalizer() }
    val activeScannerGateway = remember(context, scannerGateway) {
        scannerGateway ?: GoogleCodeScannerGateway(context, identifierNormalizer)
    }
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val agent = (authState as? AuthUiState.Authenticated)?.agent
    val assetState by assetViewModel.uiState.collectAsStateWithLifecycle()
    val serialInput by assetViewModel.serialInput.collectAsStateWithLifecycle()
    val managementState by assetViewModel.managementUiState.collectAsStateWithLifecycle()
    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { authViewModel.handleAuthorizationResult(it.data) },
    )

    LaunchedEffect(authViewModel) {
        authViewModel.authorizationIntents.collect(authorizationLauncher::launch)
    }
    LaunchedEffect(authViewModel) {
        authViewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Scanning is a one-time side effect driven by the Scanning state, not by recomposition.
    LaunchedEffect(assetState, activeScannerGateway) {
        if (assetState != AssetUiState.Scanning) return@LaunchedEffect

        when (val result = activeScannerGateway.scan()) {
            is ScanResult.Success -> {
                lookupFromManual = false
                assetViewModel.onIdentifierSubmitted(result.identifier)
                navController.navigate(Routes.candidates(result.identifier.value))
            }
            ScanResult.Cancellation -> assetViewModel.onScanCancelled()
            ScanResult.ScannerUnavailable,
            ScanResult.ScannerModuleUnavailable,
            is ScanResult.InvalidValue,
            ScanResult.UnexpectedFailure,
            -> {
                assetViewModel.onScanCancelled()
                Toast.makeText(context, result.userFacingMessage(), Toast.LENGTH_LONG).show()
            }
        }
    }

    // Authentication owns access to the workflow and clears its back stack on either transition.
    LaunchedEffect(authState) {
        when (authState) {
            is AuthUiState.Authenticated -> {
                assetViewModel.onSignedIn()
                if (navController.currentDestination?.route == Routes.Login) {
                    navController.navigate(Routes.Scan) {
                        popUpTo(Routes.Login) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            AuthUiState.SignedOut -> {
                assetViewModel.onSignOut()
                if (navController.currentDestination?.route != Routes.Login) {
                    navController.navigate(Routes.Login) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            AuthUiState.Checking -> Unit
        }
    }

    fun navigateToScanner(startScan: Boolean) {
        if (startScan) {
            assetViewModel.onStartAnotherScan()
        } else {
            assetViewModel.onReturnToScanner()
        }
        if (!navController.popBackStack(Routes.Scan, inclusive = false)) {
            navController.navigate(Routes.Scan)
        }
    }

    fun openManualCorrection() {
        assetViewModel.onScanCancelled()
        if (!navController.popBackStack(Routes.ManualEntry, inclusive = false)) {
            navController.navigate(Routes.ManualEntry)
        }
    }

    fun logout() {
        assetViewModel.onSignOut()
        authViewModel.signOut()
    }

    val lookupRecoveryActions = LookupRecoveryActions(
        onRescan = { navigateToScanner(startScan = true) },
        onManualCorrection = ::openManualCorrection,
    )

    NavHost(
        navController = navController,
        startDestination = Routes.Login,
    ) {
        composable(Routes.Login) {
            SignedOutScreen(
                onSignInClick = authViewModel::startSignIn,
            )
        }

        composable(Routes.Scan) {
            val currentAgent = agent ?: return@composable
            SignedInScreen(
                agent = currentAgent,
                onSignOutClick = {
                    logout()
                },
                onScanClick = assetViewModel::onScanRequested,
                onManualEntryClick = { navController.navigate(Routes.ManualEntry) },
            )
        }

        composable(Routes.ManualEntry) {
            val currentAgent = agent ?: return@composable
            ManualSerialEntryScreen(
                agent = currentAgent,
                serial = serialInput,
                onSerialChange = assetViewModel::onSerialChanged,
                onBack = { navController.popBackStack() },
                onSearch = {
                    when (val result = assetViewModel.onSerialSubmitted(serialInput)) {
                        is IdentifierNormalizationResult.Success -> {
                            lookupFromManual = true
                            navController.navigate(Routes.candidates(result.identifier.value))
                        }
                        is IdentifierNormalizationResult.Invalid -> Toast.makeText(
                            context,
                            result.reason.userFacingMessage(),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
            )
        }

        composable(
            route = Routes.Candidates,
            arguments = listOf(navArgument(Routes.IdentifierArgument) { type = NavType.StringType }),
        ) { backStackEntry ->
            val currentAgent = agent ?: return@composable
            val identifier = backStackEntry.arguments
                ?.getString(Routes.IdentifierArgument)
                .orEmpty()

            LaunchedEffect(identifier) {
                assetViewModel.onCandidatesShown(identifier)
            }

            // A single exact match skips candidate selection and opens management directly.
            LaunchedEffect(assetState, identifier) {
                val match = assetState as? AssetUiState.MatchFound ?: return@LaunchedEffect
                if (navController.currentDestination?.route != Routes.Candidates) return@LaunchedEffect
                if (!match.asset.matchedIdentifier.equals(identifier, ignoreCase = true)) return@LaunchedEffect
                assetViewModel.onAssetSelected(match.asset.id)
                assetViewModel.onManagementActionRequested()
                navController.navigate(Routes.managementAction(match.asset.id))
            }

            when (val state = assetState) {
                AssetUiState.Ready,
                AssetUiState.Scanning,
                AssetUiState.Searching,
                -> SearchingScreen(
                    agent = currentAgent,
                    serial = identifier,
                    onCancel = { navigateToScanner(startScan = false) },
                )

                is AssetUiState.NoMatch -> NoMatchingAssetScreen(
                    agent = currentAgent,
                    serial = state.identifier,
                    onRetry = assetViewModel::onRetryLookup,
                    recoveryActions = lookupRecoveryActions,
                )

                is AssetUiState.MatchFound -> SearchingScreen(
                    agent = currentAgent,
                    serial = state.asset.matchedIdentifier,
                    onCancel = { navigateToScanner(startScan = false) },
                )

                is AssetUiState.MultipleMatches -> MultipleAssetsScreen(
                    agent = currentAgent,
                    assets = state.assets,
                    onSelect = { assetId ->
                        assetViewModel.onAssetSelected(assetId)
                        assetViewModel.onManagementActionRequested()
                        navController.navigate(Routes.managementAction(assetId))
                    },
                    recoveryActions = lookupRecoveryActions,
                )

                is AssetUiState.Failure -> WorkflowFailureScreen(
                    agent = currentAgent,
                    title = "Asset search failed",
                    message = state.message,
                    primaryLabel = if (state.retryable) "Try again" else "Back to scanner",
                    onPrimary = {
                        if (state.retryable) {
                            assetViewModel.onRetry()
                        } else {
                            navigateToScanner(startScan = false)
                        }
                    },
                    recoveryActions = lookupRecoveryActions,
                )
            }
        }

        composable(
            route = Routes.ManagementAction,
            arguments = listOf(navArgument(Routes.AssetIdArgument) { type = NavType.IntType }),
        ) { backStackEntry ->
            val currentAgent = agent ?: return@composable
            val assetId = backStackEntry.arguments?.getInt(Routes.AssetIdArgument) ?: return@composable

            fun leaveManagement() {
                if (lookupFromManual) {
                    assetViewModel.onReturnToScanner()
                    if (!navController.popBackStack(Routes.ManualEntry, inclusive = false)) {
                        navController.navigate(Routes.ManualEntry)
                    }
                } else {
                    navigateToScanner(startScan = true)
                }
            }

            BackHandler {
                if (managementState.phase != ManagementPhase.Updating) leaveManagement()
            }

            LaunchedEffect(assetId) {
                assetViewModel.onAssetSelected(assetId)
            }

            val asset = (assetState as? AssetUiState.MatchFound)?.asset?.takeIf { it.id == assetId }
            when {
                asset == null -> AssetLoadingScreen(
                    agent = currentAgent,
                    assetId = assetId,
                    onBack = ::leaveManagement,
                )

                else -> ManagementActionScreen(
                    agent = currentAgent,
                    asset = asset,
                    state = managementState,
                    onActionChange = assetViewModel::onActionSelected,
                    onUserQueryChange = assetViewModel::onUserQueryChanged,
                    onUserSelected = assetViewModel::onUserSelected,
                    onSiteSelected = assetViewModel::onSiteSelected,
                    onRetrySites = assetViewModel::onManagementActionRequested,
                    onBack = ::leaveManagement,
                    backLabel = if (lookupFromManual) "Back to manual entry" else "Scan again",
                    onSubmit = assetViewModel::onSubmitUpdate,
                    onManageAgain = assetViewModel::onManagementActionRequested,
                )
            }
        }
    }
}

/** Central route definitions keep argument encoding and destination patterns together. */
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

private fun ScanResult.userFacingMessage(): String = when (this) {
    ScanResult.ScannerUnavailable ->
        "Google Code Scanner is unavailable on this device. Enter the serial manually."
    ScanResult.ScannerModuleUnavailable ->
        "The scanner module is not available yet. Check the connection and try again, or enter the serial manually."
    is ScanResult.InvalidValue -> reason.userFacingMessage()
    ScanResult.UnexpectedFailure ->
        "The scanner could not read the label. Try again or enter the serial manually."
    ScanResult.Cancellation,
    is ScanResult.Success,
    -> ""
}

private fun InvalidIdentifierReason.userFacingMessage(): String = when (this) {
    InvalidIdentifierReason.Empty -> "The scanned value was empty. Enter the serial manually."
    InvalidIdentifierReason.UnsupportedFormat ->
        "That barcode is not an approved serial-number format. Nothing was looked up in Halo."
}
