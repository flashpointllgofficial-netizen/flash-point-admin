package com.flashpoint.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.flashpoint.admin.ui.banners.BannersScreen
import com.flashpoint.admin.ui.dashboard.DashboardScreen
import com.flashpoint.admin.ui.notifications.NotificationsScreen
import com.flashpoint.admin.ui.orders.OrdersScreen
import com.flashpoint.admin.ui.people.PeopleMode
import com.flashpoint.admin.ui.people.PeopleScreen
import com.flashpoint.admin.ui.sellers.SellersScreen
import com.flashpoint.admin.ui.theme.Logo
import kotlinx.coroutines.launch

private data class Dest(val route: String, val title: String, val icon: ImageVector)

private val DESTS = listOf(
    Dest("dashboard", "Dashboard", Icons.Default.Dashboard),
    Dest("sellers", "Sellers", Icons.Default.Storefront),
    Dest("delivery", "Delivery Partners", Icons.Default.LocalShipping),
    Dest("customers", "Customers", Icons.Default.People),
    Dest("orders", "Orders", Icons.Default.ShoppingCart),
    Dest("banners", "Banners", Icons.Default.Image),
    Dest("notifications", "Notifications", Icons.Default.Notifications),
)

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
            DESTS.forEach { d ->
                NavigationDrawerItem({ Text(d.title) }, route == d.route, { go(d.route) },
                    icon = { Icon(d.icon, null) })
            }
            NavigationDrawerItem({ Text("Sign out") }, false, { confirmLogout = true },
                icon = { Icon(Icons.Default.Logout, null) })
        }
    }) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(DESTS.firstOrNull { it.route == route }?.title ?: "Dashboard") },
                navigationIcon = {
                    IconButton({ scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "Open menu") }
                })
        }) { pad ->
            NavHost(nav, "dashboard", Modifier.padding(pad)) {
                composable("dashboard") { DashboardScreen(onOpenSellers = { go("sellers") }) }
                composable("sellers") { SellersScreen() }
                composable("delivery") { PeopleScreen(PeopleMode.DELIVERY) }
                composable("customers") { PeopleScreen(PeopleMode.CUSTOMERS) }
                composable("orders") { OrdersScreen() }
                composable("banners") { BannersScreen() }
                composable("notifications") { NotificationsScreen() }
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
