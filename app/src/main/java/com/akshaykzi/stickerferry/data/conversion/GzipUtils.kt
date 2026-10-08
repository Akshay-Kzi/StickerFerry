package com.akshaykzi.stickerferry.data.conversion

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

/**
 * Utility for Gzip decompression, used for Telegram .tgs (Lottie) stickers.
 */
object GzipUtils {
    fun decompress(compressedBytes: ByteArray): String {
        return GZIPInputStream(ByteArrayInputStream(compressedBytes)).use { gzipInputStream ->
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var len: Int
            while (gzipInputStream.read(buffer).also { len = it } > 0) {
                outputStream.write(buffer, 0, len)
            }
            outputStream.toString("UTF-8")
        }
    }
}
