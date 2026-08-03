package com.akshaykzi.stickerferry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerPackDao {
    @Query("SELECT * FROM sticker_packs ORDER BY lastUsedTimestamp DESC")
    fun getAllPacks(): Flow<List<StickerPackEntity>>

    @Query("SELECT * FROM sticker_packs ORDER BY lastUsedTimestamp DESC")
    suspend fun getAllPacksSync(): List<StickerPackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPack(pack: StickerPackEntity)

    @Query("DELETE FROM sticker_packs WHERE identifier = :identifier")
    suspend fun deletePack(identifier: String)

    @Query("SELECT * FROM sticker_packs WHERE identifier = :identifier")
    suspend fun getPackById(identifier: String): StickerPackEntity?

    @Query("UPDATE sticker_packs SET addedToWhatsApp = :added WHERE identifier = :identifier")
    suspend fun updateAddedToWhatsApp(identifier: String, added: Boolean)
}
