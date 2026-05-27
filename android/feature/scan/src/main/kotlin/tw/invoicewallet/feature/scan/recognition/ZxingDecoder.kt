package tw.invoicewallet.feature.scan.recognition

import android.graphics.Bitmap
import zxingcpp.BarcodeReader

/**
 * QR decoding via zxing-cpp — markedly more robust than ML Kit on hard, skewed, or
 * faint thermal-printed e-invoice codes (tryHarder / tryRotate / tryInvert / downscale).
 * Classifies the two e-invoice symbols: left = text header+items, right = `**`-prefixed
 * binary continuation (returned as raw bytes).
 */
class ZxingDecoder {

    private val reader = BarcodeReader().apply {
        options = BarcodeReader.Options(
            formats = setOf(BarcodeReader.Format.QR_CODE),
            tryHarder = true,
            tryRotate = true,
            tryInvert = true,
            tryDownscale = true,
        )
    }

    /** Returns (leftCode, rightBytes) found in [bitmap]; either may be null. */
    fun scan(bitmap: Bitmap): Pair<String?, ByteArray?> {
        val results = runCatching { reader.read(bitmap) }.getOrDefault(emptyList())
        var left: String? = null
        var right: ByteArray? = null
        results.forEach { result ->
            val bytes = result.bytes
            if (bytes != null && bytes.size >= 2 && bytes[0] == MARKER && bytes[1] == MARKER) {
                right = bytes
            } else {
                val text = result.text
                if (text != null && LEFT_CODE.containsMatchIn(text)) left = text
            }
        }
        return left to right
    }

    private companion object {
        const val MARKER: Byte = 0x2A // '*'
        val LEFT_CODE = Regex("""^[A-Z]{2}\d{8}\d{7}""")
    }
}
