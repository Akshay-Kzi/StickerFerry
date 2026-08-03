package com.akshaykzi.stickerferry.data.whatsapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.akshaykzi.stickerferry.domain.model.StickerPack
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for handling WhatsApp sticker pack handoff.
 *
 * This manager handles:
 * - Checking WhatsApp installation
 * - Building the intent to add stickers to WhatsApp
 * - Supporting both WhatsApp and WhatsApp Business
 * - Handling the result from WhatsApp
 */
@Singleton
class WhatsAppHandoffManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        private const val ENABLE_STICKER_PACK_ACTION = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
        private const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
        private const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
        private const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"
    }

    /**
     * Checks if WhatsApp is installed.
     */
    fun isWhatsAppInstalled(): Boolean {
        return isPackageInstalled(WHATSAPP_PACKAGE)
    }

    /**
     * Checks if WhatsApp Business is installed.
     */
    fun isWhatsAppBusinessInstalled(): Boolean {
        return isPackageInstalled(WHATSAPP_BUSINESS_PACKAGE)
    }

    /**
     * Gets all installed WhatsApp packages.
     */
    fun getInstalledWhatsAppPackages(): List<String> {
        val packages = mutableListOf<String>()
        if (isWhatsAppInstalled()) {
            packages.add(WHATSAPP_PACKAGE)
        }
        if (isWhatsAppBusinessInstalled()) {
            packages.add(WHATSAPP_BUSINESS_PACKAGE)
        }
        return packages
    }

    /**
     * Creates an intent to add a sticker pack to WhatsApp.
     *
     * @param pack The sticker pack to add
     * @param authority The ContentProvider authority
     * @param targetPackage Specific WhatsApp package (null for auto-detect)
     * @return Intent to launch, or null if WhatsApp is not installed
     */
    fun createAddPackIntent(
        pack: StickerPack,
        authority: String,
        targetPackage: String? = null,
    ): Intent? {
        val packages = if (targetPackage != null) {
            if (isPackageInstalled(targetPackage)) listOf(targetPackage) else emptyList()
        } else {
            getInstalledWhatsAppPackages()
        }

        if (packages.isEmpty()) {
            return null
        }

        // Try each package
        for (packageName in packages) {
            val intent = Intent(ENABLE_STICKER_PACK_ACTION).apply {
                putExtra(EXTRA_STICKER_PACK_ID, pack.identifier)
                putExtra(EXTRA_STICKER_PACK_AUTHORITY, authority)
                putExtra(EXTRA_STICKER_PACK_NAME, pack.name)
                setPackage(packageName)
            }

            // Verify the intent can be resolved
            if (intent.resolveActivity(context.packageManager) != null) {
                return intent
            }
        }

        return null
    }

    /**
     * Gets the recommended WhatsApp package for the sticker pack.
     *
     * @param pack The sticker pack
     * @return The recommended package name, or null if none available
     */
    fun getRecommendedPackage(pack: StickerPack): String? {
        // If the pack is animated, prefer WhatsApp (better animation support)
        // Otherwise, prefer WhatsApp Business if available
        return when {
            pack.animatedPack && isWhatsAppInstalled() -> WHATSAPP_PACKAGE
            isWhatsAppBusinessInstalled() -> WHATSAPP_BUSINESS_PACKAGE
            isWhatsAppInstalled() -> WHATSAPP_PACKAGE
            else -> null
        }
    }

    /**
     * Gets package information for a WhatsApp package.
     */
    fun getPackageInfo(packageName: String): WhatsAppPackageInfo? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            WhatsAppPackageInfo(
                packageName = packageName,
                versionName = packageInfo.versionName ?: "Unknown",
                versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                },
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}

/**
 * Information about an installed WhatsApp package.
 */
data class WhatsAppPackageInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
)