package com.flashpoint.admin.ui.notifications

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flashpoint.admin.core.SentNotification
import com.flashpoint.admin.core.toUserMessage
import com.flashpoint.admin.data.CloudinaryUploader
import com.flashpoint.admin.data.NotificationRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotifState(
    val sending: Boolean = false,
    val progress: Int = 0,
    val message: String? = null,
    val history: List<SentNotification> = emptyList(),
    val historyLoading: Boolean = true,
    val historyError: String? = null,
)

class NotificationsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = NotificationRepository()
    private val _state = MutableStateFlow(NotifState())
    val state: StateFlow<NotifState> = _state
    private var job: Job? = null

    init {
        viewModelScope.launch {
            repo.history().catch { e ->
                _state.update { it.copy(historyLoading = false, historyError = e.toUserMessage()) }
            }.collect { list -> _state.update { it.copy(historyLoading = false, history = list, historyError = null) } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun send(
        title: String, message: String, image: Uri?, link: String, button: String,
        audience: String, role: String, uid: String, onSuccess: () -> Unit,
    ) {
        if (_state.value.sending) return
        job = viewModelScope.launch {
            _state.update { it.copy(sending = true, progress = 0) }
            try {
                var imageUrl = ""
                if (image != null) {
                    imageUrl = CloudinaryUploader.upload(getApplication(), image) { p ->
                        _state.update { it.copy(progress = p) }
                    }.url
                }
                val adminUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                val count = repo.send(title, message, imageUrl, link, button, audience, role, uid, adminUid)
                _state.update { it.copy(message = "Notification sent to $count recipient(s).") }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(sending = false) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(sending = false, message = "Cancelled.") }
    }
}
