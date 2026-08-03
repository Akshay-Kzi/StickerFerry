package com.akshaykzi.stickerferry.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshaykzi.stickerferry.data.repository.StickerPackRepository
import com.akshaykzi.stickerferry.domain.model.ConversionState
import com.akshaykzi.stickerferry.domain.model.Sticker
import com.akshaykzi.stickerferry.domain.model.StickerPack
import com.akshaykzi.stickerferry.domain.usecase.ConvertPackUseCase
import com.akshaykzi.stickerferry.domain.usecase.AddToWhatsAppUseCase
import com.akshaykzi.stickerferry.data.whatsapp.StickerContentProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for the Progress screen.
 */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    @ApplicationContext private val app: android.content.Context,
    private val convertPackUseCase: ConvertPackUseCase,
    private val addToWhatsAppUseCase: AddToWhatsAppUseCase,
    private val stickerPackRepository: StickerPackRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    private var conversionJob: Job? = null

    fun startConversion(pack: StickerPack) {
        conversionJob?.cancel()
        conversionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                pack = pack,
                stickers = pack.stickers,
                isConverting = true,
                errors = emptyList(),
            )

            convertPackUseCase(pack).collect { progress ->
                when (progress) {
                    is com.akshaykzi.stickerferry.domain.usecase.ConversionProgress.TrayIconConverting -> {
                        _uiState.value = _uiState.value.copy(currentStickerIndex = -1)
                    }
                    is com.akshaykzi.stickerferry.domain.usecase.ConversionProgress.StickerConverting -> {
                        _uiState.value = _uiState.value.copy(currentStickerIndex = progress.index)
                    }
                    is com.akshaykzi.stickerferry.domain.usecase.ConversionProgress.StickerDone -> {
                        val updatedStickers = _uiState.value.stickers.toMutableList()
                        updatedStickers[progress.index] = progress.sticker
                        _uiState.value = _uiState.value.copy(stickers = updatedStickers)
                    }
                    is com.akshaykzi.stickerferry.domain.usecase.ConversionProgress.StickerFailed -> {
                        val updatedStickers = _uiState.value.stickers.toMutableList()
                        val failedSticker = updatedStickers[progress.index].copy(
                            conversionState = ConversionState.Failed(progress.error.message ?: "Unknown error"),
                        )
                        updatedStickers[progress.index] = failedSticker
                        _uiState.value = _uiState.value.copy(
                            stickers = updatedStickers,
                            errors = _uiState.value.errors + (progress.error.message ?: "Sticker failed")
                        )
                    }
                    is com.akshaykzi.stickerferry.domain.usecase.ConversionProgress.Completed -> {
                        stickerPackRepository.savePack(progress.pack)
                        _uiState.value = _uiState.value.copy(
                            pack = progress.pack,
                            stickers = progress.pack.stickers,
                            currentStickerIndex = progress.pack.stickers.size,
                            isConverting = false,
                            isComplete = true,
                            outputDir = progress.outputDir,
                        )
                    }
                    is com.akshaykzi.stickerferry.domain.usecase.ConversionProgress.Failed -> {
                        _uiState.value = _uiState.value.copy(
                            errors = _uiState.value.errors + progress.message,
                            isConverting = false,
                        )
                    }
                    is com.akshaykzi.stickerferry.domain.usecase.ConversionProgress.ValidationFailed -> {
                        _uiState.value = _uiState.value.copy(
                            errors = _uiState.value.errors + progress.errors,
                            isConverting = false,
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    fun addToWhatsApp() {
        val pack = _uiState.value.pack ?: return
        
        viewModelScope.launch {
            val validationErrors = addToWhatsAppUseCase.validateForHandoff(pack)
            if (validationErrors.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(handoffErrors = validationErrors)
                return@launch
            }

            val authority = app.packageName + StickerContentProvider.AUTHORITY_SUFFIX
            val result = addToWhatsAppUseCase.createAddPackIntent(pack, authority)
            
            result.onSuccess { intent ->
                viewModelScope.launch {
                    stickerPackRepository.markAsAddedToWhatsApp(pack.identifier)
                }
                _uiState.value = _uiState.value.copy(
                    handoffReady = true,
                    handoffIntent = intent
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    handoffErrors = listOf(error.message ?: "Failed to create intent")
                )
            }
        }
    }

    fun resetHandoff() {
        _uiState.value = _uiState.value.copy(
            handoffReady = false,
            handoffIntent = null,
            handoffErrors = emptyList()
        )
    }

    fun cancelConversion() {
        conversionJob?.cancel()
        _uiState.value = _uiState.value.copy(isConverting = false, isCancelled = true)
    }

    fun retryConversion() {
        val pack = _uiState.value.pack ?: return
        startConversion(pack)
    }
}

data class ProgressUiState(
    val pack: StickerPack? = null,
    val stickers: List<Sticker> = emptyList(),
    val currentStickerIndex: Int = 0,
    val isConverting: Boolean = false,
    val isComplete: Boolean = false,
    val isCancelled: Boolean = false,
    val errors: List<String> = emptyList(),
    val outputDir: File? = null,
    val handoffReady: Boolean = false,
    val handoffIntent: android.content.Intent? = null,
    val handoffErrors: List<String> = emptyList(),
) {
    val overallProgress: Float
        get() {
            if (stickers.isEmpty()) return 0f
            val completedCount = stickers.count {
                (it.conversionState is ConversionState.Done || it.conversionState is ConversionState.Failed)
            }
            return completedCount.toFloat() / stickers.size
        }
}
