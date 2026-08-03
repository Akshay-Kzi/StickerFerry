package com.akshaykzi.stickerferry.data.repository

import com.akshaykzi.stickerferry.data.local.StickerPackDao
import com.akshaykzi.stickerferry.data.local.StickerPackEntity
import com.akshaykzi.stickerferry.domain.model.Sticker
import com.akshaykzi.stickerferry.domain.model.StickerPack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StickerPackRepository @Inject constructor(
    private val stickerPackDao: StickerPackDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getRecentPacks(): Flow<List<StickerPack>> {
        return stickerPackDao.getAllPacks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun savePack(pack: StickerPack) {
        stickerPackDao.insertPack(pack.toEntity())
    }

    suspend fun getPack(identifier: String): StickerPack? {
        return stickerPackDao.getPackById(identifier)?.toDomain()
    }

    suspend fun deletePack(identifier: String) {
        stickerPackDao.deletePack(identifier)
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
            addedToWhatsApp = addedToWhatsApp,
            publisherWebsite = publisherWebsite,
            privacyPolicyUrl = privacyPolicyUrl,
            licenseAgreementUrl = licenseAgreementUrl
        )
    }

    suspend fun markAsAddedToWhatsApp(identifier: String) {
        stickerPackDao.updateAddedToWhatsApp(identifier, true)
    }

    private fun StickerPack.toEntity(): StickerPackEntity {
        return StickerPackEntity(
            identifier = identifier,
            name = name,
            publisher = publisher,
            trayImageFile = trayImageFile,
            animatedPack = animatedPack,
            addedToWhatsApp = addedToWhatsApp,
            publisherWebsite = publisherWebsite,
            privacyPolicyUrl = privacyPolicyUrl,
            licenseAgreementUrl = licenseAgreementUrl,
            stickersJson = json.encodeToString(stickers)
        )
    }
}
