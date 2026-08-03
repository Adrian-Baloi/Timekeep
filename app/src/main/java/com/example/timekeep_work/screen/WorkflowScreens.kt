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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.timekeep_work.Agent
import com.example.timekeep_work.Asset
import com.example.timekeep_work.Header
import com.example.timekeep_work.ui.theme.AppUIPalette

internal data class ConfirmationState(
    val onDismiss: () -> Unit,
    val onConfirm: () -> Unit,
)

@Composable
internal fun ManualSerialEntryScreen(
    agent: Agent,
    serial: String,
    onSerialChange: (String) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
) = WorkflowPage(agent, "Enter serial number", "Type the serial exactly as it appears on the device.") {
    Panel {
        OutlinedTextField(
            value = serial,
            onValueChange = onSerialChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Serial number") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Text("Demo: use NONE for no match or MULTI for several matches.", style = bodyStyle(), color = AppUIPalette.TextSecondary)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Search assets", onSearch, enabled = serial.isNotBlank())
        SecondaryButton("Back", onBack)
    }
}

@Composable
internal fun SearchingScreen(
    agent: Agent,
    serial: String,
    onCancel: () -> Unit,
    onShowFakeResult: () -> Unit,
) = WorkflowPage(agent, "Searching Halo ITSM", "Looking for serial number") {
    Panel(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = AppUIPalette.Primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(20.dp))
        SerialNumber(serial)
        Spacer(Modifier.height(12.dp))
        Text("This demo waits for you so the searching state can be reviewed.", style = bodyStyle(), color = AppUIPalette.TextSecondary)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Show fake result", onShowFakeResult)
        SecondaryButton("Cancel", onCancel)
    }
}

@Composable
internal fun NoMatchingAssetScreen(
    agent: Agent,
    serial: String,
    onScanAgain: () -> Unit,
    onManualEntry: () -> Unit,
) = WorkflowPage(agent, "No matching asset", "No asset has this serial number.") {
    Panel {
        SerialNumber(serial)
        Spacer(Modifier.height(16.dp))
        Notice("Nothing was changed. Check the label or try scanning again.", Color(0xFFFFF4DA), Color(0xFF8A5A00))
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Scan again", onScanAgain)
        SecondaryButton("Enter serial manually", onManualEntry)
    }
}

@Composable
internal fun OneMatchingAssetScreen(
    agent: Agent,
    asset: Asset,
    onReviewAsset: () -> Unit,
    onWrongAsset: () -> Unit,
) = WorkflowPage(agent, "One asset found", "Review the match before making any change.") {
    AssetSummary(asset)
    Spacer(Modifier.height(16.dp))
    PrimaryButton("Review asset", onReviewAsset)
    SecondaryButton("Wrong asset — scan again", onWrongAsset)
}

@Composable
internal fun MultipleAssetsScreen(
    agent: Agent,
    assets: List<Asset>,
    onSelect: (Asset) -> Unit,
    onScanAgain: () -> Unit,
) = WorkflowPage(agent, "Choose the correct asset", "${assets.size} assets share this serial. Compare the details.") {
    assets.forEach { asset ->
        CandidateAsset(asset, onClick = { onSelect(asset) })
        Spacer(Modifier.height(12.dp))
    }
    SecondaryButton("None are correct — scan again", onScanAgain)
}

@Composable
internal fun AssetDetailsScreen(
    agent: Agent,
    asset: Asset,
    onBack: () -> Unit,
    onScanAgain: () -> Unit,
    onReviewed: () -> Unit,
) = WorkflowPage(agent, "Asset details", "Confirm this is the device in front of you.") {
    Panel {
        SerialNumber(asset.serialNumber)
        Spacer(Modifier.height(20.dp))
        DetailRow("Hostname", asset.hostname)
        DetailRow("Asset tag", asset.assetTag)
        DetailRow("Device", listOfNotNull(asset.manufacturer, asset.model).joinToString(" "))
        DetailRow("Assigned user", asset.assignedUser)
        DetailRow("Status", asset.status)
        DetailRow("Location", asset.location)
        DetailRow("Halo asset ID", asset.id.toString())
    }
    Spacer(Modifier.height(16.dp))
    Notice("Management actions stay locked until you confirm this review.", Color(0xFFE7F0F8), AppUIPalette.Primary)
    Spacer(Modifier.height(16.dp))
    PrimaryButton("I've reviewed this asset", onReviewed)
    SecondaryButton("Wrong asset — scan again", onScanAgain)
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to result") }
}

