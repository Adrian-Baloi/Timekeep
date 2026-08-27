package com.example.timekeep_work.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timekeep_work.Agent
import com.example.timekeep_work.Asset
import com.example.timekeep_work.AssignableUser
import com.example.timekeep_work.AssetManagementAction
import com.example.timekeep_work.AssetSite
import com.example.timekeep_work.Header
import com.example.timekeep_work.ui.theme.AppUIPalette

internal data class ConfirmationState(
    val onDismiss: () -> Unit,
    val onConfirm: () -> Unit,
)

internal data class LookupRecoveryActions(
    val onRescan: () -> Unit,
    val onManualCorrection: () -> Unit,
    val onReturnToScanner: () -> Unit,
    val onLogout: () -> Unit,
)

// Lookup screens: manual entry, progress, no match, and candidate selection.

@Composable
internal fun ManualSerialEntryScreen(
    agent: Agent,
    serial: String,
    onSerialChange: (String) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
) = WorkflowPage(agent, "Enter asset identifier", "Type the asset tag or serial number exactly as it appears on the device.") {
    Panel {
        OutlinedTextField(
            value = serial,
            onValueChange = onSerialChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Asset tag or serial number") },
            singleLine = true,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Search assets", onSearch, enabled = serial.isNotBlank())
        SecondaryButton("Back", onBack)
    }
}

@Composable
internal fun ScanningScreen(
    agent: Agent,
    onCancel: () -> Unit,
    onSerialDetected: () -> Unit,
) = WorkflowPage(agent, "Scan device", "Point the camera at the serial label.") {
    Panel(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = AppUIPalette.Primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(20.dp))
        Text("Waiting for a barcode or QR code", style = sectionStyle())
        Spacer(Modifier.height(8.dp))
        Text(
            "Camera integration is simulated in this prototype.",
            style = bodyStyle(),
            color = AppUIPalette.TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Use demo scan", onSerialDetected)
        SecondaryButton("Cancel", onCancel)
    }
}

@Composable
internal fun SearchingScreen(
    agent: Agent,
    serial: String,
    onCancel: () -> Unit,
) = WorkflowPage(agent, "Searching Halo ITSM", "Looking for asset tag or serial number") {
    Panel(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = AppUIPalette.Primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(20.dp))
        SerialNumber(serial)
        Spacer(Modifier.height(12.dp))
        Text("The lookup continues while this screen is active.", style = bodyStyle(), color = AppUIPalette.TextSecondary)
        Spacer(Modifier.height(16.dp))
        SecondaryButton("Cancel", onCancel)
    }
}

@Composable
internal fun NoMatchingAssetScreen(
    agent: Agent,
    serial: String,
    onRetry: () -> Unit,
    recoveryActions: LookupRecoveryActions,
) = WorkflowPage(agent, "No matching asset", "No asset has this asset tag or serial number.") {
    Panel {
        SerialNumber(serial)
        Spacer(Modifier.height(16.dp))
        Notice("Nothing was changed. Check the label or try scanning again.", Color(0xFFFFF4DA), Color(0xFF8A5A00))
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Retry lookup", onRetry)
        RecoveryActions(recoveryActions)
    }
}

@Composable
internal fun OneMatchingAssetScreen(
    agent: Agent,
    asset: Asset,
    onReviewAsset: () -> Unit,
    recoveryActions: LookupRecoveryActions,
) = WorkflowPage(agent, "One asset found", "Review the match before making any change.") {
    AssetSummary(asset)
    Spacer(Modifier.height(16.dp))
    PrimaryButton("Review asset", onReviewAsset)
    RecoveryActions(recoveryActions)
}

@Composable
internal fun MultipleAssetsScreen(
    agent: Agent,
    assets: List<Asset>,
    onSelect: (Int) -> Unit,
    recoveryActions: LookupRecoveryActions,
) = WorkflowPage(agent, "Choose the correct asset", "${assets.size} assets share this identifier. Compare the details.") {
    assets.forEach { asset ->
        CandidateAsset(asset, onClick = { onSelect(asset.id) })
        Spacer(Modifier.height(12.dp))
    }
    Notice("No asset is selected until you choose one.", Color(0xFFE7F0F8), AppUIPalette.Primary)
    RecoveryActions(recoveryActions)
}

