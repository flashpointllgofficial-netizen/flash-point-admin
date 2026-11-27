package com.flashpoint.admin.ui.notifications

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

private val AUDIENCES = listOf(
    "SPECIFIC" to "Specific user", "CUSTOMERS" to "All customers", "SELLERS" to "All sellers",
    "DELIVERY" to "All delivery", "EVERYONE" to "Everyone",
)
private val ROLES = listOf("customers" to "Customer", "sellers" to "Seller", "deliveryPartners" to "Delivery partner")

@Composable
fun NotificationsScreen(vm: NotificationsViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.messageShown() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Send") })
                Tab(tab == 1, { tab = 1 }, text = { Text("History") })
            }
            if (tab == 0) SendForm(s, vm) else History(s)
        }
    }
}

@Composable
private fun SendForm(s: NotifState, vm: NotificationsViewModel) {
    var audience by remember { mutableStateOf("CUSTOMERS") }
    var role by remember { mutableStateOf("customers") }
    var uid by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var button by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) image = uri // null = cancelled, ignore
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Audience", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AUDIENCES.forEach { (k, label) -> FilterChip(audience == k, { audience = k }, { Text(label) }) }
        }
        if (audience == "SPECIFIC") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ROLES.forEach { (k, label) -> FilterChip(role == k, { role = k }, { Text(label) }) }
            }
            OutlinedTextField(uid, { uid = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("User UID *") })
        }
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Title *") })
        OutlinedTextField(message, { message = it }, Modifier.fillMaxWidth(), minLines = 3,
            label = { Text("Description *") })

        Text("Optional", style = MaterialTheme.typography.labelLarge)
        image?.let {
            AsyncImage(it, "Selected image", Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Crop)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ picker.launch(arrayOf("image/*")) }, enabled = !s.sending) {
                Text(if (image == null) "Add image" else "Replace image")
            }
            if (image != null) TextButton({ image = null }, enabled = !s.sending) { Text("Remove") }
        }
        OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Redirect link (https://...)") })
        OutlinedTextField(button, { button = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Button text (default: Open)") }, enabled = link.isNotBlank())

        if (s.sending) LinearProgressIndicator(progress = { s.progress / 100f }, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !s.sending, onClick = {
                error = when {
                    title.isBlank() || message.isBlank() -> "Title and description are required."
                    audience == "SPECIFIC" && uid.isBlank() -> "Please enter the user UID."
                    link.isNotBlank() && !link.trim().startsWith("http") -> "Link must start with http:// or https://"
                    link.isBlank() && button.isNotBlank() -> "Add a redirect link for the button."
                    else -> null
                }
                if (error == null) confirm = true
            }) { Text(if (s.sending) "Sending…" else "Send notification") }
            if (s.sending) OutlinedButton({ vm.cancel() }) { Text("Cancel") }
        }
    }

    if (confirm) {
        val label = AUDIENCES.first { it.first == audience }.second
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Send notification") },
            text = { Text("Send \"${title.trim()}\" to: $label${if (audience == "SPECIFIC") " ($uid)" else ""}?") },
            confirmButton = {
                TextButton({
                    confirm = false
                    vm.send(title.trim(), message.trim(), image, link.trim(),
                        if (link.isBlank()) "" else button.trim().ifBlank { "Open" },
                        audience, role, uid.trim()) {
                        title = ""; message = ""; link = ""; button = ""; image = null; uid = ""
                    }
                }) { Text("Send") }
            },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun History(s: NotifState) {
    when {
        s.historyLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        s.historyError != null -> Text(s.historyError, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        s.history.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No notifications sent yet.") }
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(s.history, key = { it.id }) { n ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(n.title, style = MaterialTheme.typography.titleMedium)
                        Text(n.message)
                        val to = AUDIENCES.firstOrNull { it.first == n.audience }?.second ?: n.audience
                        Text("To: $to${if (n.targetUid.isNotBlank()) " (${n.targetUid})" else ""} • ${n.recipients} recipient(s)",
                            style = MaterialTheme.typography.bodySmall)
                        if (n.linkUrl.isNotBlank()) Text("Button: ${n.buttonText} → ${n.linkUrl}", style = MaterialTheme.typography.bodySmall)
                        Text(DateFormat.getDateTimeInstance().format(Date(n.createdAt)), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
