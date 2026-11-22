package com.farm.delivery.ui

import java.time.LocalDate

import com.farm.delivery.route.pastDays

import com.farm.delivery.route.dayLabel

import com.farm.core.ui.ScreenTitle

import com.farm.core.ui.ProfileScreen

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.material3.NavigationBarItemDefaults

import androidx.compose.material3.NavigationBarItem

import androidx.compose.material3.NavigationBar

import androidx.compose.material3.Scaffold

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farm.core.auth.AuthManager
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.CenteredLoading
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmCard
import com.farm.core.ui.FarmColors
import com.farm.core.ui.Pill
import com.farm.core.ui.TextVariant

@Composable
fun DeliveryHomeScreen(onLogin: () -> Unit, onTeam: () -> Unit, onRoute: (String?) -> Unit) {
    val auth by AuthManager.state.collectAsState()
    if (auth.isLoading) return CenteredLoading()
    if (auth.isAuthenticated && auth.hasRole("delivery")) return PartnerTabs(onLogin, onTeam, onRoute)

    Column(
        Modifier.fillMaxSize().background(FarmColors.Background).systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        run {
            FarmCard(Modifier.fillMaxWidth().widthIn(max = 400.dp)) {
                FText("🚚 Farm Delivery", variant = TextVariant.H1)
                FText(
                    "Welcome! Log in with your Delivery Partner account to access your assigned pooled routes and stops.",
                    color = FarmColors.TextLabel, modifier = Modifier.padding(top = 8.dp),
                )
                FarmButton("Log In / Sign Up", onLogin, Modifier.fillMaxWidth().padding(top = 24.dp))
                FarmButton("👥  Meet the Team", onTeam, Modifier.fillMaxWidth().padding(top = 8.dp), variant = ButtonVariant.Ghost)
            }
            return@Column
        }

    }
}

private enum class PartnerTab { ROUTE, HISTORY, PROFILE }

/** Signed-in partner: bottom bar with today's Route, History (earlier days) and Profile. */
@Composable
private fun PartnerTabs(onLogin: () -> Unit, onTeam: () -> Unit, onRoute: (String?) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(PartnerTab.ROUTE) }
    Scaffold(
        containerColor = FarmColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = FarmColors.Surface, tonalElevation = 0.dp) {
                listOf(PartnerTab.ROUTE to ("🗺️" to "Route"), PartnerTab.HISTORY to ("🕘" to "History"), PartnerTab.PROFILE to ("👤" to "Profile"))
                    .forEach { (t, item) ->
                        NavigationBarItem(
                            selected = tab == t, onClick = { tab = t },
                            icon = { Text(item.first, fontSize = 20.sp) },
                            label = { Text(item.second, fontSize = 12.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedTextColor = FarmColors.Primary, unselectedTextColor = FarmColors.TextMuted, indicatorColor = FarmColors.PrimaryLight,
                            ),
                        )
                    }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                PartnerTab.ROUTE -> RouteScreen(dateParam = null, onBack = null, onHome = { tab = PartnerTab.ROUTE })
                PartnerTab.HISTORY -> HistoryTab(onRoute)
                PartnerTab.PROFILE -> ProfileScreen("Farm delivery partner", onLogin = onLogin, onTeam = onTeam)
            }
        }
    }
}

/** The last seven days; each opens that day's route (GET /logistics/my-route?pool_date=). */
@Composable
private fun HistoryTab(onRoute: (String?) -> Unit) {
    val days = remember { pastDays(LocalDate.now(), 7) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        ScreenTitle("History", "Routes from the last week")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { d ->
                FarmCard(Modifier.fillMaxWidth(), onClick = { onRoute(d.toString()) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FText(dayLabel(d, LocalDate.now()), variant = TextVariant.H2, modifier = Modifier.weight(1f))
                        Text("›", fontSize = 22.sp, color = FarmColors.TextMuted)
                    }
                }
            }
        }
    }
}