@Composable
internal fun ManagementActionScreen(
    agent: Agent,
    asset: Asset,
    selectedStatus: String,
    reviewed: Boolean,
    onStatusChange: (String) -> Unit,
    onReviewedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    confirmation: ConfirmationState? = null,
) {
    WorkflowPage(agent, "Management action", "Choose the intended change for this asset.") {
        AssetSummary(asset, compact = true)
        Spacer(Modifier.height(16.dp))
        Panel {
            Text("Change status to", style = sectionStyle())
            Spacer(Modifier.height(8.dp))
            listOf("In use", "In stock", "Repair", "Retired").forEach { status ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onStatusChange(status) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedStatus == status, onClick = { onStatusChange(status) })
                    Text(status, style = bodyStyle())
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = reviewed, onCheckedChange = onReviewedChange)
                Text("I reviewed the asset details", style = bodyStyle())
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Review change", onContinue, enabled = reviewed)
        if (!reviewed) {
            Text(
                "Review confirmation is required before write actions are enabled.",
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
                    Text("${asset.hostname ?: asset.assetTag ?: asset.serialNumber} will be changed to “$selectedStatus”.")
                    Spacer(Modifier.height(12.dp))
                    Text("Serial: ${asset.serialNumber}", fontWeight = FontWeight.SemiBold)
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
internal fun SuccessfulUpdateScreen(
    agent: Agent,
    asset: Asset,
    newStatus: String,
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
        Text("Status changed to $newStatus", style = bodyStyle(), color = Color(0xFF24713A))
        Spacer(Modifier.height(8.dp))
        Text("Serial ${asset.serialNumber}", style = bodyStyle(), color = AppUIPalette.TextSecondary)
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
    onSecondary: () -> Unit,
) = WorkflowPage(agent, title, "The requested update could not be completed.") {
    Notice(message, Color(0xFFFFE8E7), Color(0xFF9A2722))
    Spacer(Modifier.height(20.dp))
    PrimaryButton(primaryLabel, onPrimary)
    SecondaryButton("Back to scanner", onSecondary)
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
        Spacer(Modifier.height(22.dp))
        Text(title, style = titleStyle())
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = bodyStyle(), color = AppUIPalette.TextSecondary)
        Spacer(Modifier.height(18.dp))
        content()
        Spacer(Modifier.height(72.dp))
    }
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
        SerialNumber(asset.serialNumber)
        Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
        Text(asset.hostname ?: "No hostname", style = sectionStyle())
        Text(listOfNotNull(asset.manufacturer, asset.model).joinToString(" "), style = bodyStyle(), color = AppUIPalette.TextSecondary)
        if (!compact) {
            Spacer(Modifier.height(12.dp))
            DetailRow("Asset tag", asset.assetTag)
            DetailRow("Assigned to", asset.assignedUser)
            DetailRow("Status", asset.status)
            DetailRow("Location", asset.location)
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
            Text(asset.hostname ?: "No hostname", style = sectionStyle())
            Text("Serial ${asset.serialNumber}", color = AppUIPalette.Primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("${asset.assetTag ?: "No asset tag"} • ${asset.model ?: "Unknown model"}", style = bodyStyle())
            Text("${asset.assignedUser ?: "Unassigned"} • ${asset.location ?: "No location"}", style = bodyStyle(), color = AppUIPalette.TextSecondary)
            Spacer(Modifier.height(12.dp))
            Text("Select and review →", color = AppUIPalette.Primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SerialNumber(serial: String) {
    Column {
        Text("SERIAL NUMBER", color = AppUIPalette.TextSecondary, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
        Text(serial, color = AppUIPalette.TextPrimary, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
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
internal fun FakeStatePicker(
    current: AppScreen,
    onDismiss: () -> Unit,
    onSelect: (AppScreen) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            shape = RoundedCornerShape(18.dp),
            color = AppUIPalette.Surface,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Fake UI states", style = titleStyle())
                Text("Open any screen without networking.", style = bodyStyle(), color = AppUIPalette.TextSecondary)
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                    items(AppScreen.entries) { destination ->
                        TextButton(
                            onClick = { onSelect(destination) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = if (destination == current) "• ${destination.title}" else destination.title,
                                modifier = Modifier.fillMaxWidth(),
                                color = AppUIPalette.TextPrimary,
                            )
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
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
