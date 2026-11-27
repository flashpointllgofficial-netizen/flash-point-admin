package com.flashpoint.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.flashpoint.admin.ui.dashboard.DashboardScreen
import com.flashpoint.admin.ui.sellers.SellersScreen
import com.flashpoint.admin.ui.theme.Logo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHome(onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route ?: "dashboard"

    fun go(r: String) {
        scope.launch { drawer.close() }
        nav.navigate(r) { popUpTo("dashboard"); launchSingleTop = true }
    }

    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet {
            Logo(Modifier.padding(20.dp).height(64.dp))
            NavigationDrawerItem({ Text("Dashboard") }, route == "dashboard", { go("dashboard") },
                icon = { Icon(Icons.Default.Dashboard, null) })
            NavigationDrawerItem({ Text("Sellers") }, route == "sellers", { go("sellers") },
                icon = { Icon(Icons.Default.Storefront, null) })
            NavigationDrawerItem({ Text("Sign out") }, false, { confirmLogout = true },
                icon = { Icon(Icons.Default.Logout, null) })
        }
    }) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(if (route == "sellers") "Sellers" else "Dashboard") },
                navigationIcon = {
                    IconButton({ scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "Open menu") }
                })
        }) { pad ->
            NavHost(nav, "dashboard", Modifier.padding(pad)) {
                composable("dashboard") { DashboardScreen(onOpenSellers = { go("sellers") }) }
                composable("sellers") { SellersScreen() }
            }
        }
    }

    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Sign out") }, text = { Text("Do you want to sign out?") },
        confirmButton = { TextButton({ confirmLogout = false; onSignOut() }) { Text("Sign out") } },
        dismissButton = { TextButton({ confirmLogout = false }) { Text("Cancel") } },
    )
}
