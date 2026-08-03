package com.akshaykzi.stickerferry.ui.progress

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.akshaykzi.stickerferry.domain.model.ConversionState
import com.akshaykzi.stickerferry.domain.model.Sticker
import com.akshaykzi.stickerferry.domain.model.StickerPack
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.akshaykzi.stickerferry.ui.theme.StickerFerryTheme

/**
 * Progress screen for sticker conversion.
 *
 * Features:
 * - Per-sticker conversion progress
 * - Overall progress bar
 * - Cancel option
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    packId: String,
    pack: StickerPack? = null,
    stickers: List<Sticker> = emptyList(),
    currentStickerIndex: Int = 0,
    overallProgress: Float = 0f,
    isComplete: Boolean = false,
    errors: List<String> = emptyList(),
    onBack: () -> Unit = {},
    onComplete: (String) -> Unit = {},
    onCancel: () -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val effectiveStickers = uiState.stickers.ifEmpty { stickers }
    val effectiveIndex = if (uiState.stickers.isNotEmpty()) {
        if (uiState.isComplete) uiState.stickers.size else uiState.currentStickerIndex
    } else currentStickerIndex
    val effectiveProgress = if (uiState.stickers.isNotEmpty()) uiState.overallProgress else overallProgress
    val effectiveComplete = if (uiState.stickers.isNotEmpty()) uiState.isComplete else isComplete
    val effectiveErrors = if (uiState.stickers.isNotEmpty()) uiState.errors else errors
    val isConverting = uiState.isConverting

    val snackbarHostState = remember { SnackbarHostState() }

    val whatsappLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Check if WhatsApp actually returned a successful-looking result
        // Some versions of WhatsApp don't return RESULT_OK, but they definitely 
        // return RESULT_CANCELED if the user backed out.
        if (result.resultCode != android.app.Activity.RESULT_CANCELED) {
            onComplete(packId)
        } else {
            // User cancelled or it failed, stay on this screen and allow retry
        }
    }

    LaunchedEffect(uiState.handoffErrors) {
        if (uiState.handoffErrors.isNotEmpty()) {
            snackbarHostState.showSnackbar(
                message = uiState.handoffErrors.first(),
                actionLabel = "Dismiss"
            )
            viewModel.resetHandoff()
        }
    }

    LaunchedEffect(uiState.handoffReady, uiState.handoffIntent) {
        if (uiState.handoffReady && uiState.handoffIntent != null) {
            try {
                whatsappLauncher.launch(uiState.handoffIntent)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to open WhatsApp")
            } finally {
                viewModel.resetHandoff()
            }
        }
    }

    LaunchedEffect(pack) {
        if (pack != null && uiState.pack == null) {
            viewModel.startConversion(pack)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Converting Stickers",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            // Overall progress
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Overall Progress",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${(effectiveProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = effectiveProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${effectiveIndex + 1} of ${effectiveStickers.size} stickers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Per-sticker progress list
            Text(
                text = "Sticker Progress",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalStickerList(
                stickers = effectiveStickers,
                currentIndex = effectiveIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error summary if any
            if (effectiveErrors.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "${effectiveErrors.size} errors occurred",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        effectiveErrors.take(3).forEach { error ->
                            Text(
                                text = "• $error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }

                        if (effectiveErrors.size > 3) {
                            Text(
                                text = "... and ${effectiveErrors.size - 3} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isConverting && !effectiveComplete) {
                    // Cancel button
                    FilledTonalButton(
                        onClick = { viewModel.cancelConversion(); onCancel() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Cancel",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                if (effectiveComplete || (!isConverting && effectiveProgress >= 1f)) {
                    if (effectiveErrors.isEmpty()) {
                        // Success - complete button
                        Button(
                            onClick = { viewModel.addToWhatsApp() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Complete",
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Add to WhatsApp",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    } else {
                        // Errors - retry button
                        FilledTonalButton(
                            onClick = { viewModel.retryConversion(); onRetry() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Retry Failed",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                } else if (!isConverting && effectiveErrors.isNotEmpty()) {
                    // Fail state button (not complete but stopped with errors)
                    Button(
                        onClick = { viewModel.retryConversion(); onRetry() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Retry Conversion",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Vertical list of sticker progress items.
 */
@Composable
fun LazyVerticalStickerList(
    stickers: List<Sticker>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(stickers) { index, sticker ->
            StickerProgressItem(
                sticker = sticker,
                state = when {
                    sticker.conversionState.isTerminal() -> sticker.conversionState
                    index < currentIndex -> ConversionState.Done()
                    index == currentIndex -> ConversionState.Converting(0.5f)
                    else -> ConversionState.Queued
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Individual sticker progress item.
 */
@Composable
fun StickerProgressItem(
    sticker: Sticker,
    state: ConversionState,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (state) {
        is ConversionState.Queued -> MaterialTheme.colorScheme.outline
        is ConversionState.Converting -> MaterialTheme.colorScheme.primary
        is ConversionState.Done -> MaterialTheme.colorScheme.primary
        is ConversionState.Failed -> MaterialTheme.colorScheme.error
    }

    val containerColor = when (state) {
        is ConversionState.Converting -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    is ConversionState.Queued -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Queued",
                            modifier = Modifier.size(16.dp),
                            tint = statusColor,
                        )
                    }
                    is ConversionState.Converting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = statusColor,
                        )
                    }
                    is ConversionState.Done -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Done",
                            modifier = Modifier.size(16.dp),
                            tint = statusColor,
                        )
                    }
                    is ConversionState.Failed -> {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Failed",
                            modifier = Modifier.size(16.dp),
                            tint = statusColor,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Sticker info
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = sticker.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )

                Text(
                    text = state.getProgressString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
            }

            // Type badge
            Text(
                text = when {
                    sticker.isVideo -> "Video"
                    sticker.isAnimated -> "GIF"
                    else -> "Static"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProgressScreenPreview() {
    StickerFerryTheme {
        ProgressScreen(
            packId = "test_pack",
            stickers = listOf(
                Sticker(fileName = "sticker1.webp", emojis = listOf("😀")),
                Sticker(fileName = "sticker2.webp", emojis = listOf("😎")),
                Sticker(fileName = "sticker3.webp", emojis = listOf("🎉")),
            ),
            currentStickerIndex = 1,
            overallProgress = 0.33f,
        )
    }
}