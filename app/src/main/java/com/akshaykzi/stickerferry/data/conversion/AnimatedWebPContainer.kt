package com.akshaykzi.stickerferry.data.conversion

/**
 * Builds animated WebP files (RIFF container) from single-frame WebP payloads.
 *
 * Frame payloads are the bytes produced by Android's platform Bitmap WebP encoder
 * (the same encoder that produces the app's WhatsApp-valid static stickers); this
 * class only assembles the container: VP8X (canvas) + ANIM (loop) + ANMF (frames).
 *
 * Byte layouts follow the official spec:
 * https://developers.google.com/speed/webp/docs/riff_container
 *
 * Pure Kotlin (no Android types) so it can be unit-tested on the JVM.
 */
object AnimatedWebPContainer {

    /**
     * Muxes single-frame WebP payloads into one animated WebP.
     *
     * @param frames single-frame WebP bytes, in playback order
     * @param frameDurationMs duration of every frame in milliseconds (>= 8)
     * @param loopCount 0 = loop forever (WhatsApp requirement)
     */
    fun mux(frames: List<ByteArray>, frameDurationMs: Long, loopCount: Int = 0): ByteArray {
        require(frames.isNotEmpty()) { "No frames to mux" }
        require(frameDurationMs >= 8) { "Frame duration below 8ms is not WhatsApp-compatible" }

        val parsedFrames = frames.map { parseFrame(it) }
        val width = parsedFrames.first().width
        val height = parsedFrames.first().height
        require(parsedFrames.all { it.width == width && it.height == height }) {
            "All frames must have the same dimensions"
        }

        val out = ChunkWriter()
        out.writeFourCc("RIFF")
        val riffSizePos = out.reserveSize()
        out.writeFourCc("WEBP")

        // VP8X: canvas + flags. Flags are LSB-first per libwebp's muxer: bit 1 =
        // animation (0x02), bit 4 = alpha (0x10, set only if any frame carries an
        // ALPH chunk). Payload: flags(1) + reserved(3) + width-1(3) + height-1(3).
        val hasAlpha = parsedFrames.any { it.hasAlpha }
        val vp8xFlags = 0x02 or if (hasAlpha) 0x10 else 0
        val vp8x = byteArrayOf(
            vp8xFlags.toByte(), 0, 0, 0,
            u24(width - 1)[0], u24(width - 1)[1], u24(width - 1)[2],
            u24(height - 1)[0], u24(height - 1)[1], u24(height - 1)[2],
        )
        out.writeChunk("VP8X", vp8x)

        // ANIM: background [Blue, Green, Red, Alpha] = transparent, loop forever.
        val anim = byteArrayOf(0, 0, 0, 0, (loopCount and 0xFF).toByte(), ((loopCount shr 8) and 0xFF).toByte())
        out.writeChunk("ANIM", anim)

        val duration = u24(frameDurationMs)
        // ANMF flags byte, LSB-first per libwebp: bit 0 = disposal method (1 =
        // dispose to background), bit 1 = blending method (1 = do not blend).
        // Full-canvas Lottie frames replace the canvas each tick; this combination
        // avoids ghosting of semi-transparent regions and matches libwebp's own
        // output for full frames.
        val anmfFlags = 0x03.toByte()
        for (frame in parsedFrames) {
            val metadata = byteArrayOf(
                0, 0, 0,                       // frame x (in 2-pixel units) = 0
                0, 0, 0,                       // frame y = 0
                u24(frame.width - 1)[0], u24(frame.width - 1)[1], u24(frame.width - 1)[2],
                u24(frame.height - 1)[0], u24(frame.height - 1)[1], u24(frame.height - 1)[2],
                duration[0], duration[1], duration[2],
                anmfFlags,
            )
            val payload = metadata + frame.dataChunks
            out.writeChunk("ANMF", payload)
        }

        out.patchSize(riffSizePos, out.size() - riffSizePos - 4)
        return out.toByteArray()
    }

    /** A single-frame WebP split into its raw frame-data chunks plus dimensions. */
    private class ParsedFrame(val width: Int, val height: Int, val hasAlpha: Boolean, val dataChunks: ByteArray)

