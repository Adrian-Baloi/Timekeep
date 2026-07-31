package com.example.timekeep_work

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private object SignedOutPalette {
    val Background = Color(0xFFF4F6F8)
    val Surface = Color(0xFFFFFFFF)
    val Border = Color(0xFFD7DCE2)
    val TextPrimary = Color(0xFF17212B)
    val TextSecondary = Color(0xFF59636E)
    val Primary = Color(0xFF173B5E)
    val OnPrimary = Color(0xFFFFFFFF)
}

@Composable
fun SignedOutScreen(
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SignedOutPalette.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Header()

        Spacer(modifier = Modifier.weight(1f))

        SignInPanel(
            onSignInClick = onSignInClick,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Header(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "Asset Scanner",
            color = SignedOutPalette.TextPrimary,
            fontFamily = FontFamily.SansSerif,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "HALO ITSM",
            color = SignedOutPalette.TextSecondary,
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.1.sp,
        )
    }
}

@Composable
private fun SignInPanel(
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SignedOutPalette.Surface,
        contentColor = SignedOutPalette.TextPrimary,
        border = BorderStroke(1.dp, SignedOutPalette.Border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = "Sign in",
                color = SignedOutPalette.TextPrimary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Use your Halo ITSM account.",
                color = SignedOutPalette.TextSecondary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSignInClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignedOutPalette.Primary,
                    contentColor = SignedOutPalette.OnPrimary,
                ),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
                Text(
                    text = "Sign in with Halo ITSM",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

        }
    }
}
