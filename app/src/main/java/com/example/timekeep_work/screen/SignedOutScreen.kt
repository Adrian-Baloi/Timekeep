package com.example.timekeep_work.screen

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timekeep_work.Header
import com.example.timekeep_work.ui.theme.AppUIPalette

/** Sign-in landing screen shown when no valid Halo session is available. */
@Composable
fun SignedOutScreen(
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUIPalette.Background)
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
private fun SignInPanel(
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = AppUIPalette.Surface,
        contentColor = AppUIPalette.TextPrimary,
        border = BorderStroke(1.dp, AppUIPalette.Border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = "Sign in",
                color = AppUIPalette.TextPrimary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Use your Halo ITSM account.",
                color = AppUIPalette.TextSecondary,
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
                    containerColor = AppUIPalette.Primary,
                    contentColor = AppUIPalette.OnPrimary,
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
