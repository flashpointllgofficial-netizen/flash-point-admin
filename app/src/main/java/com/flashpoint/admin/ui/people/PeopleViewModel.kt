package com.flashpoint.admin.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashpoint.admin.core.PersonItem
import com.flashpoint.admin.core.toUserMessage
import com.flashpoint.admin.data.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PeopleMode { DELIVERY, CUSTOMERS }

data class PeopleState(
    val loading: Boolean = true,
    val people: List<PersonItem> = emptyList(),
    val query: String = "",
    val error: String? = null,
    val message: String? = null,
) {
    val visible: List<PersonItem>
        get() = people.filter {
            query.isBlank() || it.name.contains(query, true) ||
                it.email.contains(query, true) || it.uid == query
        }
}

class PeopleViewModel(
    private val mode: PeopleMode,
    private val repo: AdminRepository = AdminRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(PeopleState())
    val state: StateFlow<PeopleState> = _state

    private var job: kotlinx.coroutines.Job? = null

    init { start() }

    fun retry() = start()

    private fun start() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            val flow = if (mode == PeopleMode.DELIVERY) repo.deliveryPartners() else repo.customers()
            flow.catch { e -> _state.update { it.copy(loading = false, error = e.toUserMessage()) } }
                .collect { list -> _state.update { it.copy(loading = false, people = list, error = null) } }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun messageShown() = _state.update { it.copy(message = null) }

    fun changeStatus(uid: String, status: String) {
        viewModelScope.launch {
            try {
                repo.setPartnerStatus(uid, status)
                _state.update { it.copy(message = "Status updated to $status.") }
            } catch (e: Exception) {
                _state.update { it.copy(message = e.toUserMessage()) }
            }
        }
    }
}
