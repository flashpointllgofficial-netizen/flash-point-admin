package com.flashpoint.admin.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun DashboardScreen(onNavigate: (String) -> Unit, vm: DashboardViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    when {
        s.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        s.error != null -> Column(Modifier.fillMaxSize().padding(24.dp),
            Arrangement.Center, Alignment.CenterHorizontally) {
            Text(s.error!!, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Button(vm::load) { Text("Retry") }
        }
        else -> {
            val st = s.stats
            val cards = listOf(
                "Total Orders" to "${st.totalOrders}", "Customers" to "${st.totalCustomers}",
                "Sellers" to "${st.totalSellers}", "Delivery Partners" to "${st.totalDeliveryPartners}",
                "Pending Sellers" to "${st.pendingSellers}", "Pending Delivery" to "${st.pendingDelivery}",
                "COD Revenue" to "%.2f".format(st.codRevenue), "Return Requests" to "${st.returnRequests}",
                "Exchange Requests" to "${st.exchangeRequests}",
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(cards.chunked(2)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (label, value) -> StatCard(label, value, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item { BarChart(listOf("Orders" to st.totalOrders, "Cust." to st.totalCustomers,
                    "Sellers" to st.totalSellers, "Deliv." to st.totalDeliveryPartners)) }
                item {
                    Text("Quick actions", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Sellers" to "sellers", "Delivery" to "delivery", "Orders" to "orders",
                            "Returns" to "returns", "Exchanges" to "exchanges", "Banners" to "banners",
                            "Notify" to "notifications", "Reports" to "reports").forEach { (label, route) ->
                            AssistChip({ onNavigate(route) }, { Text(label) })
                        }
                    }
                }
                item { Text("Recent Activities", style = MaterialTheme.typography.titleMedium) }
                if (s.activities.isEmpty()) item { Text("No recent activity.") }
                items(s.activities) { a ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(a.first)
                            Text(DateFormat.getDateTimeInstance().format(Date(a.second)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { Text("Recent Orders", style = MaterialTheme.typography.titleMedium) }
                if (s.orders.isEmpty()) item { Text("No orders yet.") }
                items(s.orders) { o ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("#${o.id}", style = MaterialTheme.typography.titleSmall)
                            Text("${o.customerName} • %.2f • ${o.status}".format(o.totalAmount))
                            Text(DateFormat.getDateTimeInstance().format(Date(o.createdAt)),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BarChart(data: List<Pair<String, Long>>) {
    val color = MaterialTheme.colorScheme.primary
    val max = (data.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Platform overview", style = MaterialTheme.typography.titleMedium)
            Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp)) {
                val slot = size.width / data.size
                data.forEachIndexed { i, (_, v) ->
                    val h = size.height * v / max
                    drawRoundRect(color, Offset(i * slot + slot * 0.2f, size.height - h),
                        Size(slot * 0.6f, h), CornerRadius(8f))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                data.forEach { Text(it.first, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
