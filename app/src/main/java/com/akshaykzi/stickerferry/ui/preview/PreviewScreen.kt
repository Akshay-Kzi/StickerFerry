package com.akshaykzi.stickerferry.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.akshaykzi.stickerferry.domain.model.Sticker
import com.akshaykzi.stickerferry.domain.model.StickerPack
import com.akshaykzi.stickerferry.ui.theme.StickerFerryTheme

/**
 * Preview screen for reviewing sticker packs before conversion.
 *
 * Features:
 * - Grid of stickers with type badges
 * - Editable pack name and publisher
 * - Convert button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    modifier: Modifier = Modifier,
    stickerLink: String? = null,
    pack: StickerPack? = null,
    isLoading: Boolean = false,
    error: String? = null,
    onBack: () -> Unit = {},
    onConvert: (StickerPack) -> Unit = {},
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val effectivePack = pack ?: uiState.pack
    val effectiveIsLoading = isLoading || uiState.isLoading
    val effectiveError = error ?: uiState.error

    LaunchedEffect(stickerLink) {
        if (stickerLink != null && (uiState.pack == null && uiState.error == null)) {
            viewModel.loadStickerPack(stickerLink)
        }
    }

    var editableName by remember { mutableStateOf(effectivePack?.name ?: "") }
    var editablePublisher by remember { mutableStateOf(effectivePack?.publisher ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Preview Pack",
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
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (effectiveIsLoading) {
                LoadingState()
            } else if (effectiveError != null) {
                ErrorState(
                    error = effectiveError,
                    onRetry = { stickerLink?.let { viewModel.loadStickerPack(it) } },
                )
            } else if (effectivePack != null) {
                // Pack metadata editor
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
                        Text(
                            text = "Pack Information",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = editableName,
                            onValueChange = { editableName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Pack Name") },
                            singleLine = true,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = editablePublisher,
                            onValueChange = { editablePublisher = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Publisher") },
                            singleLine = true,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${effectivePack.stickers.size} stickers • ${if (effectivePack.animatedPack) "Animated" else "Static"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sticker grid
                Text(
                    text = "Stickers",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(effectivePack.stickers) { sticker ->
                        StickerGridItem(
                            sticker = sticker,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Convert button
                Button(
                    onClick = {
                        val updatedPack = effectivePack.copy(
                            name = editableName,
                            publisher = editablePublisher,
                        )
                        onConvert(updatedPack)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = effectivePack.stickers.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Convert",
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (effectivePack.addedToWhatsApp) "Re-add to WhatsApp" else "Convert to WhatsApp",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else {
                // No pack loaded - show link info
                NoPackState(stickerLink = stickerLink)
            }
        }
    }
}

/**
 * Individual sticker grid item.
 */
@Composable
fun StickerGridItem(
    sticker: Sticker,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        sticker.isVideo -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        sticker.isAnimated -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    }

    val contentColor = when {
        sticker.isVideo -> MaterialTheme.colorScheme.error
        sticker.isAnimated -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, contentColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (sticker.localCachePath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(sticker.localCachePath)
                        .crossfade(enable = true)
                        .build(),
                    contentDescription = sticker.fileName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Type badge
            Text(
                text = when {
                    sticker.isVideo -> "Video"
                    sticker.isAnimated -> "GIF"
                    else -> "Static"
                },
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )

            // Emoji
            if (sticker.emojis.isNotEmpty()) {
                Text(
                    text = sticker.emojis.firstOrNull() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Loading state while fetching pack data.
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Loading sticker pack...",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * Error state when loading fails.
 */
@Composable
fun ErrorState(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Failed to load sticker pack",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            FilledTonalButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Retry",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * State when no pack is loaded.
 */
@Composable
fun NoPackState(stickerLink: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "No sticker pack loaded",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stickerLink ?: "Paste a Telegram sticker link on the home screen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewScreenPreview() {
    StickerFerryTheme {
        PreviewScreen(
            pack = StickerPack(
                identifier = "test_pack",
                name = "Test Pack",
                publisher = "Test Publisher",
                stickers = listOf(
                    Sticker(fileName = "sticker1.webp", emojis = listOf("😀")),
                    Sticker(fileName = "sticker2.webp", emojis = listOf("😎")),
                    Sticker(fileName = "sticker3.webp", emojis = listOf("🎉")),
                ),
                trayImageFile = "tray.webp",
            ),
        )
    }
}