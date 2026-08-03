package com.example.timekeep_work.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timekeep_work.Agent
import com.example.timekeep_work.Header
import com.example.timekeep_work.ui.theme.AppUIPalette

@Composable
fun SignedInScreen(
    agent: Agent,
    onSignOutClick: () -> Unit,
    onScanClick: () -> Unit,
    onManualEntryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUIPalette.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Header()

        Spacer(Modifier.height(14.dp))

        SignedInIdentity(
            agent = agent,
            onSignOutClick = onSignOutClick,
        )

        Spacer(Modifier.height(34.dp))

        Text(
            text = "Ready to scan",
            color = AppUIPalette.TextPrimary,
            fontFamily = FontFamily.SansSerif,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Scan the serial label on the device to find its Halo ITSM asset.",
            color = AppUIPalette.TextSecondary,
            fontFamily = FontFamily.SansSerif,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(24.dp))

        ScanPanel(
            onScanClick = onScanClick,
            onManualEntryClick = onManualEntryClick,
        )

        Spacer(Modifier.height(18.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFE7F0F8),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = "You’ll review the matched asset before any management action is enabled.",
                modifier = Modifier.padding(16.dp),
                color = AppUIPalette.Primary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }

        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun SignedInIdentity(
    agent: Agent,
    onSignOutClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppUIPalette.Surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppUIPalette.Border),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                color = Color(0xFFE7F0F8),
                shape = RoundedCornerShape(21.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = agent.name
                            .split(" ")
                            .take(2)
                            .mapNotNull { it.firstOrNull()?.uppercase() }
                            .joinToString(""),
                        color = AppUIPalette.Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("●", color = Color(0xFF24713A), fontSize = 10.sp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Signed in",
                        color = Color(0xFF24713A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = agent.name,
                    color = AppUIPalette.TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = agent.email,
                    color = AppUIPalette.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TextButton(onClick = onSignOutClick) {
                Text(
                    text = "Sign out",
                    color = AppUIPalette.Primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ScanPanel(
    onScanClick: () -> Unit,
    onManualEntryClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
        color = AppUIPalette.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppUIPalette.Border),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Point the camera at the barcode or QR code on the device label.",
                modifier = Modifier.fillMaxWidth(),
                color = AppUIPalette.TextSecondary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth().height(76.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppUIPalette.Primary,
                    contentColor = AppUIPalette.OnPrimary,
                ),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                Text(
                    text = "Scan device",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onManualEntryClick,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AppUIPalette.Primary),
            ) {
                Text(
                    text = "Enter serial manually",
                    color = AppUIPalette.Primary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
