package com.akshaykzi.stickerferry.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Represents a single sticker within a pack.
 *
 * @param fileName Filename of the sticker (WebP format)
 * @param emojis List of emoji tags associated with this sticker (max 3)
 * @param isAnimated Whether this is an animated sticker
 * @param isVideo Whether this sticker originated from a video source
 * @param originalFileName Original filename before conversion (for tracking)
 * @param conversionState Current state of the conversion process
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
@Serializable
data class Sticker(
    val fileName: String,
    val emojis: List<String> = emptyList(),
    val isAnimated: Boolean = false,
    val isVideo: Boolean = false,
    val originalFileName: String? = null,
    val conversionState: ConversionState = ConversionState.Queued,
    val fileId: String = "",
    val fileUniqueId: String = "",
    val localCachePath: String? = null,
) {
    companion object {
        const val MAX_EMOJIS = 3

        // WhatsApp static sticker constraints
        const val STATIC_WIDTH = 512
        const val STATIC_HEIGHT = 512
        const val STATIC_MAX_SIZE_BYTES = 100 * 1024 // 100 KB

        // WhatsApp animated sticker constraints
        const val ANIMATED_WIDTH = 512
        const val ANIMATED_HEIGHT = 512
        const val ANIMATED_MAX_SIZE_BYTES = 500 * 1024 // 500 KB
        const val ANIMATED_MAX_DURATION_MS = 10_000 // 10 seconds
        const val ANIMATED_MAX_FRAMES = 300
        const val ANIMATED_MIN_FRAME_DURATION_MS = 8 // 8ms minimum per frame
    }

    /**
     * Validates the sticker against WhatsApp constraints.
     * Returns a list of validation errors, empty if valid.
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (emojis.size > MAX_EMOJIS) {
            errors.add("Too many emojis (max $MAX_EMOJIS, got ${emojis.size})")
        }

        if (fileName.isBlank()) {
            errors.add("Sticker filename cannot be blank")
        }

        return errors
    }

    /**
     * Returns the appropriate width/height based on animation type.
     */
    fun getTargetSize(): Pair<Int, Int> {
        return if (isAnimated || isVideo) {
            ANIMATED_WIDTH to ANIMATED_HEIGHT
        } else {
            STATIC_WIDTH to STATIC_HEIGHT
        }
    }

    /**
     * Returns the maximum allowed file size in bytes based on animation type.
     */
    fun getMaxFileSize(): Long {
        return if (isAnimated || isVideo) {
            ANIMATED_MAX_SIZE_BYTES.toLong()
        } else {
            STATIC_MAX_SIZE_BYTES.toLong()
        }
    }
}