package com.newforma.timekeep

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.newforma.timekeep.ui.TimekeepRoot
import com.newforma.timekeep.ui.TimekeepTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels {
        AppViewModel.Factory((application as TimekeepApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthIntent(intent)
        setContent {
            TimekeepTheme {
                TimekeepRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val expectedPath = BuildConfig.HALO_REDIRECT_PATH
        if (uri.scheme == BuildConfig.HALO_REDIRECT_SCHEME &&
            uri.host == BuildConfig.HALO_REDIRECT_HOST &&
            (expectedPath.isBlank() || uri.path == expectedPath)
        ) {
            viewModel.handleAuthorizationCallback(uri)
            intent.data = null
        }
    }
}
