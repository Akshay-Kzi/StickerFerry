package com.akshaykzi.stickerferry.data.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.akshaykzi.stickerferry.data.telegram.TelegramRepository
import com.akshaykzi.stickerferry.domain.model.Sticker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converter for static Telegram stickers to WhatsApp format.
 */
@Singleton
class StaticStickerConverter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegramRepository: TelegramRepository,
) {
    companion object {
        private const val TARGET_SIZE = 512
        private const val MAX_SIZE_BYTES = 100 * 1024L // 100 KB
        private const val TRAY_ICON_SIZE = 96
        private const val INITIAL_QUALITY = 90
        private const val MIN_QUALITY = 50
        private const val QUALITY_STEP = 10
    }

    suspend fun convert(sticker: Sticker, outputDir: File): Result<Sticker> {
        return try {
            // 1. Download source bytes
            val bytes = telegramRepository.downloadSticker(sticker.fileId, sticker.fileUniqueId)

            // 2. Decode to Bitmap
            val options = BitmapFactory.Options().apply { inMutable = true }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: return Result.failure(ConversionException("Failed to decode sticker bitmap"))

            // 3. Resize/pad to 512x512
            val processedBitmap = ConversionUtils.resizeAndPad(bitmap, TARGET_SIZE)

            // 4. Re-encode as WebP with quality adjustment
            val (encodedBytes, quality) = encodeWithQualityAdjustment(processedBitmap, MAX_SIZE_BYTES)

            // 5. Write to output file
            val outputFile = File(outputDir, sticker.fileName)
            outputFile.writeBytes(encodedBytes)

            val convertedSticker = sticker.copy(
                conversionState = com.akshaykzi.stickerferry.domain.model.ConversionState.Done(
                    outputSizeBytes = encodedBytes.size.toLong(),
                ),
                localCachePath = outputFile.absolutePath
            )

            Result.success(convertedSticker)
        } catch (e: Exception) {
            Result.failure(ConversionException("Failed to convert static sticker: ${e.message}", e))
        }
    }

    suspend fun generateTrayIcon(sticker: Sticker, outputDir: File): Result<File> {
        return try {
            val bytes = telegramRepository.downloadSticker(sticker.fileId, sticker.fileUniqueId)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return Result.failure(ConversionException("Failed to decode tray icon bitmap"))

            generateTrayIconFromBitmap(bitmap, outputDir)
        } catch (e: Exception) {
            Result.failure(ConversionException("Failed to generate tray icon: ${e.message}", e))
        }
    }

    fun generateTrayIconFromBitmap(bitmap: Bitmap, outputDir: File): Result<File> {
        return try {
            val processedBitmap = ConversionUtils.resizeAndPad(bitmap, TRAY_ICON_SIZE)
            
            // WhatsApp prefers PNG for tray icon
            val outputStream = ByteArrayOutputStream()
            processedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val encodedBytes = outputStream.toByteArray()

            val trayIconFile = File(outputDir, "tray_icon.png")
            trayIconFile.writeBytes(encodedBytes)

            Result.success(trayIconFile)
        } catch (e: Exception) {
            Result.failure(ConversionException("Failed to encode tray icon: ${e.message}", e))
        }
    }

    @Suppress("DEPRECATION")
    fun encodeWithQualityAdjustment(
        bitmap: Bitmap,
        maxFileSize: Long,
        initialQuality: Int = INITIAL_QUALITY,
    ): Pair<ByteArray, Int> {
        var quality = initialQuality
        var encodedBytes: ByteArray
        
        // Use legacy WEBP for maximum compatibility with WhatsApp's decoder on all Android versions
        val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

        do {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(format, quality, outputStream)
            encodedBytes = outputStream.toByteArray()

            if (encodedBytes.size <= maxFileSize || quality <= MIN_QUALITY) {
                break
            }

            quality -= QUALITY_STEP
        } while (quality >= MIN_QUALITY)

        return encodedBytes to quality
    }
}
