package com.akshaykzi.stickerferry.data.telegram

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Telegram Bot API client.
 *
 * This client handles communication with the Telegram Bot API.
 * It includes rate limiting, retry logic, and proper error handling.
 */
@Singleton
class TelegramApiClient @Inject constructor(
    private val config: TelegramConfig,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient(Android) {
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }

        install(HttpRequestRetry) {
            maxRetries = 3
            retryIf { _, response -> response.status.value in 500..599 }
            delayMillis { retryCount ->
                retryCount * 1000L
            }
        }
    }

    private suspend inline fun <reified T> execute(method: String, block: HttpRequestBuilder.() -> Unit = {}): T {
        val token = config.getBotToken()
        if (token.isBlank()) {
            throw TelegramApiException("Bot token not configured")
        }

        val response: HttpResponse = httpClient.get("https://api.telegram.org/bot$token/$method", block)

        if (!response.status.isSuccess()) {
            throw TelegramApiException("HTTP ${response.status.value}: ${response.status.description}")
        }

        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject
        val ok = root["ok"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

        if (!ok) {
            val desc = root["description"]?.jsonPrimitive?.content ?: "Unknown error"
            throw TelegramApiException(desc)
        }

        val result = root["result"] ?: throw TelegramApiException("Empty response")
        return json.decodeFromJsonElement(result)
    }

    /**
     * Gets sticker set information from Telegram Bot API.
     *
     * @param setName Name of the sticker set
     * @return Sticker set information
     * @throws TelegramApiException if the API request fails
     */
    suspend fun getStickerSet(setName: String): TelegramStickerSetResponse {
        return execute("getStickerSet") {
            parameter("name", setName)
        }
    }

    /**
     * Gets file information from Telegram Bot API.
     *
     * @param fileId File identifier
     * @return File information
     * @throws TelegramApiException if the API request fails
     */
    suspend fun getFile(fileId: String): TelegramFileResponse {
        return execute("getFile") {
            parameter("file_id", fileId)
        }
    }

    /**
     * Downloads file bytes from Telegram servers.
     *
     * @param filePath File path from getFile response
     * @return File bytes
     * @throws TelegramApiException if the download fails
     */
    suspend fun downloadFile(filePath: String): ByteArray {
        val token = config.getBotToken()
        if (token.isBlank()) {
            throw TelegramApiException("Bot token not configured")
        }

        return try {
            httpClient.get("https://api.telegram.org/file/bot$token/$filePath").body()
        } catch (e: Exception) {
            throw TelegramApiException("Failed to download file: ${e.message}", e)
        }
    }

    /**
     * Tests the bot token by getting bot information.
     *
     * @return Bot information
     * @throws TelegramApiException if the token is invalid
     */
    suspend fun getMe(): TelegramBotInfo {
        return execute("getMe")
    }

    /**
     * Closes the HTTP client.
     */
    fun close() {
        httpClient.close()
    }
}

/**
 * Exception thrown when a Telegram API request fails.
 */
class TelegramApiException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Sticker set response from getStickerSet.
 */
@kotlinx.serialization.Serializable
data class TelegramStickerSetResponse(
    val name: String,
    val title: String,
    val is_animated: Boolean = false,
    val is_video: Boolean = false,
    val stickers: List<TelegramStickerDto> = emptyList(),
    val thumbnail: TelegramThumbnailDto? = null,
)

/**
 * Sticker DTO from Telegram API.
 */
@kotlinx.serialization.Serializable
data class TelegramStickerDto(
    val file_id: String,
    val file_unique_id: String,
    val type: String = "regular",
    val width: Int,
    val height: Int,
    val is_animated: Boolean = false,
    val is_video: Boolean = false,
    val emoji: String? = null,
    val set_name: String? = null,
    val thumbnail: TelegramThumbnailDto? = null,
    val file_size: Int = 0,
)

/**
 * Thumbnail DTO from Telegram API.
 */
@kotlinx.serialization.Serializable
data class TelegramThumbnailDto(
    val file_id: String,
    val file_unique_id: String,
    val file_size: Int = 0,
    val width: Int,
    val height: Int,
)

/**
 * File response from getFile.
 */
@kotlinx.serialization.Serializable
data class TelegramFileResponse(
    val file_id: String,
    val file_unique_id: String,
    val file_size: Int = 0,
    val file_path: String,
)

/**
 * Bot information from getMe.
 */
@kotlinx.serialization.Serializable
data class TelegramBotInfo(
    val id: Long,
    val is_bot: Boolean,
    val first_name: String,
    val username: String?,
    val can_join_groups: Boolean = false,
    val can_read_all_group_messages: Boolean = false,
    val supports_inline_queries: Boolean = false,
    val can_read_messages: Boolean = false,
)