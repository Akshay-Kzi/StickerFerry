package com.akshaykzi.stickerferry.domain.usecase

import com.akshaykzi.stickerferry.data.telegram.TelegramRepository
import com.akshaykzi.stickerferry.domain.model.Sticker
import com.akshaykzi.stickerferry.domain.model.StickerPack
import com.akshaykzi.stickerferry.domain.model.TelegramStickerPack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for fetching a sticker pack from Telegram.
 *
 * This use case handles:
 * - Parsing the Telegram sticker pack link
 * - Fetching the pack metadata from the Bot API
 * - Converting Telegram stickers to domain models
 */
@Singleton
class FetchPackUseCase @Inject constructor(
    private val telegramRepository: TelegramRepository,
) {
    /**
     * Fetches a sticker pack from Telegram by its link or name.
     *
     * @param input Telegram sticker pack link or pack name
     * @return Result containing the list of sticker packs (split if > 30)
     */
    suspend operator fun invoke(input: String): Result<List<StickerPack>> {
        return try {
            // Extract pack name from various input formats
            val packName = extractPackName(input)
                ?: return Result.failure(
                    IllegalArgumentException("Invalid Telegram sticker pack link: $input")
                )

            // Fetch pack from Telegram API
            val telegramPack = telegramRepository.getStickerSet(packName)

            // Convert and split to domain models
            val stickerPacks = convertAndSplitToDomain(telegramPack)

            Result.success(stickerPacks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extracts the pack name from various Telegram sticker pack formats.
     *
     * Supported formats:
     * - https://t.me/addstickers/<pack_name>
     * - tg://addstickers?set=<pack_name>
     * - <pack_name> (plain text)
     *
     * @return The extracted pack name, or null if invalid
     */
    fun extractPackName(input: String): String? {
        val trimmedInput = input.trim()

        // Handle t.me/addstickers/<pack_name> format
        val httpsPattern = Regex("""https?://t\.me/addstickers/([a-zA-Z0-9_]+)""")
        httpsPattern.find(trimmedInput)?.let { match ->
            return match.groupValues[1]
        }

        // Handle tg://addstickers?set=<pack_name> format
        val deepLinkPattern = Regex("""tg://addstickers\?set=([a-zA-Z0-9_]+)""")
        deepLinkPattern.find(trimmedInput)?.let { match ->
            return match.groupValues[1]
        }

        // Handle plain pack name (alphanumeric and underscores only)
        val plainNamePattern = Regex("""^[a-zA-Z0-9_]+$""")
        if (plainNamePattern.matches(trimmedInput)) {
            return trimmedInput
        }

        return null
    }

    /**
     * Converts and splits a Telegram sticker pack into domain models.
     */
    private fun convertAndSplitToDomain(telegramPack: TelegramStickerPack): List<StickerPack> {
        // Filter: WhatsApp only supports static stickers in the current version of StickerFerry.
        // Animated/Video stickers (.tgs/.webm) are skipped to prevent "Corrupted Pack" errors.
        val staticStickers = telegramPack.stickers.filter { !it.isAnimated && !it.isVideo }.map { telegramSticker ->
            Sticker(
                fileName = "${telegramSticker.fileUniqueId}.webp",
                emojis = if (!telegramSticker.emoji.isNullOrBlank()) {
                    listOf(telegramSticker.emoji)
                } else {
                    listOf("😀")
                },
                isAnimated = false,
                isVideo = false,
                originalFileName = telegramSticker.fileId,
                fileId = telegramSticker.fileId,
                fileUniqueId = telegramSticker.fileUniqueId,
            )
        }

        if (staticStickers.isEmpty()) {
            throw IllegalArgumentException("This pack contains only animated/video stickers, which are not yet supported.")
        }

        // Split into chunks of 30
        val chunks = staticStickers.chunked(StickerPack.MAX_STICKERS)
        
        return chunks.mapIndexed { index, stickers ->
            val partSuffix = if (chunks.size > 1) " - ${index + 1}" else ""
            val idSuffix = if (chunks.size > 1) "_${index + 1}" else ""
            
            StickerPack(
                identifier = "${telegramPack.name}$idSuffix",
                name = "${telegramPack.title}$partSuffix",
                publisher = "Telegram",
                stickers = stickers,
                trayImageFile = "tray_icon.png",
                animatedPack = telegramPack.isAnimated || telegramPack.isVideo,
            )
        }
    }
}
