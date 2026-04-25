package com.priyatra.guide

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.priyatra.guide.auth.AuthViewModel
import com.priyatra.guide.data.TripRepository
import com.priyatra.guide.ui.TripViewModel
import com.priyatra.guide.ui.screens.AdminScreen
import com.priyatra.guide.ui.screens.HomeScreen
import com.priyatra.guide.ui.screens.ItineraryScreen
import com.priyatra.guide.ui.screens.LiveMapScreen
import com.priyatra.guide.ui.screens.LoginScreen
import com.priyatra.guide.ui.screens.ServicesScreen
import com.priyatra.guide.ui.screens.SpotDetailScreen
import com.priyatra.guide.ui.screens.TodayScreen
import com.priyatra.guide.ui.theme.PriyaTraTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* POC: scheduling still works with inexact alarms if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val context = LocalContext.current
            PriyaTraTheme {
                val navController = rememberNavController()
                val vm: TripViewModel = viewModel()
                val authVm: AuthViewModel = viewModel()
                val isLoggedIn by authVm.isLoggedIn.collectAsState()
                val isAdmin by authVm.isAdmin.collectAsState()
                val previewingAsUser by authVm.previewingAsUser.collectAsState()
                
                val navBackStack by navController.currentBackStackEntryAsState()
                val route = navBackStack?.destination?.route.orEmpty()
                val homeRoute = "home"

                when {
                    !isLoggedIn -> {
                        val loginError by authVm.loginError.collectAsState()
                        LoginScreen(
                            errorMessage = loginError,
                            onDismissError = { authVm.clearLoginError() },
                            onContinue = { phone -> authVm.loginWithPhone(phone) },
                        )
                    }
                    isAdmin && !previewingAsUser -> {
                        AdminScreen(
                            onLogout = { authVm.logout() },
                            onPreviewWithTrip = { tripId -> authVm.setPreviewAsUser(true, tripId) },
                        )
                    }
                    else -> {
                        val hideBottom = route.startsWith("spot/")
                        val showFab = !hideBottom && route != "map"
                        LaunchedEffect(isLoggedIn, isAdmin, previewingAsUser) {
                            com.priyatra.guide.data.TripRepository.refreshFromSession(context)
                        }
                        Scaffold(
                            floatingActionButton = {
                                if (showFab) {
                                    val tripForFab by TripRepository.tripState.collectAsState()
                                    FloatingActionButton(
                                        onClick = {
                                            val raw = tripForFab.supportPhone
                                            val normalized = raw.replace(" ", "")
                                            context.startActivity(
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalized")),
                                            )
                                        },
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    ) {
                                        Icon(Icons.Outlined.Call, contentDescription = "Help call")
                                    }
                                }
                            },
                            bottomBar = {
                                if (!hideBottom) {
                                    NavigationBar {
                                        val items = listOf(
                                            Tab("home", "Home", Icons.Outlined.Home),
                                            Tab("today", "Today", Icons.Outlined.CalendarToday),
                                            Tab("plan", "Plan", Icons.AutoMirrored.Outlined.Assignment),
                                            Tab("map", "Map", Icons.Outlined.Map),
                                            Tab("services", "Desk", Icons.Outlined.Train),
                                        )
                                        items.forEach { tab ->
                                            // Bottom tabs are siblings under "home" as root; always pop back to the home
                                            // entry first (string route) — findStartDestination().id can fail to pop correctly.
                                            NavigationBarItem(
                                                selected = when (tab.route) {
                                                    homeRoute -> route == homeRoute
                                                    else -> route == tab.route
                                                },
                                                onClick = {
                                                    navController.navigate(tab.route) {
                                                        popUpTo(homeRoute) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                },
                                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                                label = { Text(tab.label) },
                                            )
                                        }
                                    }
                                }
                            },
                        ) { padding ->
                            NavHost(
                                navController = navController,
                                startDestination = homeRoute,
                                modifier = Modifier.padding(padding),
                            ) {
                                composable(homeRoute) {
                                    HomeScreen(
                                        onOpenToday = {
                                            navController.navigate("today") { launchSingleTop = true }
                                        },
                                        onOpenPlan = {
                                            navController.navigate("plan") { launchSingleTop = true }
                                        },
                                        onOpenServices = {
                                            navController.navigate("services") { launchSingleTop = true }
                                        },
                                    )
                                }
                                composable("today") {
                                    TodayScreen(
                                        vm = vm,
                                        onOpenSpot = { id -> navController.navigate("spot/$id") },
                                        onOpenMaps = { lat, lng, label ->
                                            navController.navigate("map") { launchSingleTop = true }
                                        },
                                    )
                                }
                                composable("plan") {
                                    ItineraryScreen { id -> navController.navigate("spot/$id") }
                                }
                                composable("map") {
                                    LiveMapScreen { id -> navController.navigate("spot/$id") }
                                }
                                composable("services") {
                                    ServicesScreen(
                                        isAdmin = isAdmin,
                                        onBackToAdmin = { authVm.setPreviewAsUser(false) },
                                        onLogout = { authVm.logout() }
                                    )
                                }
                                composable("spot/{id}") { entry ->
                                    val id = entry.arguments?.getString("id").orEmpty()
                                    SpotDetailScreen(spotId = id, onBack = { navController.popBackStack() })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private data class Tab(val route: String, val label: String, val icon: ImageVector)
}
