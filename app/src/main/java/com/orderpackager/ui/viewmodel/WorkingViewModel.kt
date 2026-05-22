package com.orderpackager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orderpackager.data.db.entity.CyclicItem
import com.orderpackager.data.db.entity.OrderPosition
import com.orderpackager.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.orderpackager.R

data class WorkingState(
    val clientName: String = "",
    val orderId: Long = 0L,
    val cyclicItems: List<CyclicItem> = emptyList(),
    val currentIndex: Int = 0,
    // Форма текущей позиции
    val hasClothes: Boolean = false,
    val hasShoes: Boolean = false,
    val hasCosmetics: Boolean = false,
    val hasAccessories: Boolean = false,
    val hasOther: Boolean = false,
    val otherText: String = "",
    // Вес
    val weightKg: Float? = null,
    val isLoadingWeight: Boolean = false,
    val weightError: String? = null,
    // Статус
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

    init {
        viewModelScope.launch {
            // Загружаем циклический список и сохранённые позиции параллельно
            val items = repo.getAllCyclicItemsOnce()
            combine(
                flowOf(items),
                repo.getPositionsForOrder(orderId)
            ) { cyclicItems, saved ->
                _state.update { s ->
                    s.copy(
                        cyclicItems = cyclicItems,
                        savedPositions = saved,
                        isLoading = false
                    )
                }
                // При смене сохранённых позиций обновить форму текущей
                loadCurrentPositionData()
            }.collect()
        }
    }

    // ─── Навигация ────────────────────────────────────────────────────────────
    fun goNext() {
        val s = _state.value
        if (s.cyclicItems.isEmpty()) return
        val next = (s.currentIndex + 1) % s.cyclicItems.size
        _state.update { it.copy(currentIndex = next) }
        loadCurrentPositionData()
    }

    fun goPrev() {
        val s = _state.value
        if (s.cyclicItems.isEmpty()) return
        val prev = (s.currentIndex - 1 + s.cyclicItems.size) % s.cyclicItems.size
        _state.update { it.copy(currentIndex = prev) }
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
            weightKg       = saved?.weightKg?.takeIf { w -> w > 0f },
            weightError    = null
        )}
    }

    // ─── Форма ────────────────────────────────────────────────────────────────
    fun setClothes(v: Boolean) = _state.update { it.copy(hasClothes = v) }
    fun setShoes(v: Boolean) = _state.update { it.copy(hasShoes = v) }
    fun setCosmetics(v: Boolean) = _state.update { it.copy(hasCosmetics = v) }
    fun setAccessories(v: Boolean) = _state.update { it.copy(hasAccessories = v) }
    fun setOther(v: Boolean) = _state.update { it.copy(hasOther = v) }
    fun setOtherText(v: String) = _state.update { it.copy(otherText = v) }

    // ─── Весы ─────────────────────────────────────────────────────────────────
    fun fetchWeight() {
        _state.update { it.copy(isLoadingWeight = true, weightError = null) }
        viewModelScope.launch {
            repo.getWeightFromScale(context)
                .onSuccess { w -> _state.update { it.copy(weightKg = w, isLoadingWeight = false) } }
                .onFailure { e -> _state.update { it.copy(
                    isLoadingWeight = false,
                    //weightError = "Ошибка весов: ${e.localizedMessage}"
                    weightError = stringResource(R.string.weight_error,e.localizedMessage)
                )}}
        }
    }

    // ─── Сохранение позиции ───────────────────────────────────────────────────
    suspend fun saveCurrentPosition(): Boolean {
        val s = _state.value
        val currentItem = s.currentItem ?: return false
        val position = OrderPosition(
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
        )
        repo.upsertPosition(position)
        return true
    }

    fun completePosition() {
        viewModelScope.launch {
            saveCurrentPosition()
            goNext()
            _state.update { it.copy(snackbarMessage = stringResource(R.string.position_saved)) }
        }
    }

    fun clearSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    fun buildCompositionText(): String {
        val s = _state.value
        val parts = mutableListOf<String>()
        if (s.hasClothes) parts += "Одежда"
        if (s.hasShoes) parts += "Обувь"
        if (s.hasCosmetics) parts += "Косметика"
        if (s.hasAccessories) parts += "Аксессуары"
        if (s.hasOther && s.otherText.isNotBlank()) parts += "Другое: ${s.otherText}"
        else if (s.hasOther) parts += "Другое"
        return parts.joinToString(", ")
    }
}
