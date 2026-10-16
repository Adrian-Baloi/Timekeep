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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timekeep_work.Agent
import com.example.timekeep_work.Asset
import com.example.timekeep_work.AssignableUser
import com.example.timekeep_work.AssetManagementAction
import com.example.timekeep_work.AssetSite
import com.example.timekeep_work.Header
import com.example.timekeep_work.ui.theme.AppUIPalette

internal data class LookupRecoveryActions(
    val onRescan: () -> Unit,
    val onManualCorrection: () -> Unit,
)

// Lookup screens: manual entry, progress, no match, and candidate selection.

@Composable
internal fun ManualSerialEntryScreen(
    agent: Agent,
    serial: String,
    onSerialChange: (String) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
) = WorkflowPage(agent, "Enter identifier", "") {
    Panel {
        OutlinedTextField(
            value = serial,
            onValueChange = onSerialChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Asset tag or serial number") },
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Search assets", onSearch, enabled = serial.isNotBlank())
        SecondaryButton("Back", onBack)
    }
}

@Composable
internal fun SearchingScreen(
    agent: Agent,
    serial: String,
    onCancel: () -> Unit,
) = WorkflowPage(agent, "Searching", "") {
    Panel(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = AppUIPalette.Primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(20.dp))
        SerialNumber(serial)
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
) = WorkflowPage(agent, "No matching asset", "") {
    Panel {
        SerialNumber(serial)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Retry lookup", onRetry)
        RecoveryActions(recoveryActions)
    }
}

@Composable
internal fun MultipleAssetsScreen(
    agent: Agent,
    assets: List<Asset>,
    onSelect: (Int) -> Unit,
    recoveryActions: LookupRecoveryActions,
) = WorkflowPage(agent, "Choose asset", "${assets.size} matches") {
    assets.forEach { asset ->
        CandidateAsset(asset, onClick = { onSelect(asset.id) })
        Spacer(Modifier.height(12.dp))
    }
    RecoveryActions(recoveryActions)
}

@Composable
private fun AssetDetailsPanel(
    asset: Asset,
    showAll: Boolean,
) {
    Panel {
        MatchedIdentifier(asset.matchedIdentifier)
        Spacer(Modifier.height(16.dp))
        DetailRow("Device", asset.hostname)
        DetailRow("Asset tag", asset.assetTag)
        DetailRow("Assigned user", asset.assignedUser)
        DetailRow("Location", asset.location)
        if (showAll) {
            DetailRow("Serial number", asset.serialNumber)
            DetailRow("Status", asset.status)
        }
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
    onBack: () -> Unit,
    backLabel: String,
    onSubmit: () -> Unit,
    onManageAgain: () -> Unit,
) {
    WorkflowPage(agent, "Asset details", "") {
        if (state.phase == ManagementPhase.Success) {
            Notice("Asset updated", Color(0xFFE2F4E8), Color(0xFF24713A))
            Spacer(Modifier.height(16.dp))
        }
        AssetDetailsPanel(asset, showAll = state.phase == ManagementPhase.Success)
        if (state.phase == ManagementPhase.Success) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Manage asset", onManageAgain)
        } else {
            Spacer(Modifier.height(24.dp))
            Text("Management action", style = sectionStyle())
            Spacer(Modifier.height(12.dp))
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
                        TertiaryButton("Retry sites", onRetrySites)
                    }
                }
            }
            state.failure?.let { failure ->
                Spacer(Modifier.height(12.dp))
                Notice(failure.message, Color(0xFFFFE8E7), Color(0xFF9A2722))
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                label = when {
                    state.phase == ManagementPhase.Updating -> "Sending..."
                    state.selectedAction == AssetManagementAction.Deploying -> "Deploy asset"
                    state.selectedAction == AssetManagementAction.ReturningToInventory -> "Return to inventory"
                    else -> "Send update"
                },
                onClick = onSubmit,
                enabled = state.canSubmit && state.phase == ManagementPhase.Editing,
            )
        }
        if (state.phase != ManagementPhase.Updating) {
            SecondaryButton(backLabel, onBack)
        }
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
            label = { Text("Search user") },
            supportingText = {
                Text(
                    when {
                        state.selectedUser != null ->
                            "Selected ${if (state.selectedUser.isAgent) "agent" else "user"}"
                        state.userSearching -> "Searching Halo ITSM..."
                        state.userQuery.trim().length < AssetViewModel.MIN_USER_QUERY_LENGTH ->
                            "Enter 4 or more characters"
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
        Title(title, subtitle)
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Title(
    title: String,
    subtitle: String,
) {
        Spacer(Modifier.height(22.dp))
        Text(title, style = titleStyle())
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = bodyStyle(), color = AppUIPalette.TextSecondary)
        }
        Spacer(Modifier.height(18.dp))
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
            DetailRow("Assigned user", asset.assignedUser)
            DetailRow("Location", asset.location)
            Spacer(Modifier.height(12.dp))
            Text("Select asset →", color = AppUIPalette.Primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RecoveryActions(actions: LookupRecoveryActions) {
    SecondaryButton("Scan again", actions.onRescan)
    TertiaryButton("Enter manually", actions.onManualCorrection)
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
