package com.farm.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farm.core.api.ApiConfig
import com.farm.core.auth.AuthManager

/**
 * The Profile tab of both apps: who is signed in, the server address, the team, and sign out
 * (or sign in when nobody is). [extra] adds app-specific rows above the shared ones.
 */
@Composable
fun ProfileScreen(appName: String, onLogin: () -> Unit, onTeam: () -> Unit, extra: @Composable () -> Unit = {}) {
    val auth by AuthManager.state.collectAsState()
    var changingServer by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(ApiConfig.baseUrl) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Profile", appName)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val user = auth.user
            FarmCard(Modifier.fillMaxWidth()) {
                if (auth.isAuthenticated && user != null) {
                    FText(user.name.ifBlank { "Signed in" }, variant = TextVariant.H2)
                    FText(user.phone, variant = TextVariant.Caption)
                    FText("Roles: ${user.roles.joinToString()}", variant = TextVariant.Caption, modifier = Modifier.padding(top = 4.dp))
                } else {
                    FText("Not signed in", variant = TextVariant.H2)
                    FText("Sign in to see your orders and routes.", variant = TextVariant.Caption)
                    FarmButton("Sign in", onLogin, Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            }
            extra()
            FarmCard(Modifier.fillMaxWidth()) {
                FText("Server", variant = TextVariant.Label)
                FText(server, variant = TextVariant.Caption, modifier = Modifier.padding(top = 2.dp))
                FarmButton("Change server", { changingServer = true }, Modifier.fillMaxWidth().padding(top = 8.dp), variant = ButtonVariant.Secondary)
            }
            FarmButton("Meet the team", onTeam, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
            if (auth.isAuthenticated) {
                FarmButton("Sign out", { AuthManager.logout() }, Modifier.fillMaxWidth(), variant = ButtonVariant.Destructive)
            }
        }
    }
    if (changingServer) {
        ServerAddressDialog(onDismiss = { changingServer = false }, onSaved = { changingServer = false; server = ApiConfig.baseUrl })
    }
}
