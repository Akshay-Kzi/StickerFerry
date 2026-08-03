package com.akshaykzi.stickerferry.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.akshaykzi.stickerferry.domain.model.StickerPack
import com.akshaykzi.stickerferry.ui.home.HomeScreen
import com.akshaykzi.stickerferry.ui.preview.PreviewScreen
import com.akshaykzi.stickerferry.ui.progress.ProgressScreen
import com.akshaykzi.stickerferry.ui.result.ResultScreen

/**
 * Navigation routes for the app.
 */
object Routes {
    const val HOME = "home"
    const val PREVIEW = "preview"
    const val PROGRESS = "progress/{packId}"
    const val RESULT = "result/{packId}"

    fun progress(packId: String) = "progress/$packId"
    fun result(packId: String) = "result/$packId"
}

/**
 * Main navigation graph for the app.
 *
 * @param initialStickerLink Optional initial sticker link from intent
 * @param modifier Modifier for the nav host
 */
@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    initialStickerLink: String? = null,
) {
    val navController = rememberNavController()
    var stickerLink by remember { mutableStateOf(initialStickerLink) }
    var pendingPack by remember { mutableStateOf<StickerPack?>(null) }

    // Handle initial sticker link from intent
    LaunchedEffect(initialStickerLink) {
        if (initialStickerLink != null) {
            stickerLink = initialStickerLink
            // Navigate to preview with the link
            navController.navigate(Routes.PREVIEW) {
                popUpTo(Routes.HOME)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onStickerLinkSubmitted = { link ->
                    stickerLink = link
                    navController.navigate(Routes.PREVIEW)
                },
                onNavigateToPreview = { link ->
                    stickerLink = link
                    navController.navigate(Routes.PREVIEW)
                },
            )
        }

        composable(Routes.PREVIEW) {
            PreviewScreen(
                stickerLink = stickerLink,
                onBack = { navController.popBackStack() },
                onConvert = { pack ->
                    pendingPack = pack
                    navController.navigate(Routes.progress(pack.identifier))
                },
            )
        }

        composable(Routes.PROGRESS) { backStackEntry ->
            val packId = backStackEntry.arguments?.getString("packId") ?: return@composable
            val pack = if (pendingPack?.identifier == packId) pendingPack else null
            ProgressScreen(
                packId = packId,
                pack = pack,
                onBack = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
                onComplete = { packId ->
                    navController.navigate(Routes.result(packId)) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }

        composable(Routes.RESULT) { backStackEntry ->
            val packId = backStackEntry.arguments?.getString("packId") ?: return@composable
            ResultScreen(
                packId = packId,
                onNavigateHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onNavigateToNextPart = { nextId ->
                    stickerLink = nextId
                    navController.navigate(Routes.PREVIEW) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }
    }
}