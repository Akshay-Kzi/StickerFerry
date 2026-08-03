package com.akshaykzi.stickerferry.ui.preview

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshaykzi.stickerferry.data.repository.StickerPackRepository
import com.akshaykzi.stickerferry.data.telegram.TelegramRepository
import com.akshaykzi.stickerferry.domain.model.StickerPack
import com.akshaykzi.stickerferry.domain.usecase.FetchPackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for the Preview screen.
 *
 * Handles:
 * - Loading sticker pack data
 * - Editing pack metadata
 * - Initiating conversion
 */
@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val fetchPackUseCase: FetchPackUseCase,
    private val telegramRepository: TelegramRepository,
    private val stickerPackRepository: StickerPackRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    /**
     * Loads a sticker pack from the given link.
     */
    fun loadStickerPack(link: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
            )

            // 1. Check local storage first (by identifier or name)
            val packIdentifier = fetchPackUseCase.extractPackName(link) ?: link
            val localPack = stickerPackRepository.getPack(packIdentifier) ?: stickerPackRepository.getPack(link)
            
            if ((localPack != null) && (verifyLocalFiles(localPack))) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pack = localPack,
                    editableName = localPack.name,
                    editablePublisher = localPack.publisher,
                )
                return@launch
            }

            // 2. If it's a "Part X" identifier but not found locally, try to fetch the base pack
            val splitMatch = Regex("(.*)_([0-9]+)$").find(packIdentifier)
            if (splitMatch != null) {
                val baseName = splitMatch.groupValues[1]
                val partIndex = splitMatch.groupValues[2].toInt() - 1
                
                fetchPackUseCase(baseName).onSuccess { parts ->
                    val correctPart = parts.getOrNull(partIndex)
                    if (correctPart != null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            pack = correctPart,
                            editableName = correctPart.name,
                            editablePublisher = correctPart.publisher,
                        )
                        downloadStickerImages(correctPart)
                        return@launch
                    }
                }
            }

            // 3. Fallback to Telegram fetch
            fetchPackUseCase(link)
                .onSuccess { packs ->
                    val pack = packs.firstOrNull()
                    if (pack != null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            pack = pack,
                            editableName = pack.name,
                            editablePublisher = pack.publisher,
                        )
                        downloadStickerImages(pack)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "No stickers found in this pack",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load sticker pack",
                    )
                }
        }
    }

    /**
     * Downloads sticker images for thumbnail display.
     */
    private fun downloadStickerImages(pack: StickerPack) {
        viewModelScope.launch {
            val cacheDir = File(context.cacheDir, "sticker_previews").apply { mkdirs() }
            val updatedStickers = coroutineScope {
                pack.stickers.map { sticker ->
                    async(Dispatchers.IO) {
                        if (sticker.fileId.isBlank()) return@async sticker
                        try {
                            val bytes = telegramRepository.downloadSticker(sticker.fileId, sticker.fileUniqueId)
                            val cacheFile = File(cacheDir, "${sticker.fileUniqueId}.webp")
                            cacheFile.writeBytes(bytes)
                            sticker.copy(localCachePath = cacheFile.absolutePath)
                        } catch (_: Exception) {
                            sticker
                        }
                    }
                }.awaitAll()
            }
            val currentPack = _uiState.value.pack ?: return@launch
            _uiState.value = _uiState.value.copy(
                pack = currentPack.copy(stickers = updatedStickers),
            )
        }
    }

    /**
     * Verifies that all sticker files for a pack exist locally.
     */
    private fun verifyLocalFiles(pack: StickerPack): Boolean {
        if (pack.stickers.isEmpty()) return false
        return pack.stickers.all { sticker ->
            val path = sticker.localCachePath ?: return@all false
            File(path).exists()
        }
    }
}

/**
 * UI state for the Preview screen.
 */
data class PreviewUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val pack: StickerPack? = null,
    val editableName: String = "",
    val editablePublisher: String = "",
)