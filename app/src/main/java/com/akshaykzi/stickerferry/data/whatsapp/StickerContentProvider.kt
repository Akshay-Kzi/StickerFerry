package com.akshaykzi.stickerferry.data.whatsapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.util.Log
import com.akshaykzi.stickerferry.data.local.StickerPackDao
import com.akshaykzi.stickerferry.data.local.StickerPackEntity
import com.akshaykzi.stickerferry.domain.model.Sticker
import com.akshaykzi.stickerferry.domain.model.StickerPack
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException

/**
 * ContentProvider for WhatsApp sticker integration.
 * This implementation strictly follows the official WhatsApp sample requirements.
 */
class StickerContentProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface StickerProviderEntryPoint {
        fun stickerPackDao(): StickerPackDao
    }

    private val stickerPackDao: StickerPackDao by lazy {
        val ctx = context ?: throw IllegalStateException("Context is null")
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            ctx.applicationContext,
            StickerProviderEntryPoint::class.java
        )
        hiltEntryPoint.stickerPackDao()
    }

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "StickerProvider"
        const val AUTHORITY_SUFFIX = ".stickercontentprovider"

        private const val METADATA = "metadata"
        private const val METADATA_CODE = 1
        private const val METADATA_CODE_FOR_SINGLE_PACK = 2
        
        private const val STICKERS = "stickers"
        private const val STICKERS_CODE = 3
        
        private const val STICKERS_ASSET = "stickers_asset"
        private const val STICKERS_ASSET_CODE = 4

        // WhatsApp expected column names (Official order)
        const val STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier"
        const val STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name"
        const val STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher"
        const val STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon"
        const val ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link"
        const val IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_download_link"
        const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
        const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
        const val PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website"
        const val LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website"
        const val IMAGE_DATA_VERSION = "image_data_version"
        const val AVOID_CACHE = "whatsapp_will_not_cache_stickers"
        const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

        const val STICKER_FILE_NAME_IN_QUERY = "sticker_file_name"
        const val STICKER_FILE_EMOJI_IN_QUERY = "sticker_emoji"
        const val STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY = "sticker_accessibility_text"

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
        private var isMatcherInitialized = false

        private fun ensureMatcherInitialized(authority: String) {
            if (!isMatcherInitialized) {
                uriMatcher.addURI(authority, METADATA, METADATA_CODE)
                uriMatcher.addURI(authority, "$METADATA/*", METADATA_CODE_FOR_SINGLE_PACK)
                uriMatcher.addURI(authority, "$STICKERS/*", STICKERS_CODE)
                uriMatcher.addURI(authority, "$STICKERS_ASSET/*/*", STICKERS_ASSET_CODE)
                isMatcherInitialized = true
            }
        }
    }

    override fun onCreate(): Boolean = true

    private fun getAuthority(): String {
        return context?.packageName + AUTHORITY_SUFFIX
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val authority = getAuthority()
        ensureMatcherInitialized(authority)
        Log.d(TAG, "Query URI: $uri")
        
        return try {
            when (uriMatcher.match(uri)) {
                METADATA_CODE -> getPackForAllStickerPacks(uri)
                METADATA_CODE_FOR_SINGLE_PACK -> getCursorForSingleStickerPack(uri)
                STICKERS_CODE -> getStickersForAStickerPack(uri)
                else -> {
                    Log.w(TAG, "Unknown URI matched in query: $uri")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query failed: ${e.message}", e)
            null
        }
    }

    override fun getType(uri: Uri): String? {
        val authority = getAuthority()
        ensureMatcherInitialized(authority)
        return when (uriMatcher.match(uri)) {
            METADATA_CODE -> "vnd.android.cursor.dir/vnd.$authority.$METADATA"
            METADATA_CODE_FOR_SINGLE_PACK -> "vnd.android.cursor.item/vnd.$authority.$METADATA"
            STICKERS_CODE -> "vnd.android.cursor.dir/vnd.$authority.$STICKERS"
            STICKERS_ASSET_CODE -> {
                if (uri.toString().endsWith(".png")) "image/png" else "image/webp"
            }
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val authority = getAuthority()
        ensureMatcherInitialized(authority)
        Log.d(TAG, "OpenFile URI: $uri")
        
        return try {
            when (uriMatcher.match(uri)) {
                STICKERS_ASSET_CODE -> {
                    val segments = uri.pathSegments
                    val packId = segments[1]
                    val fileName = segments[2]
                    openAsset(packId, fileName)
                }
                else -> {
                    Log.w(TAG, "Unknown URI matched in openFile: $uri")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenFile failed: ${e.message}", e)
            null
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val pfd = openFile(uri, mode) ?: return null
        return AssetFileDescriptor(pfd, 0, -1)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun getPackForAllStickerPacks(uri: Uri): Cursor {
        val packs = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            stickerPackDao.getAllPacksSync().map { it.toDomain() }
        }
        return getStickerPackInfo(uri, packs)
    }

    private fun getCursorForSingleStickerPack(uri: Uri): Cursor {
        val packId = uri.lastPathSegment ?: return getStickerPackInfo(uri, emptyList())
        val pack = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            stickerPackDao.getPackById(packId)?.toDomain()
        }
        return getStickerPackInfo(uri, listOfNotNull(pack))
    }

    private fun getStickerPackInfo(uri: Uri, packs: List<StickerPack>): Cursor {
        val cursor = MatrixCursor(arrayOf(
            STICKER_PACK_IDENTIFIER_IN_QUERY,
            STICKER_PACK_NAME_IN_QUERY,
            STICKER_PACK_PUBLISHER_IN_QUERY,
            STICKER_PACK_ICON_IN_QUERY,
            ANDROID_APP_DOWNLOAD_LINK_IN_QUERY,
            IOS_APP_DOWNLOAD_LINK_IN_QUERY,
            PUBLISHER_EMAIL,
            PUBLISHER_WEBSITE,
            PRIVACY_POLICY_WEBSITE,
            LICENSE_AGREEMENT_WEBSITE,
            IMAGE_DATA_VERSION,
            AVOID_CACHE,
            ANIMATED_STICKER_PACK
        ))

        for (pack in packs) {
            cursor.addRow(arrayOf<Any>(
                pack.identifier,
                pack.name,
                pack.publisher,
                pack.trayImageFile,
                "", // android_play_store_link
                "", // ios_app_download_link
                "stickerferry@akshaykzi.com", // publisher_email
                pack.publisherWebsite ?: "",
                pack.privacyPolicyUrl ?: "",
                pack.licenseAgreementUrl ?: "",
                "1", // image_data_version
                0, // avoid_cache
                0 // animated_sticker_pack (forced static for now)
            ))
        }
        cursor.setNotificationUri(context?.contentResolver, uri)
        return cursor
    }

    private fun getStickersForAStickerPack(uri: Uri): Cursor {
        val packId = uri.lastPathSegment ?: return MatrixCursor(emptyArray())
        val pack = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            stickerPackDao.getPackById(packId)?.toDomain()
        } ?: return MatrixCursor(emptyArray())

        val cursor = MatrixCursor(arrayOf(
            STICKER_FILE_NAME_IN_QUERY,
            STICKER_FILE_EMOJI_IN_QUERY,
            STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY
        ))

        for (sticker in pack.stickers) {
            val emojiString = if (sticker.emojis.isNotEmpty()) TextUtils.join(",", sticker.emojis) else "😀"
            cursor.addRow(arrayOf(sticker.fileName, emojiString, ""))
        }
        cursor.setNotificationUri(context?.contentResolver, uri)
        return cursor
    }

    private fun openAsset(packId: String, fileName: String): ParcelFileDescriptor? {
        val baseDir = File(context?.filesDir, "converted_stickers")
        val packDir = File(baseDir, packId)
        val file = File(packDir, fileName)
        
        if (!file.exists()) {
            Log.e(TAG, "File not found: ${file.absolutePath}")
            throw FileNotFoundException("File not found: $fileName")
        }
        
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun StickerPackEntity.toDomain(): StickerPack {
        val stickers = try {
            json.decodeFromString<List<Sticker>>(stickersJson)
        } catch (e: Exception) {
            emptyList()
        }
        return StickerPack(
            identifier = identifier,
            name = name,
            publisher = publisher,
            stickers = stickers,
            trayImageFile = trayImageFile,
            animatedPack = animatedPack,
            publisherWebsite = publisherWebsite,
            privacyPolicyUrl = privacyPolicyUrl,
            licenseAgreementUrl = licenseAgreementUrl
        )
    }
}
