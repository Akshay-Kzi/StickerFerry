package com.akshaykzi.stickerferry.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshaykzi.stickerferry.data.repository.StickerPackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val stickerPackRepository: StickerPackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    fun checkNextPart(currentPackId: String) {
        if (!currentPackId.contains("_")) return

        viewModelScope.launch {
            val parts = currentPackId.split("_")
            val base = parts.dropLast(1).joinToString("_")
            val index = parts.last().toIntOrNull() ?: return@launch
            val nextId = "${base}_${index + 1}"
            
            val nextPack = stickerPackRepository.getPack(nextId)
            if (nextPack != null) {
                _uiState.value = _uiState.value.copy(nextPartId = nextId)
            }
        }
    }
}

data class ResultUiState(
    val nextPartId: String? = null
)
