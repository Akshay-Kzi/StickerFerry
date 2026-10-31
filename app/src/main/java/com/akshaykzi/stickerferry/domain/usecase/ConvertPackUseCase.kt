package com.akshaykzi.stickerferry.domain.usecase

import android.content.Context
import com.akshaykzi.stickerferry.data.conversion.ConversionPipeline
import com.akshaykzi.stickerferry.domain.model.ConversionState
import com.akshaykzi.stickerferry.domain.model.Sticker
import com.akshaykzi.stickerferry.domain.model.StickerPack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentSkipListMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for converting a Telegram sticker pack to WhatsApp format.
 *
 * This use case handles:
 * - Converting each sticker (in parallel, bounded)
 * - Generating tray icon
 * - Validating output against WhatsApp constraints
 * - Reporting progress per sticker
 */
@Singleton
class ConvertPackUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversionPipeline: ConversionPipeline,
    private val stickerPackRepository: com.akshaykzi.stickerferry.data.repository.StickerPackRepository,
) {
    companion object {
        // Sticker conversion is mostly network + decode time, which overlaps well.
        // Bounded so we don't hammer Telegram or starve the decoders; the converters
        // themselves are stateless per call, so parallel runs are safe.
        private const val PARALLEL_CONVERSIONS = 4
    }

    /**
     * Converts a pack, emitting [ConversionProgress] as work completes. Sticker
     * progress events may arrive out of order; the completed pack always keeps the
     * original sticker order.
     */
    operator fun invoke(pack: StickerPack): Flow<ConversionProgress> = channelFlow {
        val outputDir = getOutputDirectory(pack.identifier)

        // Generate tray icon first
        send(ConversionProgress.TrayIconConverting)
        val trayIconResult = conversionPipeline.generateTrayIcon(
            stickers = pack.stickers,
            outputDir = outputDir,
        )
        trayIconResult.onFailure { error ->
            send(ConversionProgress.Failed("Failed to generate tray icon: ${error.message}"))
            return@channelFlow
        }
        val trayIconFile = trayIconResult.getOrThrow()
        send(ConversionProgress.TrayIconDone(trayIconFile.name))
        android.util.Log.d("ConvertUseCase", "Tray icon generated: ${trayIconFile.name}")

        // Convert stickers with bounded parallelism. Failed stickers are simply
        // absent from the completed pack (same behavior as the sequential version).
        val converted = ConcurrentSkipListMap<Int, Sticker>()
        val semaphore = Semaphore(PARALLEL_CONVERSIONS)
        coroutineScope {
            pack.stickers.forEachIndexed { index, sticker ->
                launch {
                    semaphore.withPermit {
                        // Skip already converted stickers whose file still exists
                        val existingFile = sticker.localCachePath?.let { File(it) }
                        if (sticker.conversionState is ConversionState.Done && existingFile?.exists() == true) {
                            converted[index] = sticker
                            send(ConversionProgress.StickerDone(index, sticker))
                            return@withPermit
                        }

                        send(ConversionProgress.StickerConverting(index, sticker))
                        android.util.Log.d("ConvertUseCase", "Converting sticker $index: ${sticker.fileName}")

                        conversionPipeline.convertSticker(
                            sticker = sticker,
                            outputDir = outputDir,
                        )
                            .onSuccess { convertedSticker ->
                                converted[index] = convertedSticker
                                send(ConversionProgress.StickerDone(index, convertedSticker))
                            }
                            .onFailure { error ->
                                send(ConversionProgress.StickerFailed(index, error))
                            }
                    }
                }
            }
        }

        // ConcurrentSkipListMap iterates in key order == original sticker order.
        val convertedStickers = converted.values.toList()

        // Create the converted pack
        val convertedPack = pack.copy(
            stickers = convertedStickers,
            trayImageFile = "tray_icon.png", // Explicitly ensure this matches generated file
        )

        // Validate the converted pack
        val validationErrors = convertedPack.validate()
        if (validationErrors.isNotEmpty()) {
            send(ConversionProgress.ValidationFailed(validationErrors))
            return@channelFlow
        }

        // Persist here, on the conversion flow: saving from the UI collector loses
        // the pack if the user leaves the screen in the completion instant (the
        // ViewModel scope is cancelled, killing the collect mid-flight).
        stickerPackRepository.savePack(convertedPack)

        send(ConversionProgress.Completed(convertedPack, outputDir))
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
