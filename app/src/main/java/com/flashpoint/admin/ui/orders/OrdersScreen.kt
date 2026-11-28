package com.flashpoint.admin.ui.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashpoint.admin.core.OrderItem
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.platform.LocalUriHandler

@Composable
fun OrdersScreen(vm: OrdersViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val uri = LocalUriHandler.current
    var assignFor by remember { mutableStateOf<OrderItem?>(null) }
    var cancelFor by remember { mutableStateOf<OrderItem?>(null) }

    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.messageShown() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            Text("COD collected (delivered): %.2f".format(s.codDelivered),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(s.query, vm::setQuery, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Search order ID or customer") },
                leadingIcon = { Icon(Icons.Default.Search, null) })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                item { FilterChip(s.filter == null, { vm.setFilter(null) }, { Text("ALL") }) }
                items(ORDER_STATUSES) { st ->
                    FilterChip(s.filter == st, { vm.setFilter(if (s.filter == st) null else st) }, { Text(st) })
                }
            }
            when {
                s.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                s.error != null -> Column { Text(s.error!!, color = MaterialTheme.colorScheme.error); Button({ vm.retry() }) { Text("Retry") } }
                s.visible.isEmpty() -> Text("No orders found.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.visible, key = { it.id }) { o ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("#${o.id}", style = MaterialTheme.typography.titleMedium)
                                Text("${o.customerName} • ${o.phone}")
                                Text("Total (COD): %.2f".format(o.totalAmount))
                                Text(o.address, style = MaterialTheme.typography.bodySmall)
                                var open by remember { mutableStateOf(false) }
                                TextButton({ open = !open }) { Text(if (open) "Hide details" else "Details") }
                                if (open) {
                                    o.items.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                                    Text("Payment: COD • ${o.paymentStatus.ifBlank { "pending" }}", style = MaterialTheme.typography.bodySmall)
                                    Text("Placed: ${DateFormat.getDateTimeInstance().format(Date(o.createdAt))}", style = MaterialTheme.typography.bodySmall)
                                    Text("Delivered: ${if (o.deliveredAt > 0) DateFormat.getDateTimeInstance().format(Date(o.deliveredAt)) else "-"}", style = MaterialTheme.typography.bodySmall)
                                    Text("Customer UID: ${o.customerUid}", style = MaterialTheme.typography.labelSmall)
                                    Text("Seller UID: ${o.sellerUid}", style = MaterialTheme.typography.labelSmall)
                                }
                                AssistChip({}, { Text(o.status) })
                                val partner = s.partners.firstOrNull { it.uid == o.deliveryUid }
                                Text(
                                    if (o.deliveryUid.isBlank()) "Delivery: not assigned"
                                    else "Delivery: ${partner?.name ?: o.deliveryUid}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (o.locationLink.isNotBlank()) {
                                    TextButton({ runCatching { uri.openUri(o.locationLink) } }) { Text("Open live location") }
                                }
                                if (o.status != "DELIVERED" && o.status != "CANCELLED") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button({ assignFor = o }) {
                                            Text(if (o.deliveryUid.isBlank()) "Assign" else "Reassign")
                                        }
                                        OutlinedButton({ cancelFor = o }) { Text("Cancel order") }
                                    }
                                }
                            }
                        }
                    }
                    if (s.canLoadMore) item { OutlinedButton({ vm.loadMore() }, Modifier.fillMaxWidth()) { Text("Load more orders") } }
                }
            }
        }
    }

    assignFor?.let { order ->
        AlertDialog(
            onDismissRequest = { assignFor = null },
            title = { Text("Assign delivery partner") },
            text = {
                if (s.partners.isEmpty()) Text("No approved delivery partners yet.")
                else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(s.partners, key = { it.uid }) { p ->
                        TextButton({ vm.assign(order.id, p); assignFor = null }, Modifier.fillMaxWidth()) {
                            Text("${p.name.ifBlank { p.email }} • ${p.phone}")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ assignFor = null }) { Text("Close") } },
        )
    }

    cancelFor?.let { order ->
        AlertDialog(
            onDismissRequest = { cancelFor = null },
            title = { Text("Cancel order") },
            text = { Text("Cancel order #${order.id}? This cannot be undone.") },
            confirmButton = { TextButton({ vm.cancel(order.id); cancelFor = null }) { Text("Cancel order") } },
            dismissButton = { TextButton({ cancelFor = null }) { Text("Keep") } },
        )
    }
}
