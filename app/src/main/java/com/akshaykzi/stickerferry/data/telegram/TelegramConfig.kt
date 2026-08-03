package com.akshaykzi.stickerferry.data.telegram

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration for the Telegram bot.
 *
 * This class manages the bot token and other configuration settings.
 * The bot token should be stored securely and never committed to version control.
 */
@Singleton
class TelegramConfig @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("telegram_config", Context.MODE_PRIVATE)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Gets the Telegram bot token.
     * First tries BuildConfig, then falls back to SharedPreferences.
     */
    fun getBotToken(): String {
        // Try BuildConfig first (from local.properties or environment)
        val buildConfigToken = try {
            val clazz = Class.forName("com.akshaykzi.stickerferry.BuildConfig")
            val field = clazz.getField("TELEGRAM_BOT_TOKEN")
            field.get(null) as? String
        } catch (e: Exception) {
            null
        }

        if (!buildConfigToken.isNullOrBlank()) {
            return buildConfigToken
        }

        // Fall back to SharedPreferences
        return prefs.getString(KEY_BOT_TOKEN, "") ?: ""
    }

    /**
     * Saves the bot token to SharedPreferences.
     * This is used when the user enters the token manually.
     */
    fun saveBotToken(token: String) {
        prefs.edit().putString(KEY_BOT_TOKEN, token).apply()
    }

    /**
     * Checks if a bot token is configured.
     */
    fun hasBotToken(): Boolean {
        return getBotToken().isNotBlank()
    }

    /**
     * Clears the stored bot token.
     */
    fun clearBotToken() {
        prefs.edit().remove(KEY_BOT_TOKEN).apply()
    }

    companion object {
        private const val KEY_BOT_TOKEN = "bot_token"
    }
}