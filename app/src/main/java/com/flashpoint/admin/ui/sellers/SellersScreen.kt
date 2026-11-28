package com.flashpoint.admin.ui.sellers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.flashpoint.admin.core.SellerItem
import com.flashpoint.admin.ui.details.DetailDialog
import com.flashpoint.admin.ui.details.DetailKind

@Composable
fun SellersScreen(vm: SellersViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<Pair<SellerItem, String>?>(null) }
    var detail by remember { mutableStateOf<SellerItem?>(null) }

    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.messageShown() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(s.query, vm::setQuery, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Search name, email or UID") },
                leadingIcon = { Icon(Icons.Default.Search, null) })
            Spacer(Modifier.height(8.dp))
            when {
                s.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                s.error != null -> Text(s.error!!, color = MaterialTheme.colorScheme.error)
                s.visible.isEmpty() -> Text("No sellers found.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.visible, key = { it.uid }) { seller ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(seller.name.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleMedium)
                                Text("${seller.email} • ${seller.phone}", style = MaterialTheme.typography.bodySmall)
                                Text("Category: ${seller.category.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                                Text("UID: ${seller.uid}", style = MaterialTheme.typography.labelSmall)
                                AssistChip({}, { Text(seller.status) })
                                TextButton({ detail = seller }) { Text("View details, products & orders") }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    when (seller.status) {
                                        "PENDING" -> {
                                            Button({ pending = seller to "APPROVED" }) { Text("Approve") }
                                            OutlinedButton({ pending = seller to "REJECTED" }) { Text("Reject") }
                                        }
                                        "APPROVED" -> OutlinedButton({ pending = seller to "SUSPENDED" }) { Text("Suspend") }
                                        else -> Button({ pending = seller to "APPROVED" }) { Text("Reactivate") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { DetailDialog(DetailKind.SELLER, it.uid, it.name.ifBlank { it.email }) { detail = null } }

    pending?.let { (seller, status) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Confirm") },
            text = { Text("Set ${seller.name.ifBlank { seller.email }} to $status?") },
            confirmButton = { TextButton({ vm.changeStatus(seller.uid, status); pending = null }) { Text("Confirm") } },
            dismissButton = { TextButton({ pending = null }) { Text("Cancel") } },
        )
    }
}