@Composable
private fun AssetDetailsPanel(
    asset: Asset,
) {
    Panel {
        MatchedIdentifier(asset.matchedIdentifier)
        Spacer(Modifier.height(20.dp))
        DetailRow("Hostname or device name", asset.hostname)
        DetailRow("Serial number", asset.serialNumber)
        DetailRow("Asset tag", asset.assetTag)
        DetailRow("Manufacturer", asset.manufacturer)
        DetailRow("Model", asset.model)
        DetailRow("Assigned user", asset.assignedUser)
        DetailRow("Status", asset.status)
        DetailRow("Location", asset.location)
        DetailRow("Last updated", asset.lastUpdated)
        DetailRow("Halo asset ID", asset.id.toString())
    }
}

// Management screen and its form controls.

@Composable
internal fun ManagementActionScreen(
    agent: Agent,
    asset: Asset,
    state: ManagementUiState,
    onActionChange: (AssetManagementAction) -> Unit,
    onUserQueryChange: (String) -> Unit,
    onUserSelected: (AssignableUser) -> Unit,
    onSiteSelected: (AssetSite) -> Unit,
    onRetrySites: () -> Unit,
    onReviewedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    confirmation: ConfirmationState? = null,
) {
    WorkflowPage(agent, "Asset details", "Confirm this is the device in front of you.") {
        AssetDetailsPanel(asset)
        Title("Management action", "Choose the intended change for this asset.")
        Spacer(Modifier.height(16.dp))
        Panel {
            ActionDropdown(state.selectedAction, onActionChange)
            if (state.selectedAction == AssetManagementAction.Deploying) {
                Spacer(Modifier.height(16.dp))
                UserSearchDropdown(state, onUserQueryChange, onUserSelected)
            }
            if (state.selectedAction != null) {
                Spacer(Modifier.height(16.dp))
                SiteDropdown(state, onSiteSelected)
            }
            state.optionsFailure?.let { message ->
                Spacer(Modifier.height(12.dp))
                Notice(message, Color(0xFFFFE8E7), Color(0xFF9A2722))
                if (state.sites.isEmpty() && !state.sitesLoading) {
                    TertiaryButton("Retry site list", onRetrySites)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.reviewed,
                    onCheckedChange = onReviewedChange,
                    enabled = state.selectedAction != null && state.selectedSite != null &&
                        (state.selectedAction != AssetManagementAction.Deploying || state.selectedUser != null),
                )
                Text("I reviewed the asset details", style = bodyStyle())
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            label = if (state.phase == ManagementPhase.Updating) "Updating..." else "Review change",
            onClick = onContinue,
            enabled = state.canReview && state.phase != ManagementPhase.Updating,
        )
        if (!state.canReview) {
            Text(
                "Choose the required fields and review the asset before continuing.",
                modifier = Modifier.padding(top = 8.dp),
                style = bodyStyle(),
                color = AppUIPalette.TextSecondary,
            )
        }
        SecondaryButton("Back to asset", onBack)
    }

    if (confirmation != null) {
        AlertDialog(
            onDismissRequest = confirmation.onDismiss,
            title = { Text("Confirm asset update") },
            text = {
                Column {
                    Text(state.confirmationMessage(asset))
                    Spacer(Modifier.height(12.dp))
                    Text("Matched identifier: ${asset.matchedIdentifier}", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("This will write to Halo ITSM.", color = AppUIPalette.TextSecondary)
                }
            },
            confirmButton = { Button(onClick = confirmation.onConfirm) { Text("Confirm update") } },
            dismissButton = { TextButton(onClick = confirmation.onDismiss) { Text("Go back") } },
        )
    }
}

@Composable
private fun ActionDropdown(
    selectedAction: AssetManagementAction?,
    onActionChange: (AssetManagementAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Text("Action", style = sectionStyle())
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedAction?.label ?: "Choose an action")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AssetManagementAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        expanded = false
                        onActionChange(action)
                    },
                )
            }
        }
    }
}

@Composable
private fun UserSearchDropdown(
    state: ManagementUiState,
    onQueryChange: (String) -> Unit,
    onUserSelected: (AssignableUser) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(state.userResults) {
        expanded = state.userResults.isNotEmpty()
    }
    Text("User", style = sectionStyle())
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.userQuery,
            onValueChange = {
                expanded = true
                onQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search active users and agents") },
            supportingText = {
                Text(
                    when {
                        state.selectedUser != null ->
                            "Selected ${if (state.selectedUser.isAgent) "agent" else "user"}"
                        state.userSearching -> "Searching Halo ITSM..."
                        state.userQuery.trim().length < AssetViewModel.MIN_USER_QUERY_LENGTH ->
                            "Enter at least 4 characters"
                        state.userResults.isEmpty() -> "No active users found"
                        else -> "Choose a result"
                    },
                )
            },
            singleLine = true,
        )
        DropdownMenu(
            expanded = expanded && state.userResults.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 280.dp, max = 520.dp),
        ) {
            state.userResults.forEach { user ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(user.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    if (user.isAgent) "Agent" else "User",
                                    user.email,
                                ).joinToString(" - "),
                                color = AppUIPalette.TextSecondary,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onUserSelected(user)
                    },
                )
            }
        }
    }
}

