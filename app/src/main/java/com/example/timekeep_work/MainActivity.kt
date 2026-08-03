package com.example.timekeep_work

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.timekeep_work.screen.Screen
import com.example.timekeep_work.ui.theme.TimekeepWorkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimekeepWorkTheme {
                Screen()
            }
        }
    }
}