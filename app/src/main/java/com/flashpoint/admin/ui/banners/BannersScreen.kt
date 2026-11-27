package com.flashpoint.admin.ui.banners

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.flashpoint.admin.core.BannerItem
import java.text.SimpleDateFormat
import java.util.Locale

private val DEST_TYPES = listOf("PRODUCT", "SELLER", "CATEGORY", "URL")
private fun fmt(ms: Long) = if (ms <= 0) "" else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(ms)
private fun parse(t: String): Long? {
    if (t.isBlank()) return 0L
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(t.trim())?.time
    }.getOrNull()
}

@Composable
fun BannersScreen(vm: BannersViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BannerItem?>(null) }
    var deleteFor by remember { mutableStateOf<BannerItem?>(null) }

    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.messageShown() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showForm = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add Banner") },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                s.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                s.error != null -> Text(s.error!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                s.banners.isEmpty() -> Text("No banners yet. Tap Add Banner.", Modifier.align(Alignment.Center))
                else -> LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(s.banners, key = { _, b -> b.id }) { i, b ->
                        Card(Modifier.fillMaxWidth()) {
                            Column {
                                AsyncImage(b.imageUrl, b.title, Modifier.fillMaxWidth().height(140.dp),
                                    contentScale = ContentScale.Crop)
                                Column(Modifier.padding(12.dp)) {
                                    Text(b.title, style = MaterialTheme.typography.titleMedium)
                                    Text("${b.destinationType}: ${b.destinationValue}", style = MaterialTheme.typography.bodySmall)
                                    Text("Start: ${fmt(b.startAt).ifBlank { "-" }}  Expires: ${fmt(b.expiresAt).ifBlank { "-" }}",
                                        style = MaterialTheme.typography.bodySmall)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(b.enabled, { vm.toggle(b.id, it) })
                                        Text(if (b.enabled) " Enabled" else " Disabled")
                                        Spacer(Modifier.weight(1f))
                                        IconButton({ vm.move(s.banners, i, -1) }, enabled = i > 0) {
                                            Icon(Icons.Default.ArrowUpward, "Move up")
                                        }
                                        IconButton({ vm.move(s.banners, i, 1) }, enabled = i < s.banners.lastIndex) {
                                            Icon(Icons.Default.ArrowDownward, "Move down")
                                        }
                                        IconButton({ editing = b; showForm = true }) { Icon(Icons.Default.Edit, "Edit") }
                                        IconButton({ deleteFor = b }) { Icon(Icons.Default.Delete, "Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) BannerForm(
        existing = editing, saving = s.saving, progress = s.progress,
        onSave = { b, uri -> vm.save(b, uri) { showForm = false } },
        onDismiss = { if (s.saving) vm.cancelUpload(); showForm = false },
    )

    deleteFor?.let { b ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Delete banner") },
            text = { Text("Delete \"${b.title}\"? This cannot be undone.") },
            confirmButton = { TextButton({ vm.delete(b.id); deleteFor = null }) { Text("Delete") } },
            dismissButton = { TextButton({ deleteFor = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BannerForm(
    existing: BannerItem?, saving: Boolean, progress: Int,
    onSave: (BannerItem, Uri?) -> Unit, onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var type by remember { mutableStateOf(existing?.destinationType ?: "URL") }
    var value by remember { mutableStateOf(existing?.destinationValue.orEmpty()) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var start by remember { mutableStateOf(fmt(existing?.startAt ?: 0)) }
    var end by remember { mutableStateOf(fmt(existing?.expiresAt ?: 0)) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // System document picker: shows Gallery, Google Photos, Files, Downloads and other providers.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { picked = uri; error = null } // null = user cancelled: do nothing
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(if (existing == null) "Add Banner" else "Edit Banner") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val preview: Any? = picked ?: existing?.imageUrl?.takeIf { it.isNotBlank() }
                if (preview != null) AsyncImage(preview, "Banner preview",
                    Modifier.fillMaxWidth().height(130.dp), contentScale = ContentScale.Crop)
                OutlinedButton({ picker.launch(arrayOf("image/*")) }, enabled = !saving) {
                    Text(if (preview == null) "Select Image" else "Replace Image")
                }
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                Text("Click destination", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DEST_TYPES.forEach { t -> FilterChip(type == t, { type = t }, { Text(t) }) }
                }
                OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(when (type) {
                        "PRODUCT" -> "sellerUid/productId"
                        "SELLER" -> "Seller UID"
                        "CATEGORY" -> "Category name"
                        else -> "Website URL (https://...)"
                    }) })
                OutlinedTextField(start, { start = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Start date (yyyy-MM-dd, optional)") })
                OutlinedTextField(end, { end = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Expiry date (yyyy-MM-dd, optional)") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(enabled, { enabled = it }); Text(if (enabled) " Enabled" else " Disabled")
                }
                if (saving) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                val s = parse(start); val e = parse(end)
                error = when {
                    title.isBlank() || value.isBlank() -> "Please complete all required fields."
                    picked == null && existing?.imageUrl.isNullOrBlank() -> "Please select a banner image."
                    s == null || e == null -> "Use date format yyyy-MM-dd."
                    e != 0L && s != 0L && e < s -> "Expiry must be after the start date."
                    type == "URL" && !value.trim().startsWith("http") -> "Enter a full website URL starting with http."
                    else -> null
                }
                if (error == null) onSave(
                    (existing ?: BannerItem()).copy(title = title.trim(), destinationType = type,
                        destinationValue = value.trim(), enabled = enabled, startAt = s ?: 0, expiresAt = e ?: 0),
                    picked,
                )
            }) { Text(if (saving) "Uploading…" else "Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text(if (saving) "Cancel upload" else "Close") } },
    )
}
