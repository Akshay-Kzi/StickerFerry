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
    companion object {
        // Matches StickerFerry's own part suffixes at the end of a pack identifier:
        // "_static", "_anim", "_static_1", "_anim_12", etc. Never matches mid-name
        // occurrences (e.g. a genuine Telegram pack called "my_animations").
        private val PART_SUFFIX_REGEX = Regex("_(static|anim)(_[0-9]+)?$")
    }

    /**
     * Fetches a sticker pack from Telegram by its link or name.
     *
     * @param input Telegram sticker pack link or pack name
     * @return Result containing the list of sticker packs (split if > 30)
     */
    suspend operator fun invoke(input: String): Result<List<StickerPack>> {
        val rawName = extractPackName(input)
            ?: return Result.failure(
                IllegalArgumentException("Invalid Telegram sticker pack link: $input")
            )

        // Internal part identifiers look like "packname_static_1" / "packname_anim_2",
        // but the Telegram API only accepts the base set name. Strip our own suffix —
        // only when it appears at the END of the name, so real pack names that merely
        // contain "_anim"/"_static" (e.g. "my_animations") are not mangled.
        val baseName = PART_SUFFIX_REGEX.replace(rawName, "")

        return try {
            fetchAndSplit(rawName)
        } catch (firstError: Exception) {
            // The raw name may itself have been a suffixed part identifier whose base
            // set exists; retry once with the stripped base name before giving up.
            if (baseName != rawName) {
                try {
                    fetchAndSplit(baseName)
                } catch (secondError: Exception) {
                    Result.failure(firstError)
                }
            } else {
                Result.failure(firstError)
            }
        }
    }

    private suspend fun fetchAndSplit(packName: String): Result<List<StickerPack>> {
        val telegramPack = telegramRepository.getStickerSet(packName)
        return Result.success(convertAndSplitToDomain(telegramPack))
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
            val chunks = chunkForWhatsApp(stickers)
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

    /**
     * Splits stickers into parts of at most MAX_STICKERS (30), balancing the part
     * sizes so the final part never has fewer stickers than WhatsApp's MIN_STICKERS (3).
     * e.g. 62 stickers -> 21/21/20 instead of 30/30/2, because WhatsApp rejects a
     * 2-sticker pack with "at least 3 stickers" validation.
     */
    private fun chunkForWhatsApp(stickers: List<Sticker>): List<List<Sticker>> {
        if (stickers.size <= StickerPack.MAX_STICKERS) return listOf(stickers)
        val partCount = (stickers.size + StickerPack.MAX_STICKERS - 1) / StickerPack.MAX_STICKERS
        val perPart = (stickers.size + partCount - 1) / partCount
        return stickers.chunked(perPart)
    }
}
