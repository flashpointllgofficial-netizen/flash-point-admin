package com.flashpoint.admin.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashpoint.admin.core.OrderItem
import com.flashpoint.admin.core.PersonItem
import com.flashpoint.admin.core.toUserMessage
import com.flashpoint.admin.data.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val ORDER_STATUSES = listOf(
    "PLACED", "ACCEPTED", "READY_FOR_PICKUP", "PICKED_UP",
    "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED",
)

data class OrdersState(
    val loading: Boolean = true,
    val orders: List<OrderItem> = emptyList(),
    val partners: List<PersonItem> = emptyList(),
    val query: String = "",
    val filter: String? = null,
    val error: String? = null,
    val message: String? = null,
) {
    val visible: List<OrderItem>
        get() = orders.filter {
            (filter == null || it.status == filter) &&
                (query.isBlank() || it.id.contains(query, true) || it.customerName.contains(query, true))
        }
    val codDelivered: Double get() = orders.filter { it.status == "DELIVERED" }.sumOf { it.totalAmount }
}

class OrdersViewModel(private val repo: AdminRepository = AdminRepository()) : ViewModel() {
    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state

    init {
        viewModelScope.launch {
            repo.orders().catch { e -> _state.update { it.copy(loading = false, error = e.toUserMessage()) } }
                .collect { list -> _state.update { it.copy(loading = false, orders = list, error = null) } }
        }
        viewModelScope.launch {
            repo.deliveryPartners().catch { }
                .collect { list -> _state.update { it.copy(partners = list.filter { p -> p.status == "APPROVED" }) } }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setFilter(f: String?) = _state.update { it.copy(filter = f) }
    fun messageShown() = _state.update { it.copy(message = null) }

    fun assign(orderId: String, partnerUid: String) = run("Delivery partner assigned.") {
        repo.assignDelivery(orderId, partnerUid)
    }

    fun cancel(orderId: String) = run("Order cancelled.") { repo.cancelOrder(orderId) }

    private fun run(ok: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(message = ok) }
            } catch (e: Exception) {
                _state.update { it.copy(message = e.toUserMessage()) }
            }
        }
    }
}
