package com.flashpoint.admin.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashpoint.admin.core.toUserMessage
import com.flashpoint.admin.data.AppSettings
import com.flashpoint.admin.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val error: String? = null,
    val message: String? = null,
)

class SettingsViewModel(private val repo: SettingsRepository = SettingsRepository()) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val s = repo.load()
                _state.update { it.copy(loading = false, settings = s) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun save(a: AppSettings) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                repo.save(a)
                _state.update { it.copy(saving = false, settings = a, message = "Settings saved.") }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, message = e.toUserMessage()) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.messageShown() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        when {
            s.loading -> Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            s.error != null -> Column(Modifier.padding(pad).padding(16.dp)) {
                Text(s.error!!, color = MaterialTheme.colorScheme.error)
                Button({ vm.load() }) { Text("Retry") }
            }
            else -> {
                var charge by remember(s.settings) { mutableStateOf(s.settings.deliveryCharge.toString()) }
                var earning by remember(s.settings) { mutableStateOf(s.settings.earningPerDelivery.toString()) }
                var free by remember(s.settings) { mutableStateOf(s.settings.freeDeliveryAbove.toString()) }
                var error by remember { mutableStateOf<String?>(null) }
                val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Delivery & earnings", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(charge, { charge = it }, Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = num, label = { Text("Delivery charge per order (customer pays)") })
                    OutlinedTextField(free, { free = it }, Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = num, label = { Text("Free delivery above order amount (0 = off)") })
                    OutlinedTextField(earning, { earning = it }, Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = num, label = { Text("Earning per completed delivery (partner)") })
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(enabled = !s.saving, onClick = {
                        val c = charge.toDoubleOrNull(); val e = earning.toDoubleOrNull(); val f = free.toDoubleOrNull()
                        if (c == null || e == null || f == null || c < 0 || e < 0 || f < 0) {
                            error = "Enter valid amounts (0 or more)."
                        } else {
                            error = null
                            vm.save(AppSettings(c, e, f))
                        }
                    }) { Text(if (s.saving) "Saving…" else "Save settings") }
                    Text("These values are read by the Customer app (checkout) and the Delivery app (earnings).",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
