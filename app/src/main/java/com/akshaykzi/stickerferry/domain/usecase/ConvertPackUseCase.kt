package com.akshaykzi.stickerferry.domain.usecase

import android.content.Context
import com.akshaykzi.stickerferry.data.conversion.ConversionPipeline
import com.akshaykzi.stickerferry.domain.model.ConversionState
import com.akshaykzi.stickerferry.domain.model.Sticker
import com.akshaykzi.stickerferry.domain.model.StickerPack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for converting a Telegram sticker pack to WhatsApp format.
 *
 * This use case handles:
 * - Converting each sticker in the pack
 * - Generating tray icon
 * - Validating output against WhatsApp constraints
 * - Reporting progress per sticker
 */
@Singleton
class ConvertPackUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversionPipeline: ConversionPipeline,
) {
    operator fun invoke(pack: StickerPack): Flow<ConversionProgress> = flow {
        val outputDir = getOutputDirectory(pack.identifier)

        // Generate tray icon first
        emit(ConversionProgress.TrayIconConverting)
        val trayIconResult = conversionPipeline.generateTrayIcon(
            stickers = pack.stickers,
            outputDir = outputDir,
        )
        trayIconResult.onFailure { error ->
            emit(ConversionProgress.Failed("Failed to generate tray icon: ${error.message}"))
            return@flow
        }
        val trayIconFile = trayIconResult.getOrThrow()
        emit(ConversionProgress.TrayIconDone(trayIconFile.name))
        android.util.Log.d("ConvertUseCase", "Tray icon generated: ${trayIconFile.name}")

        // Convert each sticker
        val convertedStickers = mutableListOf<Sticker>()
        pack.stickers.forEachIndexed { index, sticker ->
            // Skip already converted stickers if the file exists
            val existingFile = sticker.localCachePath?.let { File(it) }
            if (sticker.conversionState is ConversionState.Done && existingFile?.exists() == true) {
                convertedStickers.add(sticker)
                emit(ConversionProgress.StickerDone(index, sticker))
                return@forEachIndexed
            }

            emit(ConversionProgress.StickerConverting(index, sticker))

            val result = conversionPipeline.convertSticker(
                sticker = sticker,
                outputDir = outputDir,
            )
            android.util.Log.d("ConvertUseCase", "Converted sticker $index: ${sticker.fileName}")

            result.onSuccess { convertedSticker ->
                convertedStickers.add(convertedSticker)
                emit(ConversionProgress.StickerDone(index, convertedSticker))
            }.onFailure { error ->
                emit(ConversionProgress.StickerFailed(index, error))
            }
        }

        // Create the converted pack
        val convertedPack = pack.copy(
            stickers = convertedStickers,
            trayImageFile = "tray_icon.png", // Explicitly ensure this matches generated file
        )

        // Validate the converted pack
        val validationErrors = convertedPack.validate()
        if (validationErrors.isNotEmpty()) {
            emit(ConversionProgress.ValidationFailed(validationErrors))
            return@flow
        }

        emit(ConversionProgress.Completed(convertedPack, outputDir))
        android.util.Log.d("ConvertUseCase", "Conversion successfully completed for pack: ${pack.identifier}")
    }.flowOn(Dispatchers.IO)

    /**
     * Gets or creates the output directory for converted stickers.
     */
    fun getOutputDirectory(packId: String): File {
        val baseDir = File(context.filesDir, "converted_stickers")
        val outputDir = File(baseDir, packId)
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        return outputDir
    }
}

/**
 * Represents the progress of a sticker pack conversion.
 */
sealed class ConversionProgress {
    /** A step failed with an error */
    data class Failed(val message: String) : ConversionProgress()

    /** Tray icon is being generated */
    data object TrayIconConverting : ConversionProgress()

    /** Tray icon generation completed */
    data class TrayIconDone(val fileName: String) : ConversionProgress()

    /** A sticker is being converted */
    data class StickerConverting(
        val index: Int,
        val sticker: Sticker,
    ) : ConversionProgress()

    /** A sticker conversion completed */
    data class StickerDone(
        val index: Int,
        val sticker: Sticker,
    ) : ConversionProgress()

    /** A sticker conversion failed */
    data class StickerFailed(
        val index: Int,
        val error: Throwable,
    ) : ConversionProgress()

    /** Pack validation failed */
    data class ValidationFailed(
        val errors: List<String>,
    ) : ConversionProgress()

    /** Pack conversion completed successfully */
    data class Completed(
        val pack: StickerPack,
        val outputDir: File,
    ) : ConversionProgress()
}