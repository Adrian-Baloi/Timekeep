package com.example.timekeep_work

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.timekeep_work.ui.theme.TimekeepWorkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimekeepWorkTheme {
                var isSignedIn by remember {
                    mutableStateOf(false)
                }
                if (!isSignedIn) {
                    SignedOutScreen(onSignInClick = { isSignedIn = true })
                } else {
                    TestScreen(onClickEvent = { isSignedIn = false })
                }
            }
        }
    }
}