@Composable
private fun SiteDropdown(
    state: ManagementUiState,
    onSiteSelected: (AssetSite) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Text("Site", style = sectionStyle())
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = !state.sitesLoading && state.sites.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    state.sitesLoading -> "Loading active sites..."
                    state.selectedSite != null -> state.selectedSite.name
                    else -> "Choose the destination site"
                },
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 280.dp, max = 520.dp),
        ) {
            state.sites.forEach { site ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(site.name)
                            site.clientName?.let { Text(it, color = AppUIPalette.TextSecondary) }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSiteSelected(site)
                    },
                )
            }
        }
    }
}

private fun ManagementUiState.confirmationMessage(asset: Asset): String {
    val assetName = asset.assetTag ?: asset.hostname ?: asset.matchedIdentifier
    return when (selectedAction) {
        AssetManagementAction.Deploying ->
            "Deploy $assetName to ${selectedUser?.name} at ${selectedSite?.name}."
        AssetManagementAction.ReturningToInventory ->
            "Return $assetName to ${selectedSite?.name} and remove its assigned user."
        null -> "No management action is selected."
    }
}

// Terminal and recovery screens shown after loading or updating an asset.

@Composable
internal fun AssetLoadingScreen(
    agent: Agent,
    assetId: Int,
    onBack: () -> Unit,
) = WorkflowPage(agent, "Loading asset", "Retrieving asset $assetId from the data source.") {
    Panel(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = AppUIPalette.Primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(20.dp))
        SecondaryButton("Back", onBack)
    }
}

@Composable
internal fun SuccessfulUpdateScreen(
    agent: Agent,
    asset: Asset,
    message: String,
    onDone: () -> Unit,
    onViewAsset: () -> Unit,
) = WorkflowPage(agent, "Update successful", "Halo ITSM has been updated.") {
    Panel(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(56.dp).background(Color(0xFFE2F4E8), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("✓", color = Color(0xFF24713A), fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(16.dp))
        Text(asset.hostname ?: asset.assetTag ?: "Asset ${asset.id}", style = sectionStyle())
        Text(message, style = bodyStyle(), color = Color(0xFF24713A))
        Spacer(Modifier.height(8.dp))
        Text("Matched ${asset.matchedIdentifier}", style = bodyStyle(), color = AppUIPalette.TextSecondary)
    }
    Spacer(Modifier.height(16.dp))
    PrimaryButton("Scan another device", onDone)
    SecondaryButton("View updated asset", onViewAsset)
}

@Composable
internal fun WorkflowFailureScreen(
    agent: Agent,
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    recoveryActions: LookupRecoveryActions,
) = WorkflowPage(agent, title, "The requested Halo operation could not be completed.") {
    Notice(message, Color(0xFFFFE8E7), Color(0xFF9A2722))
    Spacer(Modifier.height(20.dp))
    PrimaryButton(primaryLabel, onPrimary)
    RecoveryActions(recoveryActions)
}

@Composable
internal fun AuthenticationFailureScreen(
    email: String,
    onSignIn: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(AppUIPalette.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Header()
        Spacer(Modifier.weight(1f))
        Panel(modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).align(Alignment.CenterHorizontally)) {
            Text("Session expired", style = titleStyle())
            Spacer(Modifier.height(8.dp))
            Text("The session for $email is no longer valid. Sign in again to continue. No changes were made.", style = bodyStyle(), color = AppUIPalette.TextSecondary)
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Sign in again", onSignIn)
            SecondaryButton("Cancel", onCancel)
        }
        Spacer(Modifier.weight(1f))
    }
}

// Shared workflow layout and visual components.

