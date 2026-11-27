package com.flashpoint.admin.ui.people

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
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flashpoint.admin.core.PersonItem

@Composable
fun PeopleScreen(mode: PeopleMode) {
    val vm: PeopleViewModel = viewModel(
        key = mode.name,
        factory = viewModelFactory { initializer { PeopleViewModel(mode) } },
    )
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<Pair<PersonItem, String>?>(null) }

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
                s.visible.isEmpty() -> Text("Nothing found.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.visible, key = { it.uid }) { p ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(p.name.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleMedium)
                                Text("${p.email} • ${p.phone}", style = MaterialTheme.typography.bodySmall)
                                Text(p.detail, style = MaterialTheme.typography.bodySmall)
                                Text("UID: ${p.uid}", style = MaterialTheme.typography.labelSmall)
                                if (mode == PeopleMode.DELIVERY) {
                                    AssistChip({}, { Text(p.status) })
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        when (p.status) {
                                            "PENDING" -> {
                                                Button({ pending = p to "APPROVED" }) { Text("Approve") }
                                                OutlinedButton({ pending = p to "REJECTED" }) { Text("Reject") }
                                            }
                                            "APPROVED" -> OutlinedButton({ pending = p to "SUSPENDED" }) { Text("Suspend") }
                                            else -> Button({ pending = p to "APPROVED" }) { Text("Reactivate") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pending?.let { (p, status) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Confirm") },
            text = { Text("Set ${p.name.ifBlank { p.email }} to $status?") },
            confirmButton = { TextButton({ vm.changeStatus(p.uid, status); pending = null }) { Text("Confirm") } },
            dismissButton = { TextButton({ pending = null }) { Text("Cancel") } },
        )
    }
}
