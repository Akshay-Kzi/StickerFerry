package com.akshaykzi.stickerferry.data.telegram

import android.content.Context
import android.util.LruCache
import com.akshaykzi.stickerferry.domain.model.TelegramStickerPack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Telegram sticker operations.
 *
 * This repository handles:
 * - Fetching sticker pack metadata
 * - Downloading and caching sticker files
 * - Managing file cache to avoid re-downloads
 */
@Singleton
class TelegramRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: TelegramApiClient,
) {
    private val memoryCache = LruCache<String, ByteArray>(50) // 50 items max
    private val fileCacheDir: File by lazy {
        File(context.cacheDir, "telegram_stickers").apply {
            mkdirs()
        }
    }

    /**
     * Gets a sticker set from Telegram.
     *
     * @param setName Name of the sticker set
     * @return Sticker set information
     */
    suspend fun getStickerSet(setName: String): TelegramStickerPack {
        return withContext(Dispatchers.IO) {
            val data = apiClient.getStickerSet(setName)

            TelegramStickerPack(
                name = data.name,
                title = data.title,
                isAnimated = data.is_animated,
                isVideo = data.is_video,
                stickers = data.stickers.map { sticker ->
                    com.akshaykzi.stickerferry.domain.model.TelegramSticker(
                        fileId = sticker.file_id,
                        fileUniqueId = sticker.file_unique_id,
                        type = sticker.type,
                        width = sticker.width,
                        height = sticker.height,
                        isAnimated = sticker.is_animated,
                        isVideo = sticker.is_video,
                        emoji = sticker.emoji,
                        setName = sticker.set_name,
                        fileSize = sticker.file_size,
                    )
                },
            )
        }
    }

    /**
     * Downloads a sticker file.
     *
     * @param fileId Telegram file ID
     * @param fileUniqueId Unique file ID for caching
     * @return Downloaded file bytes
     */
    suspend fun downloadSticker(fileId: String, fileUniqueId: String): ByteArray {
        return withContext(Dispatchers.IO) {
            // Check memory cache first
            val cached = memoryCache.get(fileUniqueId)
            if (cached != null) {
                return@withContext cached
            }

            // Check disk cache
            val cachedFile = getCachedFile(fileUniqueId)
            if (cachedFile.exists()) {
                val bytes = cachedFile.readBytes()
                memoryCache.put(fileUniqueId, bytes)
                return@withContext bytes
            }

            // Download from Telegram
            val fileInfo = apiClient.getFile(fileId)
            val bytes = apiClient.downloadFile(fileInfo.file_path)

            // Cache in memory
            memoryCache.put(fileUniqueId, bytes)

            // Cache to disk
            cacheFile(fileUniqueId, bytes)

            bytes
        }
    }

    /**
     * Gets the file extension for a sticker based on its type.
     */
    fun getFileExtension(isAnimated: Boolean, isVideo: Boolean): String {
        return when {
            isVideo -> "webm"
            isAnimated -> "tgs"
            else -> "webp"
        }
    }

    /**
     * Clears all cached files.
     */
    fun clearCache() {
        memoryCache.evictAll()
        fileCacheDir.deleteRecursively()
    }

    /**
     * Gets the total size of cached files in bytes.
     */
    fun getCacheSize(): Long {
        return fileCacheDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    private fun getCachedFile(fileUniqueId: String): File {
        val hash = fileUniqueId.hashCode().toString(16)
        return File(fileCacheDir, "$hash.sticker")
    }

    private fun cacheFile(fileUniqueId: String, bytes: ByteArray) {
        try {
            val file = getCachedFile(fileUniqueId)
            file.writeBytes(bytes)
        } catch (e: Exception) {
            // Silently fail if we can't cache
        }
    }
}