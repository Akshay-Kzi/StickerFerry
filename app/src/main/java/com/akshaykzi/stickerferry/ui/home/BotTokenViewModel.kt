package com.akshaykzi.stickerferry.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshaykzi.stickerferry.data.telegram.TelegramApiClient
import com.akshaykzi.stickerferry.data.telegram.TelegramConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the "bring your own token" flow: validate, store, and verify the token
 * the user pastes in.
 */
@HiltViewModel
class BotTokenViewModel @Inject constructor(
    private val config: TelegramConfig,
    private val api: TelegramApiClient,
) : ViewModel() {

    private val _state = MutableStateFlow(BotTokenState(hasToken = config.hasBotToken()))
    val state: StateFlow<BotTokenState> = _state.asStateFlow()

    fun save(rawToken: String) {
        val token = rawToken.trim()
        if (!TelegramConfig.isPlausibleBotToken(token)) {
            _state.update {
                it.copy(error = "That doesn't look like a bot token. It should look like 123456789:AA…")
            }
            return
        }

        // getMe() reads the token back from config, so store it first and roll
        // back if Telegram rejects it.
        val previous = config.getBotToken()
        config.saveBotToken(token)
        _state.update { it.copy(isVerifying = true, error = null, saved = false) }

        viewModelScope.launch {
            try {
                val me = api.getMe()
                _state.update {
                    it.copy(
                        hasToken = true,
                        isVerifying = false,
                        error = null,
                        botUsername = me.username,
                        saved = true,
                    )
                }
            } catch (e: Exception) {
                if (previous.isBlank()) config.clearBotToken() else config.saveBotToken(previous)
                _state.update {
                    it.copy(
                        isVerifying = false,
                        error = e.message ?: "Could not verify that token",
                        saved = false,
                    )
                }
            }
        }
    }

    fun clear() {
        config.clearBotToken()
        _state.value = BotTokenState(hasToken = false)
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

data class BotTokenState(
    val hasToken: Boolean = false,
    val isVerifying: Boolean = false,
    val error: String? = null,
    val botUsername: String? = null,
    /** True once a save attempt was verified by Telegram; the dialog closes on it. */
    val saved: Boolean = false,
)
