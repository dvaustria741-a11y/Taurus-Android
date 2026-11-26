package com.taurus.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.taurus.android.ui.screens.HomeScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
}

@Composable
fun TaurusApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen()
        }
    }
}
