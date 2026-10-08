package com.akshaykzi.stickerferry.ui.progress

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akshaykzi.stickerferry.domain.model.ConversionState
import com.akshaykzi.stickerferry.domain.model.Sticker
import com.akshaykzi.stickerferry.domain.model.StickerPack
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.akshaykzi.stickerferry.ui.theme.StickerFerryTheme

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
        if (result.resultCode != android.app.Activity.RESULT_CANCELED) {
            onComplete(packId)
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
                        text = "Converting...",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        val gradientColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                        colors = listOf(
                            gradientColor,
                            Color.Transparent
                        )
                        ),
                        radius = size.maxDimension * 0.8f,
                        center = Offset(size.width * 0.3f, size.height * 0.7f)
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Expressive Progress Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Working Magic",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = effectiveProgress,
                                modifier = Modifier.size(120.dp),
                                strokeWidth = 12.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                            text = "${(effectiveProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Processing ${effectiveIndex + 1} of ${effectiveStickers.size} stickers",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Sticker progress list
                Text(
                    text = "Conversion Log",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalStickerList(
                    stickers = effectiveStickers,
                    currentIndex = effectiveIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                // Error summary
                if (effectiveErrors.isNotEmpty()) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(),
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                            ),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Some stickers failed",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                effectiveErrors.take(3).forEach { error ->
                                    Text(
                                        text = "• $error",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Start
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (isConverting && !effectiveComplete) {
                        FilledTonalButton(
                            onClick = { viewModel.cancelConversion(); onCancel() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cancel", fontWeight = FontWeight.Medium)
                        }
                    }

                    if (effectiveComplete || (!isConverting && effectiveProgress >= 1f)) {
                        if (effectiveErrors.isEmpty()) {
                            Button(
                            onClick = { viewModel.addToWhatsApp() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            enabled = true
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Complete", modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add to WhatsApp", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            FilledTonalButton(
                            onClick = { viewModel.retryConversion(); onRetry() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry Failed", fontWeight = FontWeight.Medium)
                            }
                        }
                    } else if (!isConverting && effectiveErrors.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.retryConversion(); onRetry() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(20.dp), )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Conversion", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LazyVerticalStickerList(
    stickers: List<Sticker>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { },
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.2f)),
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
                        Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Converting",
                        modifier = Modifier.size(16.dp),
                        tint = statusColor,
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
