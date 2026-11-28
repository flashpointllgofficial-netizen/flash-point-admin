package com.flashpoint.admin.ui.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flashpoint.admin.core.toUserMessage
import com.flashpoint.admin.data.Detail
import com.flashpoint.admin.data.DetailRepository
import java.text.DateFormat
import java.util.Date

enum class DetailKind { SELLER, CUSTOMER, DELIVERY }

private fun day(ms: Long) = if (ms <= 0) "-" else DateFormat.getDateInstance().format(Date(ms))

@Composable
fun DetailDialog(kind: DetailKind, uid: String, title: String, onClose: () -> Unit) {
    val repo = remember { DetailRepository() }
    var result by remember { mutableStateOf<Result<Detail>?>(null) }
    LaunchedEffect(uid) { result = runCatching { repo.load(kind, uid) } }
    val r = result

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    r == null -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                    r.isFailure -> Text(r.exceptionOrNull()?.toUserMessage().orEmpty(), color = MaterialTheme.colorScheme.error)
                    else -> {
                        val d = r.getOrThrow()
                        Section("Profile")
                        d.profile.forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodySmall) }
                        if (kind == DetailKind.SELLER) {
                            Section("Products (${d.products.size})")
                            if (d.products.isEmpty()) Text("No products.", style = MaterialTheme.typography.bodySmall)
                            d.products.forEach {
                                Text("• ${it.name} — %.2f • stock ${it.stock} • ${if (it.available) "available" else "unavailable"}".format(it.price),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        val done = d.orders.count { it.status == "DELIVERED" }
                        Section(when (kind) {
                            DetailKind.DELIVERY -> "Deliveries (${d.orders.size}, completed $done)"
                            else -> "Order history (${d.orders.size})"
                        })
                        if (d.orders.isEmpty()) Text("No orders.", style = MaterialTheme.typography.bodySmall)
                        d.orders.forEach {
                            Text("#${it.id} • ${it.status} • %.2f • ${day(if (it.deliveredAt > 0) it.deliveredAt else it.createdAt)}".format(it.totalAmount),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (kind == DetailKind.CUSTOMER) {
                            Section("Returns & exchanges (${d.requests.size})")
                            if (d.requests.isEmpty()) Text("None.", style = MaterialTheme.typography.bodySmall)
                            d.requests.forEach {
                                Text("• ${it.type.lowercase()}: ${it.productName} — ${it.status} • ${day(it.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Close") } },
    )
}

@Composable
private fun Section(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}
