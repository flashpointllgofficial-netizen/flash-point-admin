package com.flashpoint.admin.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flashpoint.admin.core.toUserMessage
import com.flashpoint.admin.data.ReportData
import com.flashpoint.admin.data.ReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ReportsState(
    val loading: Boolean = true,
    val data: ReportData? = null,
    val from: Long = 0L,
    val to: Long = Long.MAX_VALUE,
    val fromText: String = "",
    val toText: String = "",
    val preset: String = "ALL",
    val error: String? = null,
)

class ReportsViewModel(private val repo: ReportRepository = ReportRepository()) : ViewModel() {
    private val _state = MutableStateFlow(ReportsState())
    val state: StateFlow<ReportsState> = _state
    private val fmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val d = repo.load()
                _state.update { it.copy(loading = false, data = d) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun preset(name: String) {
        val day = 24L * 60 * 60 * 1000
        val from = when (name) {
            "TODAY" -> startOfToday()
            "7D" -> startOfToday() - 6 * day
            "30D" -> startOfToday() - 29 * day
            else -> 0L
        }
        _state.update {
            it.copy(preset = name, from = from, to = Long.MAX_VALUE,
                fromText = if (from > 0) fmt.format(from) else "", toText = "")
        }
    }

    /** Returns an error message, or null when the custom range was applied. */
    fun applyCustom(fromText: String, toText: String): String? {
        val f = if (fromText.isBlank()) 0L else runCatching { fmt.parse(fromText.trim())?.time }.getOrNull()
        val t = if (toText.isBlank()) Long.MAX_VALUE
        else runCatching { fmt.parse(toText.trim())?.time?.plus(24L * 60 * 60 * 1000 - 1) }.getOrNull()
        if (f == null || t == null) return "Use date format yyyy-MM-dd."
        if (t < f) return "End date must be after the start date."
        _state.update { it.copy(preset = "CUSTOM", from = f, to = t, fromText = fromText, toText = toText) }
        return null
    }
}
