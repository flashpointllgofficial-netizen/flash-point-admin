package com.flashpoint.admin.ui.requests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flashpoint.admin.core.RequestItem
import com.google.firebase.database.ServerValue
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun day(ms: Long) = if (ms <= 0) "-" else DateFormat.getDateInstance().format(Date(ms))
private fun time(ms: Long) = if (ms <= 0) "-" else DateFormat.getDateTimeInstance().format(Date(ms))
private fun parseDate(t: String): Long? {
    if (t.isBlank()) return 0L
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(t.trim())?.time
    }.getOrNull()
}

@Composable
fun RequestsScreen(mode: RequestMode) {
    val vm: RequestsViewModel = viewModel(
        key = mode.name,
        factory = viewModelFactory { initializer { RequestsViewModel(mode) } },
    )
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var editFor by remember { mutableStateOf<RequestItem?>(null) }
    val statuses = if (mode == RequestMode.RETURN) RETURN_STATUSES else EXCHANGE_STATUSES
    val approved = if (mode == RequestMode.RETURN) "RETURN_APPROVED" else "EXCHANGE_APPROVED"

    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.messageShown() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(s.query, vm::setQuery, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Search order, product, customer or UID") },
                leadingIcon = { Icon(Icons.Default.Search, null) })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                item { FilterChip(s.filter == null, { vm.setFilter(null) }, { Text("ALL") }) }
                items(statuses) { st ->
                    FilterChip(s.filter == st, { vm.setFilter(if (s.filter == st) null else st) }, { Text(st) })
                }
            }
            when {
                s.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                s.error != null -> Text(s.error!!, color = MaterialTheme.colorScheme.error)
                s.visible.isEmpty() -> Text("No ${mode.title.lowercase()} found.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(s.visible, key = { it.id }) { r ->
                        var showHistory by remember { mutableStateOf(false) }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${r.productName} × ${r.quantity}", style = MaterialTheme.typography.titleMedium)
                                Text("Product ID: ${r.productId} • Order: #${r.orderId}", style = MaterialTheme.typography.bodySmall)
                                Text("Customer: ${s.customers[r.customerUid].orEmpty().ifBlank { "-" }} (${r.customerUid})",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("Seller: ${s.sellers[r.sellerUid].orEmpty().ifBlank { "-" }} (${r.sellerUid})",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("Original variant: ${r.originalVariant.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                                if (mode == RequestMode.EXCHANGE) {
                                    Text("Requested variant: ${r.requestedVariant.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                                    if (r.replacementProductId.isNotBlank() || r.replacementVariant.isNotBlank())
                                        Text("Replacement: ${r.replacementProductId} ${r.replacementVariant}", style = MaterialTheme.typography.bodySmall)
                                } else if (r.refundAmount > 0) {
                                    Text("Refund: %.2f • ${r.refundMethod} • ${r.refundReference} • ${day(r.refundDate)}".format(r.refundAmount),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Text("Requested: ${day(r.createdAt)} • Delivered: ${day(r.deliveredAt)}", style = MaterialTheme.typography.bodySmall)
                                Text("Reason: ${r.reason}")
                                AssistChip({}, { Text(r.status) })
                                if (r.adminNote.isNotBlank()) Text("Admin note: ${r.adminNote}", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (r.status.endsWith("REQUESTED")) {
                                        Button({ vm.update(r, approved, "Request approved.", emptyMap()) }, enabled = !s.saving) { Text("Approve") }
                                    }
                                    OutlinedButton({ editFor = r }) { Text("Update") }
                                    TextButton({ showHistory = !showHistory }) { Text(if (showHistory) "Hide history" else "History") }
                                }
                                if (showHistory) {
                                    if (r.history.isEmpty()) Text("No status history yet.", style = MaterialTheme.typography.bodySmall)
                                    r.history.forEach { h ->
                                        Text("${time(h.updatedAt)} — ${h.status}${if (h.message.isNotBlank()) ": ${h.message}" else ""}",
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editFor?.let { r ->
        UpdateDialog(r, mode, statuses, s.saving,
            onSubmit = { st, msg, extra -> vm.update(r, st, msg, extra) { editFor = null } },
            onDismiss = { editFor = null })
    }
}

@Composable
private fun UpdateDialog(
    r: RequestItem, mode: RequestMode, statuses: List<String>, saving: Boolean,
    onSubmit: (String, String, Map<String, Any>) -> Unit, onDismiss: () -> Unit,
) {
    var status by remember { mutableStateOf(r.status) }
    var msg by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(if (r.refundAmount > 0) r.refundAmount.toString() else "") }
    var method by remember { mutableStateOf(r.refundMethod) }
    var ref by remember { mutableStateOf(r.refundReference) }
    var date by remember { mutableStateOf("") }
    var repProduct by remember { mutableStateOf(r.replacementProductId) }
    var repVariant by remember { mutableStateOf(r.replacementVariant) }
    var dispatch by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val refundStage = mode == RequestMode.RETURN && (status == "REFUND_PROCESSING" || status == "REFUNDED")
    val replaceStage = mode == RequestMode.EXCHANGE &&
        status in listOf("REPLACEMENT_PROCESSING", "REPLACEMENT_DISPATCHED", "REPLACEMENT_DELIVERED")

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Update ${mode.title.dropLast(1).lowercase()} request") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                statuses.forEach { st ->
                    Row(Modifier.fillMaxWidth().clickable { status = st }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(status == st, { status = st })
                        Text(st, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(msg, { msg = it }, Modifier.fillMaxWidth(), minLines = 2,
                    label = { Text("Message to customer") })
                if (refundStage) {
                    OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Refund amount") })
                    OutlinedTextField(method, { method = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Refund method") })
                    OutlinedTextField(ref, { ref = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Transaction reference") })
                    OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Refund date (yyyy-MM-dd, default today)") })
                    Text("Mark REFUNDED only after the money has actually been sent.", style = MaterialTheme.typography.labelSmall)
                }
                if (replaceStage) {
                    OutlinedTextField(repProduct, { repProduct = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Replacement product ID") })
                    OutlinedTextField(repVariant, { repVariant = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Replacement variant") })
                    OutlinedTextField(dispatch, { dispatch = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Dispatch information") })
                    OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Completion date (yyyy-MM-dd, default today)") })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                val d = parseDate(date)
                val needsMsg = status.endsWith("REJECTED") || status.endsWith("FAILED")
                error = when {
                    status == r.status && msg.isBlank() -> "Choose a new status or add a message."
                    needsMsg && msg.isBlank() -> "Please add a message explaining this decision."
                    status == "REFUNDED" && ((amount.toDoubleOrNull() ?: 0.0) <= 0.0 || method.isBlank() || ref.isBlank()) ->
                        "Enter the refund amount, method and transaction reference first."
                    d == null -> "Use date format yyyy-MM-dd."
                    else -> null
                }
                if (error == null) {
                    val ms = d ?: 0L
                    val extra = mutableMapOf<String, Any>()
                    if (refundStage) {
                        amount.toDoubleOrNull()?.let { extra["refundAmount"] = it }
                        if (method.isNotBlank()) extra["refundMethod"] = method.trim()
                        if (ref.isNotBlank()) extra["refundReference"] = ref.trim()
                        extra["refundStatus"] = if (status == "REFUNDED") "COMPLETED" else "PROCESSING"
                        if (status == "REFUNDED") extra["refundDate"] = if (ms > 0) ms else ServerValue.TIMESTAMP
                    }
                    if (replaceStage) {
                        if (repProduct.isNotBlank()) extra["replacementProductId"] = repProduct.trim()
                        if (repVariant.isNotBlank()) extra["replacementVariant"] = repVariant.trim()
                        if (dispatch.isNotBlank()) extra["dispatchInfo"] = dispatch.trim()
                        if (status == "REPLACEMENT_DELIVERED") extra["completedAt"] = if (ms > 0) ms else ServerValue.TIMESTAMP
                    }
                    onSubmit(status, msg.trim(), extra)
                }
            }) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("Close") } },
    )
}
