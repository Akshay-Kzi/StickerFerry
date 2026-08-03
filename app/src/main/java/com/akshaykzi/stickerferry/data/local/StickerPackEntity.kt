package com.akshaykzi.stickerferry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sticker_packs")
data class StickerPackEntity(
    @PrimaryKey val identifier: String,
    val name: String,
    val publisher: String,
    val trayImageFile: String,
    val animatedPack: Boolean,
    val addedToWhatsApp: Boolean = false,
    val publisherWebsite: String?,
    val privacyPolicyUrl: String?,
    val licenseAgreementUrl: String?,
    val stickersJson: String, // JSON representation of List<Sticker>
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)
