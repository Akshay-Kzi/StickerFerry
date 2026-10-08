package com.akshaykzi.stickerferry.data.conversion

import com.akshaykzi.stickerferry.domain.model.Sticker
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main conversion pipeline for converting Telegram stickers to WhatsApp format.
 *
 * This pipeline handles:
 * - Static WebP stickers
 * - Animated .tgs (Lottie) stickers
 * - Video .webm stickers
 * - Tray icon generation
 */
@Singleton
class ConversionPipeline @Inject constructor(
    private val staticConverter: StaticStickerConverter,
    private val animatedConverter: AnimatedStickerConverter,
    private val videoConverter: VideoStickerConverter,
) {
    /**
     * Converts a single sticker to WhatsApp format.
     *
     * @param sticker The sticker to convert
     * @param outputDir Directory to write the converted sticker
     * @return Result containing the converted sticker or an error
     */
    suspend fun convertSticker(
        sticker: Sticker,
        outputDir: File,
    ): Result<Sticker> {
        return try {
            when {
                sticker.isVideo -> {
                    videoConverter.convert(sticker, outputDir)
                }
                sticker.isAnimated -> {
                    animatedConverter.convert(sticker, outputDir)
                }
                else -> {
                    staticConverter.convert(sticker, outputDir)
                }
            }
        } catch (e: Exception) {
            Result.failure(ConversionException("Failed to convert sticker: ${e.message}", e))
        }
    }

    /**
     * Generates a tray icon for the sticker pack.
     *
     * @param stickers List of stickers in the pack
     * @param outputDir Directory to write the tray icon
     * @return Result containing the tray icon file or an error
     */
    suspend fun generateTrayIcon(
        stickers: List<Sticker>,
        outputDir: File,
    ): Result<File> {
        return try {
            // Find the first static sticker for the tray icon
            val traySticker = stickers.firstOrNull { !it.isAnimated && !it.isVideo }
                ?: stickers.firstOrNull()
                ?: return Result.failure(
                    ConversionException("No stickers available for tray icon"),
                )

            when {
                traySticker.isAnimated -> {
                    animatedConverter.renderFirstFrame(traySticker).fold(
                        onSuccess = { bitmap -> staticConverter.generateTrayIconFromBitmap(bitmap, outputDir) },
                        onFailure = { Result.failure(it) }
                    )
                }
                traySticker.isVideo -> {
                    videoConverter.extractFirstFrame(traySticker).fold(
                        onSuccess = { bitmap -> staticConverter.generateTrayIconFromBitmap(bitmap, outputDir) },
                        onFailure = { Result.failure(it) }
                    )
                }
                else -> {
                    staticConverter.generateTrayIcon(traySticker, outputDir)
                }
            }
        } catch (e: Exception) {
            Result.failure(ConversionException("Failed to generate tray icon: ${e.message}", e))
        }
    }
}

/**
 * Exception thrown during conversion.
 */
class ConversionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)