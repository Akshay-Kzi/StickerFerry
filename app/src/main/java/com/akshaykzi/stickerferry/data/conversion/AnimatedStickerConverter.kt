package com.akshaykzi.stickerferry.data.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Converter for Telegram animated .tgs stickers.
 * Optimized for speed and WhatsApp compatibility.
 */
@Singleton
class AnimatedStickerConverter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegramRepository: TelegramRepository,
) {
    companion object {
        private const val MIN_DURATION_MS = 800L
        private const val MAX_DURATION_MS = 10_000L
        private const val MAX_ANIMATED_SIZE_BYTES = 480 * 1024L // 480KB for safety

        // Heuristic quality map: based on duration, pick a starting quality
        // Complex animations (long duration) start at lower quality.
        // Conservative values: WhatsApp rejects animated stickers over 500 KB, and
        // high fps/quality at 512x512 easily exceeds that.
        private fun getStartingQuality(durationMs: Long): Float {
            return when {
                durationMs > 5000 -> 30f
                durationMs > 3000 -> 40f
                else -> 55f
            }
        }

        private fun getStartingFps(durationMs: Long): Int {
            return when {
                durationMs > 5000 -> 10
                durationMs > 3000 -> 12
                else -> 15
            }
        }

        private suspend fun parseLottie(json: String, cacheKey: String) = suspendCoroutine<LottieComposition> { continuation ->
            LottieCompositionFactory.fromJsonString(json, cacheKey)
                .addListener { result -> continuation.resume(result) }
                // Without a failure listener the coroutine would never resume and the
                // conversion of this sticker would hang forever.
                .addFailureListener { error ->
                    continuation.resumeWithException(
                        ConversionException("Failed to parse Lottie animation: ${error.message}", error)
                    )
                }
        }
    }

    suspend fun convert(sticker: Sticker, outputDir: File): Result<Sticker> = withContext(Dispatchers.IO) {
        try {
            val bytes = telegramRepository.downloadSticker(sticker.fileId, sticker.fileUniqueId)
            val json = GzipUtils.decompress(bytes)

            val composition = parseLottie(json, sticker.fileUniqueId)

            val durationMs = composition.duration.toLong().coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
            val outputFile = File(outputDir, sticker.fileName)

            // HEURISTIC PASS: Calculate a starting point to avoid multi-pass loops
            var currentFps = getStartingFps(durationMs)
            var currentQuality = getStartingQuality(durationMs)
            var currentScale = 1.0f

            var frameCount = 0
            var attempt = 0
            val maxAttempts = 4

            while (attempt < maxAttempts) {
                frameCount = renderAndEncode(composition, outputFile, currentFps, currentQuality, currentScale, durationMs)

                if (outputFile.length() <= MAX_ANIMATED_SIZE_BYTES) {
                    break
                }

                // Reduce quality aggressively for next attempt
                currentQuality = (currentQuality * 0.65f).coerceAtLeast(15f)
                currentFps = (currentFps * 0.75f).toInt().coerceAtLeast(8)
                when (attempt) {
                    1 -> currentScale = 0.85f // Scale down content on second fail
                    2 -> currentScale = 0.7f  // and harder on third
                }

                attempt++
            }

            if (outputFile.length() > MAX_ANIMATED_SIZE_BYTES) {
                throw ConversionException(
                    "Sticker too complex (${outputFile.length()} bytes). Try a simpler animation."
                )
            }

            Result.success(sticker.copy(
                conversionState = ConversionState.Done(
                    outputSizeBytes = outputFile.length(),
                    frameCount = frameCount,
                    durationMs = durationMs
                ),
                localCachePath = outputFile.absolutePath
            ))
        } catch (e: Exception) {
            Result.failure(ConversionException("Failed to convert: ${e.message}", e))
        }
    }

    private fun renderAndEncode(
        composition: com.airbnb.lottie.LottieComposition,
        outputFile: File,
        fps: Int,
        quality: Float,
        scale: Float,
        durationMs: Long
    ): Int {
        val frameDurationMs = 1000 / fps
        // WhatsApp treats a 1-frame "animation" as invalid; always emit at least 2.
        val totalFrames = maxOf(2, (durationMs / frameDurationMs).toInt())

        val lottieDrawable = LottieDrawable().apply {
            setComposition(composition)
        }

        val options = WebPAnimEncoderOptions(
            animParams = WebPMuxAnimParams(loopCount = 0)
        )
        val encoder = WebPAnimEncoder(context, ConversionUtils.TARGET_SIZE, ConversionUtils.TARGET_SIZE, options)

        // STABLE CONFIG: Force lossy and standard quality
        val webPConfig = WebPConfig(
            quality = quality.coerceIn(10f, 90f),
            lossless = WebPConfig.COMPRESSION_LOSSY
        )
        encoder.configure(webPConfig)

        val bitmap = Bitmap.createBitmap(
            ConversionUtils.TARGET_SIZE,
            ConversionUtils.TARGET_SIZE,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)

        val baseMargin = ConversionUtils.TARGET_SIZE * 0.03125f
        val extraMargin = ConversionUtils.TARGET_SIZE * ((1f - scale) / 2f)
        val margin = (baseMargin + extraMargin).toInt().coerceAtLeast(4)
        val safeSize = ConversionUtils.TARGET_SIZE - (margin * 2)

        lottieDrawable.setBounds(margin, margin, margin + safeSize, margin + safeSize)

        for (i in 0 until totalFrames) {
            val currentTimeMs = i * frameDurationMs
            lottieDrawable.progress = (currentTimeMs.toFloat() / durationMs).coerceAtMost(1.0f)

            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
            lottieDrawable.draw(canvas)

            encoder.addFrame(currentTimeMs.toLong(), bitmap)
        }

        encoder.assemble(durationMs, Uri.fromFile(outputFile))
        encoder.release()
        bitmap.recycle()

        return totalFrames
    }

    suspend fun renderFirstFrame(sticker: Sticker): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val bytes = telegramRepository.downloadSticker(sticker.fileId, sticker.fileUniqueId)
            val json = GzipUtils.decompress(bytes)

            val composition = parseLottie(json, sticker.fileUniqueId)

            val lottieDrawable = LottieDrawable().apply {
                setComposition(composition)
                progress = 0f
            }

            val bitmap = Bitmap.createBitmap(
                ConversionUtils.TARGET_SIZE,
                ConversionUtils.TARGET_SIZE,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            lottieDrawable.setBounds(0, 0, canvas.width, canvas.height)
            lottieDrawable.draw(canvas)

            Result.success(bitmap)
        } catch (e: Exception) {
            Result.failure(ConversionException("Failed to render first frame: ${e.message}", e))
        }
    }
}
