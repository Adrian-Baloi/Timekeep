package com.example.timekeep_work

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.timekeep_work.data.AppDependencies
import com.example.timekeep_work.screen.Screen
import com.example.timekeep_work.ui.theme.TimekeepWorkTheme

/** Android entry point that initializes dependencies and hosts the Compose application. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDependencies.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            TimekeepWorkTheme {
                Screen()
            }
        }
    }
}
