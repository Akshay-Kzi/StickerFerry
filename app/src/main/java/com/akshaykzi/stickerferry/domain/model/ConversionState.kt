package com.akshaykzi.stickerferry.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Represents the state of a sticker conversion process.
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
@Serializable
sealed class ConversionState {
    /**
     * Sticker is waiting to be converted.
     */
    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    @Serializable
    data object Queued : ConversionState()

    /**
     * Sticker is currently being converted.
     *
     * @param progress Conversion progress from 0.0 to 1.0
     */
    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    @Serializable
    data class Converting(val progress: Float = 0f) : ConversionState() {
        init {
            require(progress in 0f..1f) { "Progress must be between 0.0 and 1.0" }
        }
    }

    /**
     * Conversion completed successfully.
     *
     * @param outputSizeBytes Size of the converted file in bytes
     * @param frameCount Number of frames (for animated stickers)
     * @param durationMs Duration in milliseconds (for animated stickers)
     */
    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    @Serializable
    data class Done(
        val outputSizeBytes: Long = 0L,
        val frameCount: Int = 0,
        val durationMs: Long = 0L,
    ) : ConversionState()

    /**
     * Conversion failed.
     *
     * @param reason Human-readable description of the failure
     */
    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    @Serializable
    data class Failed(val reason: String) : ConversionState()

    /**
     * Returns true if the conversion is complete (either success or failure).
     */
    fun isTerminal(): Boolean = this is Done || this is Failed

    /**
     * Returns true if the conversion is in progress.
     */
    fun isInProgress(): Boolean = this is Converting

    /**
     * Returns the progress as a percentage string (e.g., "45%").
     */
    fun getProgressString(): String {
        return when (this) {
            is Queued -> "Queued"
            is Converting -> "${(progress * 100).toInt()}%"
            is Done -> "Complete"
            is Failed -> "Failed"
        }
    }
}