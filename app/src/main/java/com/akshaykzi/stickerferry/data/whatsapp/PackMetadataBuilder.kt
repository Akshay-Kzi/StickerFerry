package com.akshaykzi.stickerferry.data.whatsapp

import android.content.Context
import com.akshaykzi.stickerferry.domain.model.Sticker
import com.akshaykzi.stickerferry.domain.model.StickerPack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builder for WhatsApp sticker pack metadata.
 *
 * This class helps construct and validate sticker pack metadata
 * before handing off to WhatsApp.
 */
@Singleton
class PackMetadataBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val MAX_STICKERS = 30
        private const val MIN_STICKERS = 3
        private const val MAX_EMOJIS_PER_STICKER = 3
    }

    /**
     * Builds a complete StickerPack with all required metadata.
     *
     * @param identifier Unique pack identifier
     * @param name Display name
     * @param publisher Publisher name
     * @param stickers List of stickers
     * @param trayImageFile Tray icon filename
     * @param animatedPack Whether the pack is animated
     * @param publisherWebsite Optional publisher website
     * @param privacyPolicyUrl Optional privacy policy URL
     * @param licenseAgreementUrl Optional license agreement URL
     * @return Result containing the built pack or validation errors
     */
    fun buildPack(
        identifier: String,
        name: String,
        publisher: String,
        stickers: List<Sticker>,
        trayImageFile: String = "tray_icon.webp",
        animatedPack: Boolean = false,
        publisherWebsite: String? = null,
        privacyPolicyUrl: String? = null,
        licenseAgreementUrl: String? = null,
    ): Result<StickerPack> {
        val errors = validateInput(
            identifier = identifier,
            name = name,
            publisher = publisher,
            stickers = stickers,
        )

        if (errors.isNotEmpty()) {
            return Result.failure(
                PackValidationException("Pack validation failed", errors)
            )
        }

        val pack = StickerPack(
            identifier = identifier,
            name = name,
            publisher = publisher,
            stickers = stickers,
            trayImageFile = trayImageFile,
            animatedPack = animatedPack,
            publisherWebsite = publisherWebsite,
            privacyPolicyUrl = privacyPolicyUrl,
            licenseAgreementUrl = licenseAgreementUrl,
        )

        return Result.success(pack)
    }

    /**
     * Validates input for building a sticker pack.
     */
    private fun validateInput(
        identifier: String,
        name: String,
        publisher: String,
        stickers: List<Sticker>,
    ): List<String> {
        val errors = mutableListOf<String>()

        // Validate identifier
        if (identifier.isBlank()) {
            errors.add("Pack identifier cannot be blank")
        } else if (!identifier.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            errors.add("Pack identifier can only contain alphanumeric characters and underscores")
        }

        // Validate name
        if (name.isBlank()) {
            errors.add("Pack name cannot be blank")
        } else if (name.length > 128) {
            errors.add("Pack name cannot exceed 128 characters")
        }

        // Validate publisher
        if (publisher.isBlank()) {
            errors.add("Publisher name cannot be blank")
        } else if (publisher.length > 128) {
            errors.add("Publisher name cannot exceed 128 characters")
        }

        // Validate stickers count
        if (stickers.size < MIN_STICKERS) {
            errors.add("Pack must contain at least $MIN_STICKERS stickers (currently ${stickers.size})")
        }
        if (stickers.size > MAX_STICKERS) {
            errors.add("Pack cannot contain more than $MAX_STICKERS stickers (currently ${stickers.size})")
        }

        // Validate individual stickers
        stickers.forEachIndexed { index, sticker ->
            val stickerErrors = validateSticker(sticker)
            stickerErrors.forEach { error ->
                errors.add("Sticker ${index + 1}: $error")
            }
        }

        return errors
    }

    /**
     * Validates a single sticker.
     */
    private fun validateSticker(sticker: Sticker): List<String> {
        val errors = mutableListOf<String>()

        if (sticker.fileName.isBlank()) {
            errors.add("Filename cannot be blank")
        }

        if (sticker.emojis.size > MAX_EMOJIS_PER_STICKER) {
            errors.add("Too many emojis (max $MAX_EMOJIS_PER_STICKER, got ${sticker.emojis.size})")
        }

        return errors
    }

    /**
     * Generates a unique pack identifier from a name.
     */
    fun generatePackIdentifier(name: String): String {
        return name
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(64)
    }

    /**
     * Sanitizes a pack name for display.
     */
    fun sanitizePackName(name: String): String {
        return name
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(128)
    }

    /**
     * Sanitizes a publisher name.
     */
    fun sanitizePublisherName(publisher: String): String {
        return publisher
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(128)
    }
}

/**
 * Exception thrown when pack validation fails.
 */
class PackValidationException(
    message: String,
    val errors: List<String>,
) : Exception(message)