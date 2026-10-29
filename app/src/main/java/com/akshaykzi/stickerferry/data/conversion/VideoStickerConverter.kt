package com.akshaykzi.stickerferry.data.conversion

import android.content.Context
import android.graphics.Bitmap
import com.akshaykzi.stickerferry.data.telegram.TelegramRepository
import com.akshaykzi.stickerferry.domain.model.Sticker
import com.akshaykzi.stickerferry.domain.model.ConversionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wseemann.media.FFmpegMediaMetadataRetriever
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converter for Telegram video .webm stickers.
 *
 * Frame extraction uses FFmpegMediaMetadataRetriever (a bundled ffmpeg with libvpx)
 * because Android's MediaMetadataRetriever cannot decode Telegram's VP9+alpha webm
 * stickers frame-accurately - on many devices it returns the same frame for every
 * timestamp or index, which silently produced static sticker packs.
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
        var retriever: FFmpegMediaMetadataRetriever? = null
        var tempFile: File? = null
        try {
            // 1. Download .webm file
            val bytes = telegramRepository.downloadSticker(sticker.fileId, sticker.fileUniqueId)
            tempFile = File(context.cacheDir, "${sticker.fileUniqueId}.webm")
            tempFile.writeBytes(bytes)

            // 2. Setup Retriever
            retriever = FFmpegMediaMetadataRetriever()
            retriever.setDataSource(tempFile.absolutePath)

            val durationStr = retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)
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
            retriever?.release()
            tempFile?.delete()
        }
    }

    private fun extractAndEncode(
        retriever: FFmpegMediaMetadataRetriever,
        outputFile: File,
        fps: Int,
        quality: Float,
        scale: Float,
        durationMs: Long
    ): Int {
        val frameIntervalUs = 1_000_000L / fps
        val perFrameMs = (frameIntervalUs / 1000).coerceAtLeast(8)

        // Encode every frame with Android's platform WebP encoder (the same encoder
        // that produces the app's WhatsApp-accepted static stickers), then assemble
        // the animated container ourselves.
        val framePayloads = ArrayList<ByteArray>()
        for (timeUs in 0 until (durationMs * 1000) step frameIntervalUs) {
            try {
                val frame = retriever.getFrameAtTime(timeUs, FFmpegMediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    val processedFrame = ConversionUtils.resizeAndPad(frame, scaleFactor = scale)
                    framePayloads.add(encodeFrame(processedFrame, quality))
                }
            } catch (_: Exception) {
                // Skip problematic frames
            }
        }

        if (framePayloads.isEmpty()) {
            throw ConversionException("No valid frames could be extracted from video")
        }

        // A decoder that repeats one frame would ship a still image disguised as an
        // animation; fail loudly instead.
        val first = framePayloads[0]
        val distinctFrames = framePayloads.count { !it.contentEquals(first) } + 1
        if (distinctFrames < 2) {
            throw ConversionException(
                "This device could not decode the video into distinct frames. Try again or use another pack."
            )
        }

        outputFile.writeBytes(
            AnimatedWebPContainer.mux(framePayloads, frameDurationMs = perFrameMs)
        )

        return framePayloads.size
    }

    /** Encodes one RGBA frame as a single-frame lossy WebP via the platform encoder. */
    private fun encodeFrame(bitmap: Bitmap, quality: Float): ByteArray {
        val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(format, quality.toInt().coerceIn(10, 95), outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Extracts the first frame of a video sticker to a Bitmap.
     * Useful for tray icon generation.
     */
    suspend fun extractFirstFrame(sticker: Sticker): Result<Bitmap> = withContext(Dispatchers.IO) {
        var retriever: FFmpegMediaMetadataRetriever? = null
        var tempFile: File? = null
        try {
            val bytes = telegramRepository.downloadSticker(sticker.fileId, sticker.fileUniqueId)
            tempFile = File(context.cacheDir, "${sticker.fileUniqueId}_tray.webm")
            tempFile.writeBytes(bytes)

            retriever = FFmpegMediaMetadataRetriever()
            retriever.setDataSource(tempFile.absolutePath)
            val frame = retriever.getFrameAtTime(0, FFmpegMediaMetadataRetriever.OPTION_CLOSEST)
                ?: throw ConversionException("Failed to extract frame at time 0")

            Result.success(frame)
        } catch (e: Exception) {
            Result.failure(ConversionException("Failed to extract first frame: ${e.message}", e))
        } finally {
            retriever?.release()
            tempFile?.delete()
        }
    }
}
