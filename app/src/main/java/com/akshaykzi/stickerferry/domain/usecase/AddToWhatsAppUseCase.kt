package com.akshaykzi.stickerferry.domain.usecase

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.akshaykzi.stickerferry.domain.model.StickerPack
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for adding a sticker pack to WhatsApp.
 *
 * This use case handles:
 * - Checking WhatsApp installation
 * - Building the intent to add the pack
 * - Handling the result from WhatsApp
 */
@Singleton
class AddToWhatsAppUseCase @Inject constructor(
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
     * Checks if WhatsApp is installed on the device.
     */
    fun isWhatsAppInstalled(): Boolean {
        return isPackageInstalled(WHATSAPP_PACKAGE) || isPackageInstalled(WHATSAPP_BUSINESS_PACKAGE)
    }

    /**
     * Checks if WhatsApp Business is installed on the device.
     */
    fun isWhatsAppBusinessInstalled(): Boolean {
        return isPackageInstalled(WHATSAPP_BUSINESS_PACKAGE)
    }

    /**
     * Gets the list of installed WhatsApp packages.
     */
    fun getInstalledWhatsAppPackages(): List<String> {
        val packages = mutableListOf<String>()
        if (isPackageInstalled(WHATSAPP_PACKAGE)) {
            packages.add(WHATSAPP_PACKAGE)
        }
        if (isPackageInstalled(WHATSAPP_BUSINESS_PACKAGE)) {
            packages.add(WHATSAPP_BUSINESS_PACKAGE)
        }
        return packages
    }

    /**
     * Creates an intent to add a sticker pack to WhatsApp.
     *
     * @param pack The sticker pack to add
     * @param authority The ContentProvider authority
     * @param targetPackage The WhatsApp package to target
     * @return Result containing the intent or an error
     */
    fun createAddPackIntent(
        pack: StickerPack,
        authority: String,
        targetPackage: String? = null,
    ): Result<Intent> {
        if (!isWhatsAppInstalled()) {
            return Result.failure(
                WhatsAppNotInstalledException("WhatsApp is not installed on this device")
            )
        }

        val packages = if (targetPackage != null) {
            listOf(targetPackage)
        } else {
            getInstalledWhatsAppPackages()
        }

        if (packages.isEmpty()) {
            return Result.failure(
                WhatsAppNotInstalledException("No WhatsApp packages found")
            )
        }

        // Try each package until one works
        for (packageName in packages) {
            try {
                val intent = Intent(ENABLE_STICKER_PACK_ACTION).apply {
                    putExtra(EXTRA_STICKER_PACK_ID, pack.identifier)
                    putExtra(EXTRA_STICKER_PACK_AUTHORITY, authority)
                    putExtra(EXTRA_STICKER_PACK_NAME, pack.name)
                    setPackage(packageName)
                }
                return Result.success(intent)
            } catch (e: Exception) {
                // Try next package
                continue
            }
        }

        return Result.failure(
            WhatsAppNotInstalledException("Failed to create intent for any WhatsApp package")
        )
    }

    /**
     * Validates the pack for WhatsApp handoff.
     *
     * @return List of validation errors, empty if valid
     */
    fun validateForHandoff(pack: StickerPack): List<String> {
        val errors = mutableListOf<String>()

        if (!isWhatsAppInstalled()) {
            errors.add("WhatsApp is not installed on this device")
        }

        val packErrors = pack.validate()
        errors.addAll(packErrors)

        return errors
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
 * Exception thrown when WhatsApp is not installed.
 */
class WhatsAppNotInstalledException(message: String) : Exception(message)