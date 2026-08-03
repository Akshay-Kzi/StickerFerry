package com.akshaykzi.stickerferry.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a Telegram sticker pack fetched from the Bot API.
 *
 * @param name Pack name (identifier)
 * @param title Display title
 * @param isAnimated Whether the pack contains animated stickers
 * @param isVideo Whether the pack contains video stickers
 * @param stickers List of stickers in the pack
 */
@Serializable
data class TelegramStickerPack(
    val name: String,
    val title: String,
    @SerialName("is_animated")
    val isAnimated: Boolean = false,
    @SerialName("is_video")
    val isVideo: Boolean = false,
    val stickers: List<TelegramSticker> = emptyList(),
)

/**
 * Represents a single sticker from the Telegram Bot API.
 *
 * @param fileId Unique identifier for the file
 * @param fileUniqueId Unique identifier for the file (across updates)
 * @param type Sticker type (usually "regular", "mask", or "custom_emoji")
 * @param width Sticker width
 * @param height Sticker height
 * @param isAnimated Whether the sticker is animated
 * @param isVideo Whether the sticker is a video
 * @param emoji Associated emoji
 * @param setName Name of the sticker set
 * @param thumbnail Optional thumbnail
 * @param premiumAnimation Optional premium animation file
 * @param maskPosition Optional mask position
 * @param customEmojiId Optional custom emoji identifier
 * @param needsRepainting Whether the sticker needs repainting
 * @param fileSize File size in bytes
 */
@Serializable
data class TelegramSticker(
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val fileUniqueId: String,
    val type: String = "regular",
    val width: Int,
    val height: Int,
    @SerialName("is_animated")
    val isAnimated: Boolean = false,
    @SerialName("is_video")
    val isVideo: Boolean = false,
    val emoji: String? = null,
    @SerialName("set_name")
    val setName: String? = null,
    val thumbnail: TelegramThumbnail? = null,
    @SerialName("premium_animation")
    val premiumAnimation: TelegramFile? = null,
    @SerialName("mask_position")
    val maskPosition: TelegramMaskPosition? = null,
    @SerialName("custom_emoji_id")
    val customEmojiId: String? = null,
    @SerialName("needs_repainting")
    val needsRepainting: Boolean = false,
    @SerialName("file_size")
    val fileSize: Int = 0,
)

/**
 * Telegram file information from getFile response.
 *
 * @param fileId Unique identifier for the file
 * @param fileUniqueId Unique identifier for the file
 * @param fileSize File size in bytes
 * @param filePath File path to download from Telegram servers
 */
@Serializable
data class TelegramFile(
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val fileUniqueId: String,
    @SerialName("file_size")
    val fileSize: Int = 0,
    @SerialName("file_path")
    val filePath: String,
)

/**
 * Telegram thumbnail information.
 *
 * @param fileId File identifier of the thumbnail
 * @param fileUniqueId Unique identifier of the thumbnail
 * @param fileSize File size in bytes
 * @param width Thumbnail width
 * @param height Thumbnail height
 */
@Serializable
data class TelegramThumbnail(
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val fileUniqueId: String,
    @SerialName("file_size")
    val fileSize: Int = 0,
    val width: Int,
    val height: Int,
)

/**
 * Telegram mask position for mask stickers.
 */
@Serializable
data class TelegramMaskPosition(
    val point: String,
    @SerialName("x_shift")
    val xShift: Float,
    @SerialName("y_shift")
    val yShift: Float,
    val scale: Float,
)