    /**
     * Parses a single-frame WebP (RIFF container) into the chunks that go inside an
     * ANMF frame: ALPH and/or VP8 / VP8L. Any VP8X is dropped (the animation gets
     * its own canvas VP8X); its dimensions are read to size the frame header.
     */
    private fun parseFrame(webp: ByteArray): ParsedFrame {
        if (webp.size < 20) throw IllegalArgumentException("Frame too small to be a WebP")
        val fourCc = String(webp, 0, 4, Charsets.US_ASCII)
        val riffSize = readU32(webp, 4)
        val webpTag = String(webp, 8, 4, Charsets.US_ASCII)
        if (fourCc != "RIFF" || webpTag != "WEBP") {
            throw IllegalArgumentException("Not a WebP file")
        }
        // Some encoders write a RIFF size that includes padding inaccurately; trust
        // the actual buffer, clamped to the declared size.
        val end = minOf(webp.size, riffSize + 8)

        var width = -1
        var height = -1
        var hasAlpha = false
        val dataChunks = ChunkWriter()
        var pos = 12
        while (pos + 8 <= end) {
            val id = String(webp, pos, 4, Charsets.US_ASCII)
            val size = readU32(webp, pos + 4)
            val payloadStart = pos + 8
            if (payloadStart + size > webp.size) break
            val payload = webp.copyOfRange(payloadStart, payloadStart + size)
            when (id) {
                "VP8X" -> {
                    // payload: flags(1) reserved(3) width-1(3) height-1(3)
                    width = readU24(webp, payloadStart + 4) + 1
                    height = readU24(webp, payloadStart + 7) + 1
                }
                "ALPH" -> {
                    hasAlpha = true
                    dataChunks.writeChunk("ALPH", payload)
                }
                "VP8 ", "VP8L" -> {
                    if (width < 0) {
                        // Bare VP8/VP8L without VP8X: read dimensions from the
                        // bitstream header.
                        val dims = readBitstreamDimensions(id, payload)
                        width = dims.first
                        height = dims.second
                    }
                    dataChunks.writeChunk(id, payload)
                }
                // ICCP/EXIF/XMP/ANIM etc. are not valid frame data; drop them.
            }
            pos = payloadStart + size + (size and 1) // skip payload + odd-size pad byte
        }

        if (width <= 0 || height <= 0) throw IllegalArgumentException("Frame WebP has no readable dimensions")
        if (dataChunks.size() == 0) throw IllegalArgumentException("Frame WebP has no VP8/VP8L data")
        return ParsedFrame(width, height, hasAlpha, dataChunks.toByteArray())
    }

    /** Reads width/height from a bare VP8 (lossy) or VP8L (lossless) bitstream. */
    private fun readBitstreamDimensions(id: String, payload: ByteArray): Pair<Int, Int> {
        return when (id) {
            "VP8 " -> {
                // keyframe header: 3-byte tag, 0x9D 0x01 0x2A, then 14-bit width, 14-bit height (LE)
                if (payload.size < 10) throw IllegalArgumentException("VP8 payload too small")
                val w = (payload[6].toInt() and 0xFF) or ((payload[7].toInt() and 0x3F) shl 8)
                val h = (payload[8].toInt() and 0xFF) or ((payload[9].toInt() and 0x3F) shl 8)
                w to h
            }
            "VP8L" -> {
                // signature 0x2F, then 14-bit width-1 and 14-bit height-1 packed in LE bytes
                if (payload.size < 5 || (payload[0].toInt() and 0xFF) != 0x2F) {
                    throw IllegalArgumentException("Invalid VP8L payload")
                }
                val bits = readU32(payload, 1)
                val w = (bits and 0x3FFF) + 1
                val h = ((bits shr 14) and 0x3FFF) + 1
                w to h
            }
            else -> throw IllegalArgumentException("Unsupported chunk $id")
        }
    }

    private fun u24(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
    )

    private fun u24(value: Long): ByteArray = u24(value.toInt())

    private fun readU32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun readU24(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16)

    /** Grows a byte buffer and emits spec-compliant chunks (odd sizes get a pad byte). */
    private class ChunkWriter {
        private var buffer = ByteArray(1024)
        private var length = 0

        fun size(): Int = length

        fun write(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size) {
            ensure(count)
            System.arraycopy(bytes, offset, buffer, length, count)
            length += count
        }

        fun writeFourCc(s: String) = write(s.toByteArray(Charsets.US_ASCII))

        /** Writes FourCC + placeholder size + payload (+ pad), returns position of size field. */
        fun writeChunk(id: String, payload: ByteArray) {
            writeFourCc(id)
            val sizePos = reserveSize()
            write(payload)
            patchSize(sizePos, payload.size)
            if (payload.size % 2 == 1) write(PAD)
        }

        fun reserveSize(): Int {
            ensure(4)
            val pos = length
            length += 4
            return pos
        }

        fun patchSize(pos: Int, size: Int) {
            buffer[pos] = (size and 0xFF).toByte()
            buffer[pos + 1] = ((size shr 8) and 0xFF).toByte()
            buffer[pos + 2] = ((size shr 16) and 0xFF).toByte()
            buffer[pos + 3] = ((size shr 24) and 0xFF).toByte()
        }

        fun toByteArray(): ByteArray = buffer.copyOf(length)

        private fun ensure(count: Int) {
            if (length + count <= buffer.size) return
            var newSize = buffer.size * 2
            while (newSize < length + count) newSize *= 2
            buffer = buffer.copyOf(newSize)
        }

        private companion object {
            val PAD = byteArrayOf(0)
        }
    }
}
