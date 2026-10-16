package com.example.timekeep_work

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timekeep_work.ui.theme.AppUIPalette

/** Shared product heading used across authenticated and signed-out screens. */
@Composable
public fun Header(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "Asset Scanner",
            color = AppUIPalette.TextPrimary,
            fontFamily = FontFamily.SansSerif,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "HALO ITSM",
            color = AppUIPalette.TextSecondary,
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.1.sp,
        )
    }
}
