package com.akshaykzi.stickerferry.data.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.akshaykzi.stickerferry.data.telegram.TelegramRepository
import com.akshaykzi.stickerferry.domain.model.Sticker
import com.akshaykzi.stickerferry.domain.model.ConversionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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

        /**
         * Parses a .tgs payload synchronously. The async LottieCompositionFactory
         * variant must NOT be used here: its task can deliver the result to the same
         * listener twice (immediately + via its queued main-thread notification),
         * which crashes "Already resumed". We are on Dispatchers.IO already, so the
         * sync parse is both safe and simpler.
         */
        private fun parseLottie(json: String, cacheKey: String): LottieComposition {
            val result = LottieCompositionFactory.fromJsonStringSync(json, cacheKey)
            return result.value ?: throw ConversionException(
                "Failed to parse Lottie animation: ${result.exception?.message}",
                result.exception,
            )
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
        val perFrameMs = (durationMs / totalFrames).coerceAtLeast(8)

        val lottieDrawable = LottieDrawable().apply {
            setComposition(composition)
        }

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

        // Encode every frame with Android's platform WebP encoder (the same encoder
        // that produces the app's WhatsApp-accepted static stickers), then assemble
        // the animated container ourselves. The webp-android library's muxer output
        // was rejected by WhatsApp ("problem with the sticker pack").
        val framePayloads = ArrayList<ByteArray>(totalFrames)
        for (i in 0 until totalFrames) {
            val currentTimeMs = i * frameDurationMs
            lottieDrawable.progress = (currentTimeMs.toFloat() / durationMs).coerceAtMost(1.0f)

            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
            lottieDrawable.draw(canvas)

            framePayloads.add(encodeFrame(bitmap, quality))
        }
        bitmap.recycle()

        outputFile.writeBytes(
            AnimatedWebPContainer.mux(framePayloads, frameDurationMs = perFrameMs)
        )

        return totalFrames
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
