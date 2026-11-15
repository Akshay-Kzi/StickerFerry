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
 * The bot token belongs to whoever uses the app: it is entered on the device and
 * kept in app-private storage. Nothing is baked into the APK, so a published
 * build can never leak a working token.
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
     * Gets the Telegram bot token the user saved on this device.
     * Returns an empty string until they add one.
     */
    fun getBotToken(): String {
        return prefs.getString(KEY_BOT_TOKEN, "") ?: ""
    }

    /**
     * Saves the bot token to SharedPreferences.
     * This is used when the user enters the token manually.
     */
    fun saveBotToken(token: String) {
        prefs.edit().putString(KEY_BOT_TOKEN, token.trim()).apply()
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

        // BotFather tokens look like "123456789:AAH..." — digits, a colon, then
        // 35 URL-safe characters. Accept a little slack so new token formats
        // aren't rejected, and let Telegram be the real judge.
        private val BOT_TOKEN_PATTERN = Regex("""\d{5,}:[A-Za-z0-9_-]{30,}""")

        /**
         * Cheap client-side sanity check before hitting the network. This is a
         * shape check only; a well-formed token can still be revoked.
         */
        fun isPlausibleBotToken(token: String): Boolean {
            return BOT_TOKEN_PATTERN.matches(token.trim())
        }
    }
}
