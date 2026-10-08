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
            var packName = extractPackName(input)
                ?: return Result.failure(
                    IllegalArgumentException("Invalid Telegram sticker pack link: $input")
                )

            // NUCLEAR CLEANING:
            // Telegram API only accepts the base pack name.
            // If we have something like "packname_static_1" or "packname_anim_2",
            // we strip everything from the first occurrence of "_static" or "_anim".
            packName = packName.split("_static")[0].split("_anim")[0].trim()

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
        val domainStickers = telegramPack.stickers.map { telegramSticker ->
            val rawEmoji = telegramSticker.emoji
            val emojiList = if (!rawEmoji.isNullOrBlank()) {
                listOf(rawEmoji)
            } else {
                listOf("😀")
            }
            Sticker(
                fileName = "${telegramSticker.fileUniqueId}.webp",
                emojis = emojiList.take(3),
                isAnimated = telegramSticker.isAnimated,
                isVideo = telegramSticker.isVideo,
                originalFileName = telegramSticker.fileId,
                fileId = telegramSticker.fileId,
                fileUniqueId = telegramSticker.fileUniqueId,
            )
        }

        if (domainStickers.isEmpty()) {
            throw IllegalArgumentException("This pack contains no stickers.")
        }

        // WhatsApp requirement: A pack must be all-static or all-animated.
        // Group stickers by their animated status.
        val groupedStickers = domainStickers.groupBy { it.isAnimated || it.isVideo }
        
        val resultPacks = mutableListOf<StickerPack>()
        
        groupedStickers.forEach { (isAnimated, stickers) ->
            val chunks = stickers.chunked(StickerPack.MAX_STICKERS)
            val typePrefix = if (groupedStickers.size > 1) {
                if (isAnimated) " (Animated)" else " (Static)"
            } else ""
            
            chunks.forEachIndexed { index, chunkStickers ->
                val partSuffix = if (chunks.size > 1) " - ${index + 1}" else ""
                val idSuffix = if (isAnimated) "_anim" else "_static"
                val chunkIdSuffix = if (chunks.size > 1) "_${index + 1}" else ""
                
                resultPacks.add(
                    StickerPack(
                        identifier = "${telegramPack.name}$idSuffix$chunkIdSuffix",
                        name = "${telegramPack.title}$typePrefix$partSuffix",
                        publisher = "Telegram",
                        stickers = chunkStickers,
                        trayImageFile = "tray_icon.png",
                        animatedPack = isAnimated,
                    )
                )
            }
        }
        
        return resultPacks
    }
}