@Composable
private fun WorkflowPage(
    agent: Agent,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(AppUIPalette.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Header()
        Spacer(Modifier.height(14.dp))
        IdentityBanner(agent)
        Title(title, subtitle)
        content()
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun Title(
    title: String,
    subtitle: String,
) {
        Spacer(Modifier.height(22.dp))
        Text(title, style = titleStyle())
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = bodyStyle(), color = AppUIPalette.TextSecondary)
        Spacer(Modifier.height(18.dp))
}

@Composable
private fun IdentityBanner(agent: Agent) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFE7F0F8),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("●", color = Color(0xFF24713A), fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Signed in as ${agent.name}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(agent.email, color = AppUIPalette.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = AppUIPalette.Surface,
        border = BorderStroke(1.dp, AppUIPalette.Border),
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = horizontalAlignment, content = content)
    }
}

@Composable
private fun AssetSummary(asset: Asset, compact: Boolean = false) {
    Panel {
        MatchedIdentifier(asset.matchedIdentifier)
        Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
        Text(asset.hostname ?: "Device name unavailable", style = sectionStyle())
        if (!compact) {
            Spacer(Modifier.height(12.dp))
            DetailRow("Serial number", asset.serialNumber)
            DetailRow("Asset tag", asset.assetTag)
            DetailRow("Manufacturer", asset.manufacturer)
            DetailRow("Model", asset.model)
            DetailRow("Assigned to", asset.assignedUser)
            DetailRow("Status", asset.status)
            DetailRow("Location", asset.location)
            DetailRow("Last updated", asset.lastUpdated)
        }
    }
}

@Composable
private fun CandidateAsset(asset: Asset, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = AppUIPalette.Surface,
        border = BorderStroke(1.dp, AppUIPalette.Border),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(asset.hostname ?: "Device name unavailable", style = sectionStyle())
            Text("Matched ${asset.matchedIdentifier}", color = AppUIPalette.Primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            DetailRow("Serial number", asset.serialNumber)
            DetailRow("Asset tag", asset.assetTag)
            DetailRow("Manufacturer", asset.manufacturer)
            DetailRow("Model", asset.model)
            DetailRow("Assigned user", asset.assignedUser)
            DetailRow("Status", asset.status)
            DetailRow("Location", asset.location)
            DetailRow("Last updated", asset.lastUpdated)
            Spacer(Modifier.height(12.dp))
            Text("Select and review →", color = AppUIPalette.Primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RecoveryActions(actions: LookupRecoveryActions) {
    SecondaryButton("Rescan", actions.onRescan)
    TertiaryButton("Correct identifier manually", actions.onManualCorrection)
    TertiaryButton("Return to scanner", actions.onReturnToScanner)
    TertiaryButton("Logout", actions.onLogout)
}

@Composable
private fun SerialNumber(serial: String) {
    Column {
        Text("ASSET TAG OR SERIAL", color = AppUIPalette.TextSecondary, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
        Text(serial, color = AppUIPalette.TextPrimary, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MatchedIdentifier(identifier: String) {
    Column {
        Text("MATCHED IDENTIFIER", color = AppUIPalette.TextSecondary, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
        Text(identifier, color = AppUIPalette.TextPrimary, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = bodyStyle(), color = AppUIPalette.TextSecondary, modifier = Modifier.weight(0.42f))
        Text(value?.ifBlank { "—" } ?: "—", style = bodyStyle(), fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.58f))
    }
}

@Composable
private fun Notice(message: String, background: Color, foreground: Color) {
    Surface(modifier = Modifier.fillMaxWidth(), color = background, shape = RoundedCornerShape(12.dp)) {
        Text(message, modifier = Modifier.padding(16.dp), color = foreground, style = bodyStyle())
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppUIPalette.Primary, contentColor = AppUIPalette.OnPrimary),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AppUIPalette.Primary),
    ) { Text(label, color = AppUIPalette.Primary, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun TertiaryButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text(label, color = AppUIPalette.Primary, fontWeight = FontWeight.SemiBold) }
}

private fun titleStyle() = androidx.compose.ui.text.TextStyle(
    color = AppUIPalette.TextPrimary,
    fontFamily = FontFamily.SansSerif,
    fontSize = 26.sp,
    lineHeight = 32.sp,
    fontWeight = FontWeight.SemiBold,
)

private fun sectionStyle() = androidx.compose.ui.text.TextStyle(
    color = AppUIPalette.TextPrimary,
    fontFamily = FontFamily.SansSerif,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    fontWeight = FontWeight.SemiBold,
)

private fun bodyStyle() = androidx.compose.ui.text.TextStyle(
    color = AppUIPalette.TextPrimary,
    fontFamily = FontFamily.SansSerif,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)
