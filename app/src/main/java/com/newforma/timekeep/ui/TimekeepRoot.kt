package com.newforma.timekeep.ui

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newforma.timekeep.AppUiState
import com.newforma.timekeep.AppViewModel
import com.newforma.timekeep.model.HaloAgentSummary

@Composable
fun TimekeepRoot(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val launchUrl by viewModel.launchUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(launchUrl) {
        val url = launchUrl ?: return@LaunchedEffect
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, url.toUri())
        viewModel.onAuthorizationUrlLaunched()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val state = uiState) {
            is AppUiState.Loading -> LoadingScreen(state.title, state.detail)
            is AppUiState.SetupBlocked -> SetupBlockedScreen(state, viewModel::retry)
            is AppUiState.SignedOut -> SignedOutScreen(state, viewModel::retry, viewModel::onSignInClicked)
            is AppUiState.Authenticated -> AuthenticatedScreen(state, viewModel::signOut)
        }
    }
}

@Composable
private fun LoadingScreen(
    title: String,
    detail: String,
) {
    ScreenFrame(title = title, subtitle = detail) {
        StatusCard(
            title = "Current slice",
            body = "Launch -> sign in -> /Agent/me -> placeholder home -> sign out.",
        )
    }
}

@Composable
private fun SetupBlockedScreen(
    state: AppUiState.SetupBlocked,
    onRetry: () -> Unit,
) {
    ScreenFrame(
        title = "Complete local setup",
        subtitle = "The app will not start the Halo flow until the missing runtime values are supplied.",
    ) {
        StatusCard(
            title = state.environmentLabel,
            body = "Tenant: ${state.tenantId}",
        )
        StatusCard(
            title = "Missing values",
            body = state.missingFields.joinToString(separator = "\n") { "- $it" },
        )
        StatusCard(
            title = "What to do",
            body = state.detail,
            emphasized = true,
        )
        OutlinedButton(onClick = onRetry) {
            Text("Retry after setup")
        }
    }
}

@Composable
private fun SignedOutScreen(
    state: AppUiState.SignedOut,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
) {
    ScreenFrame(
        title = "Timekeep",
        subtitle = "Halo agent authentication shell for the Newforma mobile workflow.",
    ) {
        state.message?.let {
            StatusCard(
                title = "Latest status",
                body = it,
                emphasized = true,
            )
        }
        StatusCard(
            title = state.environmentLabel,
            body = "Tenant: ${state.tenantId}",
        )
        StatusCard(
            title = "Auth readiness",
            body = buildString {
                appendLine("OIDC metadata loaded: ${yesNo(state.discoveryLoaded)}")
                appendLine("PKCE S256 supported: ${yesNo(state.pkceSupported)}")
                appendLine("Client secret present: ${yesNo(state.hasClientSecret)}")
                append("Token auth methods: ")
                append(
                    if (state.tokenAuthMethods.isEmpty()) {
                        "Not loaded"
                    } else {
                        state.tokenAuthMethods.joinToString()
                    },
                )
            },
        )
        if (!state.canStartSignIn) {
            StatusCard(
                title = "Why sign-in is blocked",
                body = if (!state.discoveryLoaded) {
                    "The app still needs live tenant auth metadata before it can build the browser request."
                } else {
                    "The tenant currently expects client-secret token auth, so sandbox testing needs HALO_CLIENT_SECRET configured."
                },
                emphasized = true,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onSignIn,
                enabled = state.canStartSignIn,
                modifier = Modifier.weight(1f),
            ) {
                Text("Sign in to Halo")
            }
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
            ) {
                Text("Retry metadata")
            }
        }
    }
}

@Composable
private fun AuthenticatedScreen(
    state: AppUiState.Authenticated,
    onSignOut: () -> Unit,
) {
    ScreenFrame(
        title = "Authenticated",
        subtitle = "The first sprint stops at a verified Halo session and placeholder home.",
    ) {
        state.message?.let {
            StatusCard(
                title = "Latest status",
                body = it,
            )
        }
        StatusCard(
            title = state.environmentLabel,
            body = "Tenant: ${state.tenantId}",
        )
        AgentCard(agent = state.agent)
        StatusCard(
            title = "Next sprint boundary",
            body = "No scan, asset lookup, reassignment, or registration actions are exposed yet.",
        )
        OutlinedButton(onClick = onSignOut) {
            Text("Sign out")
        }
    }
}

@Composable
private fun ScreenFrame(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    emphasized: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider()
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AgentCard(agent: HaloAgentSummary) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = agent.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            agent.email?.let { DetailLine("Email", it) }
            agent.jobTitle?.let { DetailLine("Job title", it) }
            agent.primaryTeam?.let { DetailLine("Primary team", it) }
            agent.initials?.let { DetailLine("Initials", it) }
            agent.id?.let { DetailLine("Agent ID", it) }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"
