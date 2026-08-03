package com.akshaykzi.stickerferry.data.conversion

import android.content.Context
import com.akshaykzi.stickerferry.data.telegram.TelegramRepository
import com.akshaykzi.stickerferry.domain.model.Sticker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converter for Telegram animated .tgs stickers.
 */
@Singleton
class AnimatedStickerConverter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegramRepository: TelegramRepository,
) {
    suspend fun convert(sticker: Sticker, outputDir: File): Result<Sticker> {
        return try {
            // 1. Download .tgs file
            val bytes = telegramRepository.downloadSticker(sticker.fileId, sticker.fileUniqueId)
            
            // 2. Write to output directory (Placeholder for actual TGS -> Animated WebP conversion)
            // For now, we save it with the intended .webp name but it contains raw TGS/JSON
            // REAL implementation would use rlottie + libwebp to render frames
            val outputFile = File(outputDir, sticker.fileName)
            outputFile.writeBytes(bytes)

            val convertedSticker = sticker.copy(
                conversionState = com.akshaykzi.stickerferry.domain.model.ConversionState.Done(
                    outputSizeBytes = outputFile.length(),
                ),
                localCachePath = outputFile.absolutePath
            )

            Result.success(convertedSticker)
        } catch (e: Exception) {
            Result.failure(ConversionException("Failed to convert animated sticker: ${e.message}", e))
        }
    }
}
