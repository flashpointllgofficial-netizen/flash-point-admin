package com.flashpoint.admin.ui.sellers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashpoint.admin.core.SellerItem
import com.flashpoint.admin.core.toUserMessage
import com.flashpoint.admin.data.AdminRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SellersState(
    val loading: Boolean = true,
    val sellers: List<SellerItem> = emptyList(),
    val query: String = "",
    val error: String? = null,
    val message: String? = null,
) {
    val visible get() = sellers.filter {
        query.isBlank() || it.name.contains(query, true) || it.email.contains(query, true) || it.uid == query
    }
}

class SellersViewModel(private val repo: AdminRepository = AdminRepository()) : ViewModel() {
    private val _state = MutableStateFlow(SellersState())
    val state: StateFlow<SellersState> = _state

    private var job: kotlinx.coroutines.Job? = null

    init { start() }

    fun retry() = start()

    private fun start() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            repo.sellers().catch { e -> _state.update { it.copy(loading = false, error = e.toUserMessage()) } }
                .collect { list -> _state.update { it.copy(loading = false, sellers = list, error = null) } }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun messageShown() = _state.update { it.copy(message = null) }

    fun changeStatus(uid: String, status: String) {
        viewModelScope.launch {
            try {
                repo.setSellerStatus(uid, status)
                _state.update { it.copy(message = "Seller status updated to $status.") }
            } catch (e: Exception) {
                _state.update { it.copy(message = e.toUserMessage()) }
            }
        }
    }
}
