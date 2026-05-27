package com.orderpackager.ui.viewmodel

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
    val hasClothes: Boolean = false,
    val hasShoes: Boolean = false,
    val hasCosmetics: Boolean = false,
    val hasAccessories: Boolean = false,
    val hasOther: Boolean = false,
    val otherText: String = "",
    val weightKg: Float? = null,
    val isLoadingWeight: Boolean = false,
    val weightError: String? = null,
    val autoWeight: Boolean = true,     // ← автоопрос включён/выключен
    val savedPositions: List<OrderPosition> = emptyList(),
    val isLoading: Boolean = true,
    val snackbarMessage: String? = null
) {
    val currentItem: CyclicItem? get() = cyclicItems.getOrNull(currentIndex)
    val isCurrentSaved: Boolean get() =
        currentItem != null && savedPositions.any { it.cyclicItemId == currentItem!!.id }
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

    companion object {
        // Интервал автоопроса — 2 секунды
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
                    s.copy(
                        cyclicItems    = cyclicItems,
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
            hasClothes     = saved?.hasClothes ?: false,
            hasShoes       = saved?.hasShoes ?: false,
            hasCosmetics   = saved?.hasCosmetics ?: false,
            hasAccessories = saved?.hasAccessories ?: false,
            hasOther       = saved?.hasOther ?: false,
            otherText      = saved?.otherText ?: "",
            // Вес не сбрасываем если включён автоопрос
            weightKg       = if (s.autoWeight) s.weightKg
            else saved?.weightKg?.takeIf { w -> w > 0f },
            weightError    = null
        )}
    }

    // ─── Форма ────────────────────────────────────────────────────────────────

    fun setClothes(v: Boolean)     = _state.update { it.copy(hasClothes = v) }
    fun setShoes(v: Boolean)       = _state.update { it.copy(hasShoes = v) }
    fun setCosmetics(v: Boolean)   = _state.update { it.copy(hasCosmetics = v) }
    fun setAccessories(v: Boolean) = _state.update { it.copy(hasAccessories = v) }
    fun setOther(v: Boolean)       = _state.update { it.copy(hasOther = v) }
    fun setOtherText(v: String)    = _state.update { it.copy(otherText = v) }

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
        val currentItem = s.currentItem ?: return false
        repo.upsertPosition(OrderPosition(
            orderId        = orderId,
            cyclicItemId   = currentItem.id,
            cyclicItemName = currentItem.name,
            hasClothes     = s.hasClothes,
            hasShoes       = s.hasShoes,
            hasCosmetics   = s.hasCosmetics,
            hasAccessories = s.hasAccessories,
            hasOther       = s.hasOther,
            otherText      = s.otherText,
            weightKg       = s.weightKg ?: 0f
        ))
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

    fun buildCompositionText(): String {
        val s = _state.value
        val parts = mutableListOf<String>()
        if (s.hasClothes) parts += "Одежда"
        if (s.hasShoes) parts += "Обувь"
        if (s.hasCosmetics) parts += "Косметика"
        if (s.hasAccessories) parts += "Аксессуары"
        if (s.hasOther && s.otherText.isNotBlank()) parts += "Сумка: ${s.otherText}"
        else if (s.hasOther) parts += "Сумка"
        return parts.joinToString(", ")
    }
}