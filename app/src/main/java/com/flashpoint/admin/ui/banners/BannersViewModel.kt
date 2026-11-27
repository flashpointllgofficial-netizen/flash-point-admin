package com.flashpoint.admin.ui.banners

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flashpoint.admin.core.BannerItem
import com.flashpoint.admin.core.UploadException
import com.flashpoint.admin.core.toUserMessage
import com.flashpoint.admin.data.BannerRepository
import com.flashpoint.admin.data.CloudinaryUploader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BannersState(
    val loading: Boolean = true,
    val banners: List<BannerItem> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val saving: Boolean = false,
    val progress: Int = 0,
)

class BannersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BannerRepository()
    private val _state = MutableStateFlow(BannersState())
    val state: StateFlow<BannersState> = _state
    private var job: Job? = null

    init {
        viewModelScope.launch {
            repo.banners().catch { e -> _state.update { it.copy(loading = false, error = e.toUserMessage()) } }
                .collect { list -> _state.update { it.copy(loading = false, banners = list, error = null) } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun save(banner: BannerItem, newImage: Uri?, onSuccess: () -> Unit) {
        if (_state.value.saving) return
        job = viewModelScope.launch {
            _state.update { it.copy(saving = true, progress = 0) }
            try {
                var b = banner
                if (newImage != null) {
                    val r = CloudinaryUploader.upload(getApplication(), newImage) { p ->
                        _state.update { it.copy(progress = p) }
                    }
                    b = b.copy(imageUrl = r.url, imagePublicId = r.publicId)
                }
                if (b.imageUrl.isBlank()) throw UploadException("Please select a banner image.")
                val next = (_state.value.banners.maxOfOrNull { it.sortOrder } ?: 0L) + 1
                repo.save(b, next)
                _state.update { it.copy(message = "Banner saved.") }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.toUserMessage()) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun cancelUpload() {
        job?.cancel()
        _state.update { it.copy(saving = false, message = "Upload cancelled.") }
    }

    fun delete(id: String) = act("Banner deleted.") { repo.delete(id) }
    fun toggle(id: String, enabled: Boolean) = act(null) { repo.setEnabled(id, enabled) }
    fun move(list: List<BannerItem>, index: Int, delta: Int) {
        val other = index + delta
        if (other !in list.indices) return
        act(null) { repo.swapOrder(list[index], list[other]) }
    }

    private fun act(ok: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                if (ok != null) _state.update { it.copy(message = ok) }
            } catch (e: Exception) {
                _state.update { it.copy(message = e.toUserMessage()) }
            }
        }
    }
}
