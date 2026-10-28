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

            // 1. Check local storage first. All parts are saved to the DB when the pack
            //    is first fetched, so parts 2+ of a >30 pack load from here without any
            //    Telegram round-trip. Previously unconverted parts were discarded here
            //    (no local files yet) and re-fetched from Telegram by reconstructing the
            //    base pack name from the part identifier, which failed with
            //    "HTTP 400: Bad Request" whenever the reconstruction was wrong.
            val packIdentifier = fetchPackUseCase.extractPackName(link) ?: link
            val localPack = stickerPackRepository.getPack(packIdentifier)
                ?: stickerPackRepository.getPack(link)

            if (localPack != null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pack = localPack,
                    editableName = localPack.name,
                    editablePublisher = localPack.publisher,
                )
                // Only the preview thumbnails depend on local cache files; the pack
                // itself is converted later by the conversion pipeline.
                if (localPack.stickers.any { it.localCachePath == null || !File(it.localCachePath).exists() }) {
                    downloadStickerImages(localPack)
                }
                return@launch
            }

            // 2. Not in the DB: fetch from Telegram. When the link refers to a specific
            //    part ("pack_static_2"), match the returned part by exact identifier —
            //    positional lookup breaks for mixed static+animated packs where the part
            //    list contains both "_static_N" and "_anim_N" entries.
            val partNumber = Regex("(.*)_([0-9]+)$").find(packIdentifier)
                ?.groupValues?.get(2)?.toIntOrNull()

            fetchPackUseCase(link)
                .onSuccess { packs ->
                    val pack = if (partNumber != null) {
                        packs.firstOrNull { it.identifier == packIdentifier }
                            ?: packs.getOrNull(partNumber - 1)
                            ?: packs.firstOrNull()
                    } else {
                        packs.firstOrNull()
                    }

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
            
            // Limit concurrency to avoid UI lag and network congestion
            val updatedStickers = pack.stickers.chunked(5).flatMap { chunk ->
                coroutineScope {
                    chunk.map { sticker ->
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
            }

            val currentPack = _uiState.value.pack ?: return@launch
            _uiState.value = _uiState.value.copy(
                pack = currentPack.copy(stickers = updatedStickers),
            )
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