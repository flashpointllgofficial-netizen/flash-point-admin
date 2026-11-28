package com.flashpoint.admin.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashpoint.admin.core.RequestItem
import com.flashpoint.admin.core.toUserMessage
import com.flashpoint.admin.data.RequestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RequestMode(val type: String, val title: String) {
    RETURN("RETURN", "Returns"),
    EXCHANGE("EXCHANGE", "Exchanges"),
}

val RETURN_STATUSES = listOf(
    "RETURN_REQUESTED", "RETURN_APPROVED", "PICKUP_PENDING", "PRODUCT_PICKED_UP", "PRODUCT_RECEIVED",
    "REFUND_PROCESSING", "REFUNDED", "RETURN_FAILED", "RETURN_REJECTED",
)
val EXCHANGE_STATUSES = listOf(
    "EXCHANGE_REQUESTED", "EXCHANGE_APPROVED", "PICKUP_PENDING", "ORIGINAL_PRODUCT_PICKED_UP",
    "ORIGINAL_PRODUCT_RECEIVED", "REPLACEMENT_PROCESSING", "REPLACEMENT_DISPATCHED",
    "REPLACEMENT_DELIVERED", "EXCHANGE_FAILED", "EXCHANGE_REJECTED",
)

data class RequestsState(
    val loading: Boolean = true,
    val items: List<RequestItem> = emptyList(),
    val customers: Map<String, String> = emptyMap(),
    val sellers: Map<String, String> = emptyMap(),
    val query: String = "",
    val filter: String? = null,
    val error: String? = null,
    val message: String? = null,
    val saving: Boolean = false,
) {
    val visible: List<RequestItem>
        get() = items.filter { r ->
            (filter == null || r.status == filter) && (query.isBlank() ||
                r.orderId.contains(query, true) || r.productName.contains(query, true) ||
                r.customerUid == query || (customers[r.customerUid] ?: "").contains(query, true))
        }
}

class RequestsViewModel(
    private val mode: RequestMode,
    private val repo: RequestRepository = RequestRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(RequestsState())
    val state: StateFlow<RequestsState> = _state

    init {
        viewModelScope.launch {
            repo.requests().catch { e -> _state.update { it.copy(loading = false, error = e.toUserMessage()) } }
                .collect { list ->
                    _state.update { it.copy(loading = false, error = null,
                        items = list.filter { r -> r.type == mode.type }) }
                }
        }
        viewModelScope.launch {
            runCatching {
                val c = repo.names("customers"); val s = repo.names("sellers")
                _state.update { it.copy(customers = c, sellers = s) }
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setFilter(f: String?) = _state.update { it.copy(filter = f) }
    fun messageShown() = _state.update { it.copy(message = null) }

    fun update(r: RequestItem, status: String, message: String, extra: Map<String, Any>, onDone: () -> Unit = {}) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                repo.update(r, status, message, extra)
                _state.update { it.copy(message = "Request updated. Customer notified.") }
                onDone()
            } catch (e: Exception) {
                _state.update { it.copy(message = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}
