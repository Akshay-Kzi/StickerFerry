package com.akshaykzi.stickerferry.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshaykzi.stickerferry.data.repository.StickerPackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val stickerPackRepository: StickerPackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    fun checkNextPart(currentPackId: String) {
        if (!currentPackId.contains("_")) return

        viewModelScope.launch {
            val next = findNextPart(currentPackId)
            if (next != null) {
                _uiState.value = _uiState.value.copy(nextPartId = next)
            }
        }
    }

    /**
     * Finds the next un-added sibling part for identifiers like "pack_static_2",
     * "pack_anim_1" or "pack_static". All parts of one Telegram pack share the
     * "<base>_" prefix and are saved to the DB when the pack is first fetched, so
     * this lookup is purely local. Siblings are ordered static parts first, then
     * animated parts, which also makes the button appear for the static ->
     * animated transition of mixed packs (last "pack_static_N" -> "pack_anim_1").
     */
    private suspend fun findNextPart(currentPackId: String): String? {
        val currentKey = partOrderKey(currentPackId)
            ?: return null // not a part identifier we understand
        val base = baseOf(currentPackId) ?: return null

        return stickerPackRepository.getPacksWithPrefix("${base}_")
            .asSequence()
            .filter { it.identifier != currentPackId && !it.addedToWhatsApp }
            // Exact siblings only: a different set named "Honeybee_extra" would also
            // match the "Honeybee_" prefix but has a different base.
            .filter { baseOf(it.identifier) == base }
            .filter { sibling -> (partOrderKey(sibling.identifier) ?: Long.MIN_VALUE) > currentKey }
            .minByOrNull { partOrderKey(it.identifier) ?: Long.MIN_VALUE }
            ?.identifier
    }

    /**
     * The Telegram base set name for a part identifier
     * ("Honeybee_static_2" -> "Honeybee", legacy "Honeybee_2" -> "Honeybee"),
     * or null if the identifier doesn't follow a known part scheme.
     */
    private fun baseOf(identifier: String): String? {
        PART_ID_REGEX.find(identifier)?.let { return it.groupValues[1] }
        identifier.substringAfterLast('_').toLongOrNull() ?: return null
        return identifier.substringBeforeLast('_').takeIf { it.isNotEmpty() }
    }

    /**
     * Sort key giving parts of one pack their natural order:
     * pack_static_1 < pack_static_2 < ... < pack_anim_1 < pack_anim_2.
     * Returns null for identifiers that don't follow the part scheme.
     */
    private fun partOrderKey(identifier: String): Long? {
        val match = PART_ID_REGEX.find(identifier)
        if (match != null) {
            val typeRank = if (match.groupValues[2] == "anim") ANIM_RANK else 0L
            return typeRank + (match.groupValues[3].toLongOrNull() ?: 1L)
        }
        // Legacy "<base>_<n>" identifiers.
        val n = identifier.substringAfterLast('_').toLongOrNull() ?: return null
        return n
    }

    companion object {
        // Matches StickerFerry part identifiers: "<base>_static", "<base>_anim",
        // "<base>_static_2", "<base>_anim_12", ...
        private val PART_ID_REGEX = Regex("^(.+)_(static|anim)(?:_([0-9]+))?$")

        // Animated parts sort after all static parts (max 30 stickers per part).
        private const val ANIM_RANK = 1_000_000L
    }
}

data class ResultUiState(
    val nextPartId: String? = null
)
