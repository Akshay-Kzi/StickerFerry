package com.akshaykzi.stickerferry.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [StickerPackEntity::class], version = 2, exportSchema = false)
abstract class StickerPackDatabase : RoomDatabase() {
    abstract fun stickerPackDao(): StickerPackDao
}
