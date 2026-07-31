package com.example.timekeep_work

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.intellij.lang.annotations.JdkConstants

@Composable
fun TestScreen(onClickEvent: () -> Unit ,modifier: Modifier = Modifier) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Text("Something is happening...")
        Button(onClick = onClickEvent) { }
    }
}