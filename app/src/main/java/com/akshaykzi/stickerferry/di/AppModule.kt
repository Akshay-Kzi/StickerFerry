package com.akshaykzi.stickerferry.di

import android.content.Context
import androidx.room.Room
import com.akshaykzi.stickerferry.data.local.StickerPackDao
import com.akshaykzi.stickerferry.data.local.StickerPackDatabase
import com.akshaykzi.stickerferry.data.conversion.AnimatedStickerConverter
import com.akshaykzi.stickerferry.data.conversion.ConversionPipeline
import com.akshaykzi.stickerferry.data.conversion.StaticStickerConverter
import com.akshaykzi.stickerferry.data.conversion.VideoStickerConverter
import com.akshaykzi.stickerferry.data.telegram.TelegramApiClient
import com.akshaykzi.stickerferry.data.telegram.TelegramConfig
import com.akshaykzi.stickerferry.data.telegram.TelegramRepository
import com.akshaykzi.stickerferry.data.whatsapp.PackMetadataBuilder
import com.akshaykzi.stickerferry.data.whatsapp.WhatsAppHandoffManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing application-wide dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideTelegramConfig(
        @ApplicationContext context: Context,
    ): TelegramConfig {
        return TelegramConfig(context)
    }

    @Provides
    @Singleton
    fun provideTelegramApiClient(
        config: TelegramConfig,
    ): TelegramApiClient {
        return TelegramApiClient(config)
    }

    @Provides
    @Singleton
    fun provideTelegramRepository(
        @ApplicationContext context: Context,
        apiClient: TelegramApiClient,
    ): TelegramRepository {
        return TelegramRepository(context, apiClient)
    }

    @Provides
    @Singleton
    fun provideStaticStickerConverter(
        @ApplicationContext context: Context,
        telegramRepository: TelegramRepository,
    ): StaticStickerConverter {
        return StaticStickerConverter(context, telegramRepository)
    }

    @Provides
    @Singleton
    fun provideAnimatedStickerConverter(
        @ApplicationContext context: Context,
        telegramRepository: TelegramRepository,
    ): AnimatedStickerConverter {
        return AnimatedStickerConverter(context, telegramRepository)
    }

    @Provides
    @Singleton
    fun provideVideoStickerConverter(
        @ApplicationContext context: Context,
        telegramRepository: TelegramRepository,
    ): VideoStickerConverter {
        return VideoStickerConverter(context, telegramRepository)
    }

    @Provides
    @Singleton
    fun provideConversionPipeline(
        staticConverter: StaticStickerConverter,
        animatedConverter: AnimatedStickerConverter,
        videoConverter: VideoStickerConverter,
    ): ConversionPipeline {
        return ConversionPipeline(
            staticConverter = staticConverter,
            animatedConverter = animatedConverter,
            videoConverter = videoConverter,
        )
    }

    @Provides
    @Singleton
    fun providePackMetadataBuilder(
        @ApplicationContext context: Context,
    ): PackMetadataBuilder {
        return PackMetadataBuilder(context)
    }

    @Provides
    @Singleton
    fun provideWhatsAppHandoffManager(
        @ApplicationContext context: Context,
    ): WhatsAppHandoffManager {
        return WhatsAppHandoffManager(context)
    }

    @Provides
    @Singleton
    fun provideStickerPackDatabase(
        @ApplicationContext context: Context,
    ): StickerPackDatabase {
        return Room.databaseBuilder(
            context,
            StickerPackDatabase::class.java,
            "sticker_ferry_db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideStickerPackDao(database: StickerPackDatabase): StickerPackDao {
        return database.stickerPackDao()
    }
}
