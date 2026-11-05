package com.grey.naswatchdog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grey.naswatchdog.charts.HistoryRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class ConnectionConfig(val baseUrl: String, val token: String, val refreshMs: Long)

data class TelemetrySnapshot(
    val stats: Stats,
    val receivedAt: Long,
    val cached: Boolean = false,
)

enum class FeedStatus { CONNECTING, LIVE, RECONNECTING, CACHED, FAILED }

data class HistoryUiState(
    val samples: List<Sample> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val receivedAt: Long = 0L,
)

data class TelemetryUiState(
    val snapshot: TelemetrySnapshot? = null,
    val feedStatus: FeedStatus = FeedStatus.CONNECTING,
    val refreshing: Boolean = false,
    val error: String? = null,
    val histories: Map<HistoryRange, HistoryUiState> = emptyMap(),
)

class TelemetryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TelemetryRepository(application)
    private val _state = MutableStateFlow(TelemetryUiState())
    val state: StateFlow<TelemetryUiState> = _state.asStateFlow()

    private var config: ConnectionConfig? = null
    private var foregroundActive = false
    private var statsJob: Job? = null
    private var historyJob: Job? = null
    private var visibleHistoryRange = HistoryRange.ONE_HOUR
    private var latestNetworkResponse: StatsResponse? = null

    fun configure(baseUrl: String, token: String, refreshMs: Long) {
        val newConfig = ConnectionConfig(baseUrl, token, refreshMs.coerceAtLeast(5_000L))
        val oldConfig = config
        if (newConfig == oldConfig) {
            startForegroundJobs()
            return
        }
        config = newConfig
        if (oldConfig?.baseUrl != baseUrl || oldConfig.token != token) {
            statsJob?.cancel()
            historyJob?.cancel()
            latestNetworkResponse = null
            _state.value = TelemetryUiState()
            loadCachedSnapshot(newConfig)
        } else {
            // The running loops captured the previous interval; replace them cleanly.
            restartStats()
            restartHistory()
        }
        startForegroundJobs()
    }

    fun setForegroundActive(active: Boolean) {
        if (foregroundActive == active) return
        foregroundActive = active
        if (active) {
            // Verify the retained snapshot before presenting live actions after a pause.
            _state.update {
                if (it.snapshot == null) it else it.copy(feedStatus = FeedStatus.RECONNECTING)
            }
            startForegroundJobs()
        } else {
            statsJob?.cancel()
            historyJob?.cancel()
            val current = config
            val latest = latestNetworkResponse
            if (current != null && latest != null) {
                viewModelScope.launch {
                    repository.persistNow(current.baseUrl, current.token, latest)
                }
            }
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun refresh() {
        restartStats()
        restartHistory()
    }

    fun showHistory(range: HistoryRange) {
        if (visibleHistoryRange == range && historyJob?.isActive == true) return
        visibleHistoryRange = range
        restartHistory()
    }

    private fun loadCachedSnapshot(config: ConnectionConfig) {
        viewModelScope.launch {
            val cached = repository.cachedStats(config.baseUrl, config.token)
            if (config != this@TelemetryViewModel.config || cached == null) return@launch
            val hasNetworkSnapshot = _state.value.snapshot?.cached == false
            if (!hasNetworkSnapshot) {
                _state.update {
                    it.copy(
                        snapshot = TelemetrySnapshot(cached.response.stats, cached.savedAt, cached = true),
                        feedStatus = FeedStatus.CACHED,
                    )
                }
            }
        }
    }

    private fun startForegroundJobs() {
        if (!foregroundActive || config == null) return
        if (statsJob?.isActive != true) startStatsLoop()
        if (historyJob?.isActive != true) startHistoryLoop()
    }

    private fun restartStats() {
        if (!foregroundActive) return
        statsJob?.cancel()
        startStatsLoop()
    }

    private fun restartHistory() {
        if (!foregroundActive) return
        historyJob?.cancel()
        startHistoryLoop()
    }

    private fun startStatsLoop() {
        val requestConfig = config ?: return
        statsJob = viewModelScope.launch {
            while (currentCoroutineContext().isActive) {
                _state.update { it.copy(refreshing = true) }
                try {
                    val response = repository.fetchStats(requestConfig.baseUrl, requestConfig.token)
                    if (requestConfig != config) return@launch
                    latestNetworkResponse = response
                    _state.update {
                        it.copy(
                            snapshot = TelemetrySnapshot(response.stats, System.currentTimeMillis()),
                            feedStatus = FeedStatus.LIVE,
                            refreshing = false,
                            error = null,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (requestConfig != config) return@launch
                    val message = connectionError(e)
                    _state.update {
                        it.copy(
                            feedStatus = when {
                                it.snapshot?.cached == true -> FeedStatus.CACHED
                                it.snapshot != null -> FeedStatus.RECONNECTING
                                else -> FeedStatus.FAILED
                            },
                            refreshing = false,
                            error = message,
                        )
                    }
                } finally {
                    if (_state.value.refreshing && requestConfig == config && statsJob?.isActive != true) {
                        _state.update { it.copy(refreshing = false) }
                    }
                }
                delay(requestConfig.refreshMs)
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (statsJob === job) _state.update { it.copy(refreshing = false) }
            }
        }
    }

    private fun startHistoryLoop() {
        val requestConfig = config ?: return
        val range = visibleHistoryRange
        historyJob = viewModelScope.launch {
            while (currentCoroutineContext().isActive) {
                updateHistory(range) { it.copy(loading = true, error = null) }
                try {
                    val samples = repository.fetchHistory(requestConfig.baseUrl, requestConfig.token, range)
                    if (requestConfig != config) return@launch
                    updateHistory(range) {
                        HistoryUiState(samples, loading = false, receivedAt = System.currentTimeMillis())
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (requestConfig != config) return@launch
                    updateHistory(range) { it.copy(loading = false, error = connectionError(e)) }
                }
                delay(60_000)
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (historyJob === job) updateHistory(range) { it.copy(loading = false) }
            }
        }
    }

    private fun updateHistory(range: HistoryRange, transform: (HistoryUiState) -> HistoryUiState) {
        _state.update { state ->
            state.copy(
                histories = state.histories.toMutableMap().apply {
                    this[range] = transform(this[range] ?: HistoryUiState())
                }
            )
        }
    }
}

internal fun freshnessLabel(
    status: FeedStatus,
    receivedAt: Long?,
    now: Long = System.currentTimeMillis(),
): String {
    if (receivedAt == null) return if (status == FeedStatus.FAILED) "NO CONNECTION" else "CONNECTING"
    val seconds = ((now - receivedAt) / 1000L).coerceAtLeast(0L)
    val age = when {
        seconds < 5L -> "JUST NOW"
        seconds < 60L -> "$seconds SEC AGO"
        seconds < 3600L -> "${seconds / 60L} MIN AGO"
        else -> "${seconds / 3600L} H AGO"
    }
    return when (status) {
        FeedStatus.LIVE -> "LIVE · RECEIVED $age"
        FeedStatus.CACHED -> "CACHED SNAPSHOT · $age"
        FeedStatus.RECONNECTING -> "RECONNECTING · DATA FROM $age"
        else -> "LAST KNOWN SNAPSHOT · $age"
    }
}
