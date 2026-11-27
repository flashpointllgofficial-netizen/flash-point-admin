package com.flashpoint.admin.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashpoint.admin.core.toUserMessage
import com.flashpoint.admin.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthState(
    val checking: Boolean = true,
    val loading: Boolean = false,
    val signedIn: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(private val repo: AuthRepository = AuthRepository()) : ViewModel() {
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state

    init {
        viewModelScope.launch {
            if (!repo.hasUser) { _state.update { it.copy(checking = false) }; return@launch }
            try {
                if (repo.isAdmin()) _state.update { it.copy(checking = false, signedIn = true) }
                else { repo.signOut(); _state.update { it.copy(checking = false) } }
            } catch (e: Exception) {
                _state.update { it.copy(checking = false, error = e.toUserMessage()) }
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Please complete all required fields.") }; return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                repo.signIn(email.trim(), password)
                _state.update { it.copy(loading = false, signedIn = true) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun signOut() {
        repo.signOut()
        _state.value = AuthState(checking = false)
    }
}
