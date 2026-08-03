package com.akshaykzi.stickerferry.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Represents a WhatsApp-compatible sticker pack.
 *
 * @param identifier Unique string identifier for the pack
 * @param name Display name of the pack
 * @param publisher Name of the pack publisher
 * @param stickers List of stickers in this pack
 * @param trayImageFile Filename of the tray icon (96x96 WebP, ≤50KB)
 * @param animatedPack Whether this pack contains animated stickers
 * @param addedToWhatsApp Whether this pack has been successfully added to WhatsApp
 * @param publisherWebsite Optional publisher website URL
 * @param privacyPolicyUrl Optional privacy policy URL
 * @param licenseAgreementUrl Optional license agreement URL
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
@Serializable
data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String,
    val stickers: List<Sticker>,
    val trayImageFile: String,
    val animatedPack: Boolean = false,
    val addedToWhatsApp: Boolean = false,
    val publisherWebsite: String? = null,
    val privacyPolicyUrl: String? = null,
    val licenseAgreementUrl: String? = null,
) {
    companion object {
        const val MIN_STICKERS = 3
        const val MAX_STICKERS = 30
        const val TRAY_ICON_SIZE = 96
        const val TRAY_ICON_MAX_SIZE_BYTES = 50 * 1024 // 50 KB
    }

    /**
     * Validates the sticker pack against WhatsApp constraints.
     * Returns a list of validation errors, empty if valid.
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (stickers.size < MIN_STICKERS) {
            errors.add("Pack must contain at least $MIN_STICKERS stickers (currently ${stickers.size})")
        }

        if (stickers.size > MAX_STICKERS) {
            errors.add("Pack must contain at most $MAX_STICKERS stickers (currently ${stickers.size})")
        }

        stickers.forEachIndexed { index, sticker ->
            val stickerErrors = sticker.validate()
            stickerErrors.forEach { error ->
                errors.add("Sticker ${index + 1}: $error")
            }
        }

        return errors
    }
}