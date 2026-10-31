package com.akshaykzi.stickerferry.data.telegram

import android.content.Context
import android.content.SharedPreferences
import com.akshaykzi.stickerferry.BuildConfig
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
        // Direct reference, not reflection: R8 strips classes that are only
        // accessed reflectively, which made release builds report a missing
        // token even though it was compiled in.
        val buildConfigToken = BuildConfig.TELEGRAM_BOT_TOKEN
        if (buildConfigToken.isNotBlank()) {
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