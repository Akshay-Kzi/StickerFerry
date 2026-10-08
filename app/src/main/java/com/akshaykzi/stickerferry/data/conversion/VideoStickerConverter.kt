package com.akshaykzi.stickerferry.data.conversion

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.akshaykzi.stickerferry.data.telegram.TelegramRepository
import com.akshaykzi.stickerferry.domain.model.Sticker
import com.akshaykzi.stickerferry.domain.model.ConversionState
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoder
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoderOptions
import com.aureusapps.android.webpandroid.encoder.WebPConfig
import com.aureusapps.android.webpandroid.encoder.WebPMuxAnimParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converter for Telegram video .webm stickers.
 * Uses MediaMetadataRetriever for frame extraction and webp-android for encoding.
 */
@Singleton
class VideoStickerConverter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegramRepository: TelegramRepository,
) {
    companion object {
        private const val MIN_DURATION_MS = 800L
        private const val MAX_DURATION_MS = 10_000L
        private const val MAX_ANIMATED_SIZE_BYTES = 490 * 1024L // Strictly under WhatsApp's 500 KB limit
    }

    private data class PassConfig(val fps: Int, val quality: Float, val scale: Float = 1.0f)

    suspend fun convert(sticker: Sticker, outputDir: File): Result<Sticker> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var tempFile: File? = null
        try {
            // 1. Download .webm file
            val bytes = telegramRepository.downloadSticker(sticker.fileId, sticker.fileUniqueId)
            tempFile = File(context.cacheDir, "${sticker.fileUniqueId}.webm")
            tempFile.writeBytes(bytes)

            // 2. Setup Retriever
            retriever.setDataSource(context, Uri.fromFile(tempFile))
            
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val rawDurationMs = durationStr?.toLong() ?: 800L
            val durationMs = rawDurationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)

            val outputFile = File(outputDir, sticker.fileName)
            val passes = listOf(
                PassConfig(fps = 15, quality = 70f),
                PassConfig(fps = 12, quality = 50f),
                PassConfig(fps = 10, quality = 35f),
                PassConfig(fps = 8, quality = 20f),
                PassConfig(fps = 6, quality = 15f),
                PassConfig(fps = 6, quality = 15f, scale = 0.8f),
                PassConfig(fps = 5, quality = 10f, scale = 0.7f)
            )

            var frameCount = 0
            for (pass in passes) {
                frameCount = extractAndEncode(retriever, outputFile, pass.fps, pass.quality, pass.scale, durationMs)
                if (outputFile.length() <= MAX_ANIMATED_SIZE_BYTES) {
                    break
                }
            }

            if (outputFile.length() > MAX_ANIMATED_SIZE_BYTES) {
                throw ConversionException(
                    "Video sticker size (${outputFile.length()} bytes) exceeds WhatsApp's 500 KB limit even after maximum compression"
                )
            }

            val convertedSticker = sticker.copy(
                conversionState = ConversionState.Done(
                    outputSizeBytes = outputFile.length(),
                    frameCount = frameCount,
                    durationMs = durationMs
                ),
                localCachePath = outputFile.absolutePath
            )

            Result.success(convertedSticker)
        } catch (e: Exception) {
            Result.failure(ConversionException("Failed to convert video sticker: ${e.message}", e))
        } finally {
            retriever.release()
            tempFile?.delete()
        }
    }

    private fun extractAndEncode(
        retriever: MediaMetadataRetriever,
        outputFile: File,
        fps: Int,
        quality: Float,
        scale: Float,
        durationMs: Long
    ): Int {
        val frameIntervalUs = 1_000_000L / fps
        val options = WebPAnimEncoderOptions(
            animParams = WebPMuxAnimParams(loopCount = 0)
        )
        val encoder = WebPAnimEncoder(context, ConversionUtils.TARGET_SIZE, ConversionUtils.TARGET_SIZE, options)
        
        val webPConfig = WebPConfig(
            quality = quality,
            lossless = WebPConfig.COMPRESSION_LOSSY
        )
        encoder.configure(webPConfig)

        var frameCount = 0
        for (timeUs in 0 until (durationMs * 1000) step frameIntervalUs) {
            try {
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    val processedFrame = ConversionUtils.resizeAndPad(frame, scaleFactor = scale)
                    encoder.addFrame(timeUs / 1000, processedFrame)
                    frameCount++
                }
            } catch (_: Exception) {
                // Skip problematic frames
            }
        }

        if (frameCount == 0) {
            encoder.release()
            throw ConversionException("No valid frames could be extracted from video")
        }

        encoder.assemble(durationMs, Uri.fromFile(outputFile))
        encoder.release()

        return frameCount
    }

    /**
     * Extracts the first frame of a video sticker to a Bitmap.
     * Useful for tray icon generation.
     */
    suspend fun extractFirstFrame(sticker: Sticker): Result<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val bytes = telegramRepository.downloadSticker(sticker.fileId, sticker.fileUniqueId)
            val tempFile = File(context.cacheDir, "${sticker.fileUniqueId}_tray.webm")
            tempFile.writeBytes(bytes)

            retriever.setDataSource(context, Uri.fromFile(tempFile))
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: throw ConversionException("Failed to extract frame at time 0")

            tempFile.delete()
            Result.success(frame)
        } catch (e: Exception) {
            Result.failure(ConversionException("Failed to extract first frame: ${e.message}", e))
        } finally {
            retriever.release()
        }
    }
}
