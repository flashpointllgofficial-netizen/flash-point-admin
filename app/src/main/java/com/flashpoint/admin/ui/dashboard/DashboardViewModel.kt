package com.flashpoint.admin.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashpoint.admin.core.OrderSummary
import com.flashpoint.admin.core.Stats
import com.flashpoint.admin.core.toUserMessage
import com.flashpoint.admin.data.AdminRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardState(
    val loading: Boolean = true,
    val stats: Stats = Stats(),
    val orders: List<OrderSummary> = emptyList(),
    val error: String? = null,
)

class DashboardViewModel(private val repo: AdminRepository = AdminRepository()) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state
    private var job: Job? = null

    init { load() }

    fun load() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                _state.update { it.copy(orders = repo.recentOrders()) }
                repo.stats().collect { s -> _state.update { it.copy(loading = false, stats = s) } }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }
}
