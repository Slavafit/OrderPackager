package com.orderpackager.ui.viewmodel

import com.orderpackager.data.catalog.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orderpackager.data.db.entity.CyclicItem
import com.orderpackager.data.db.entity.OrderPosition
import com.orderpackager.repository.AppRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.orderpackager.R


data class WorkingState(
    val clientName: String = "",
    val orderId: Long = 0L,
    val cyclicItems: List<CyclicItem> = emptyList(),
    val currentIndex: Int = 0,
    val composition: List<CompositionItem> = emptyList(),
    val weightKg: Float? = null,
    val isLoadingWeight: Boolean = false,
    val weightError: String? = null,
    val autoWeight: Boolean = true,     // ← автоопрос включён/выключен
    val savedPositions: List<OrderPosition> = emptyList(),
    val isLoading: Boolean = true,
    val snackbarMessage: String? = null
) {
    val currentItem: CyclicItem? get() = cyclicItems.getOrNull(currentIndex)
    val hasSelectedComposition: Boolean get() =
        composition.isNotEmpty()
    val isCurrentSaved: Boolean get() =
        currentItem != null && savedPositions.any { it.cyclicItemId == currentItem!!.id }
    val totalOrderWeight: Float get() = savedPositions.sumOf { it.weightKg.toDouble() }.toFloat()
    val totalItems: Int get() = cyclicItems.size
}

class WorkingViewModel(
    private val repo: AppRepository,
    private val orderId: Long,
    private val clientName: String,
    private val context: android.content.Context
) : ViewModel() {

    private val _state = MutableStateFlow(WorkingState(clientName = clientName, orderId = orderId))
    val state: StateFlow<WorkingState> = _state.asStateFlow()

    private var pollingJob: Job? = null
    private var hasLoadedInitialIndex = false

    companion object {
        // Интервал автоопроса — 1 секунда
        const val POLL_INTERVAL_MS = 1000L
    }

    init {
        viewModelScope.launch {
            val items = repo.getAllCyclicItemsOnce()
            combine(
                flowOf(items),
                repo.getPositionsForOrder(orderId)
            ) { cyclicItems, saved ->
                _state.update { s ->
                    val currentIndex = if (hasLoadedInitialIndex) {
                        s.currentIndex.coerceIn(0, (cyclicItems.size - 1).coerceAtLeast(0))
                    } else {
                        repo.getSavedCyclicIndex(context, cyclicItems.size)
                    }
                    hasLoadedInitialIndex = true
                    s.copy(
                        cyclicItems    = cyclicItems,
                        currentIndex   = currentIndex,
                        savedPositions = saved,
                        isLoading      = false
                    )
                }
                loadCurrentPositionData()
                // запускаем автоопрос если он включен
                if (_state.value.autoWeight) {
                    startPolling()
                }
            }.collect()
        }
    }

    // ─── Автоопрос весов ──────────────────────────────────────────────────────

    fun toggleAutoWeight() {
        val enabled = !_state.value.autoWeight
        _state.update { it.copy(autoWeight = enabled, weightError = null) }
        if (enabled) startPolling() else stopPolling()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                repo.getWeightFromScale(context)
                    .onSuccess { w ->
                        _state.update { it.copy(weightKg = w, weightError = null) }
                    }
                    .onFailure { e ->
                        _state.update { it.copy(weightError = context.getString(R.string.weight_error, e.localizedMessage)) }
                    }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    // ─── Навигация ────────────────────────────────────────────────────────────

    fun goNext() {
        val s = _state.value
        if (s.cyclicItems.isEmpty()) return
        _state.update { it.copy(currentIndex = (s.currentIndex + 1) % s.cyclicItems.size) }
        loadCurrentPositionData()
    }

    fun goPrev() {
        val s = _state.value
        if (s.cyclicItems.isEmpty()) return
        _state.update { it.copy(currentIndex = (s.currentIndex - 1 + s.cyclicItems.size) % s.cyclicItems.size) }
        loadCurrentPositionData()
    }

    private fun loadCurrentPositionData() {
        val s = _state.value
        val currentItem = s.currentItem ?: return
        val saved = s.savedPositions.firstOrNull { it.cyclicItemId == currentItem.id }
        _state.update { it.copy(
            composition = Composition.decode(
                context.getSharedPreferences("composition_drafts", 0)
                    .getString("$orderId:${currentItem.id}", saved?.compositionJson ?: "[]")
            ),
            // Вес не сбрасываем если включён автоопрос
            weightKg       = if (s.autoWeight) s.weightKg
            else saved?.weightKg?.takeIf { w -> w > 0f },
            weightError    = null
        )}
    }

    // ─── Форма ────────────────────────────────────────────────────────────────

    // A composition is saved independently of weight/registration. Opening and
    // cancelling the editor never mutates this durable draft.
    suspend fun saveComposition(items: List<CompositionItem>) {
        require(items.all { it.quantity in 1..Composition.MAX_QUANTITY })
        val current = checkNotNull(_state.value.currentItem) { "Position is not loaded" }
        val json = Composition.encode(items)
        withContext(Dispatchers.IO) {
            check(context.getSharedPreferences("composition_drafts", 0).edit()
                .putString("$orderId:${current.id}", json).commit())
        }
        if (_state.value.currentItem?.id == current.id) {
            _state.update { it.copy(composition = items) }
        }
    }

    // ─── Ручной запрос ────────────────────────────────────────────────────────

    fun fetchWeight() {
        _state.update { it.copy(isLoadingWeight = true, weightError = null) }
        viewModelScope.launch {
            repo.getWeightFromScale(context)
                .onSuccess { w -> _state.update { it.copy(weightKg = w, isLoadingWeight = false) } }
                .onFailure { e -> _state.update { it.copy(
                    isLoadingWeight = false,
                    weightError = context.getString(R.string.weight_error,e.localizedMessage)
                )}}
        }
    }

    // ─── Сохранение позиции ───────────────────────────────────────────────────

    suspend fun saveCurrentPosition(): Boolean {
        val s = _state.value
        if (!s.hasSelectedComposition) return false
        val currentItem = s.currentItem ?: return false
        repo.upsertPosition(OrderPosition(
            orderId        = orderId,
            cyclicItemId   = currentItem.id,
            cyclicItemName = currentItem.name,
            compositionJson = Composition.encode(s.composition),
            weightKg       = s.weightKg ?: 0f
        ))
        context.getSharedPreferences("composition_drafts", 0).edit()
            .remove("$orderId:${currentItem.id}").apply()
        repo.saveNextCyclicIndex(context, s.currentIndex, s.cyclicItems.size)
        return true
    }

//    fun completePosition() {
//        viewModelScope.launch {
//            saveCurrentPosition()
//            goNext()
//            _state.update { it.copy(snackbarMessage = context.getString(R.string.position_saved)) }
//        }
//    }

    fun clearSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    fun buildCompositionText(): String = Composition.text(
        _state.value.composition, context.resources.configuration.locales[0].language
    )
}
