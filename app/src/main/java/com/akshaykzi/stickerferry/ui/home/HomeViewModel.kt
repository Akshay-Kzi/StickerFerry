package com.akshaykzi.stickerferry.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshaykzi.stickerferry.data.repository.StickerPackRepository
import com.akshaykzi.stickerferry.domain.model.StickerPack
import com.akshaykzi.stickerferry.domain.usecase.FetchPackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Home screen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val fetchPackUseCase: FetchPackUseCase,
    private val stickerPackRepository: StickerPackRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeRecentPacks()
    }

    private fun observeRecentPacks() {
        viewModelScope.launch {
            stickerPackRepository.getRecentPacks().collectLatest { packs ->
                _uiState.value = _uiState.value.copy(recentPacks = packs)
            }
        }
    }

    fun fetchStickerPack(link: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
            )

            fetchPackUseCase(link)
                .onSuccess { packs ->
                    // Save all parts to DB so they appear in Recent
                    viewModelScope.launch {
                        packs.forEach { pack ->
                            stickerPackRepository.savePack(pack)
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            currentPack = packs.firstOrNull(),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to fetch sticker pack",
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun consumeNavigationEvent() {
        _uiState.value = _uiState.value.copy(currentPack = null)
    }

    fun removeRecentPack(pack: StickerPack) {
        viewModelScope.launch {
            stickerPackRepository.deletePack(pack.identifier)
        }
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPack: StickerPack? = null,
    val recentPacks: List<StickerPack> = emptyList(),
)
