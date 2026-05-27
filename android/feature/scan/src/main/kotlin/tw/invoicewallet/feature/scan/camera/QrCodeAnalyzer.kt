package tw.invoicewallet.feature.scan.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import zxingcpp.BarcodeReader

/**
 * CameraX frame analyzer that decodes a Taiwan e-invoice's two QR codes live with
 * zxing-cpp (tryHarder / tryRotate / tryInvert + internal binarizers) — far more robust
 * on faint, dense, or skewed thermal-printed codes than the default. The two codes
 * rarely decode in the same frame, so we **accumulate across frames**: emit as soon as
 * both are captured, or the left alone after a short grace window. Runs on the caller's
 * single-thread analysis executor; reads are synchronous.
 */
class QrCodeAnalyzer(private val onInvoiceQr: (left: String, rightBytes: ByteArray?) -> Unit) :
    ImageAnalysis.Analyzer {

    private val reader = BarcodeReader().apply {
        options = BarcodeReader.Options(
            formats = setOf(BarcodeReader.Format.QR_CODE),
            tryHarder = true,
            tryRotate = true,
            tryInvert = true,
            tryDownscale = true,
        )
    }

    @Volatile
    private var emitted = false
    private var left: String? = null
    private var right: ByteArray? = null
    private var framesSinceLeft = 0

    override fun analyze(imageProxy: ImageProxy) {
        if (emitted) {
            imageProxy.close()
            return
        }
        val results = try {
            imageProxy.use { reader.read(it) }
        } catch (_: Exception) {
            emptyList()
        }
        results.forEach { result ->
            val bytes = result.bytes
            if (bytes != null && bytes.size >= 2 && bytes[0] == MARKER && bytes[1] == MARKER) {
                if (right == null) right = bytes
            } else {
                val value = result.text
                if (left == null && value != null && LEFT_CODE.containsMatchIn(value)) left = value
            }
        }
        val l = left
        if (l != null) framesSinceLeft++
        if (!emitted && l != null && (right != null || framesSinceLeft >= GRACE_FRAMES)) {
            emitted = true
            onInvoiceQr(l, right)
        }
    }

    private companion object {
        const val MARKER: Byte = 0x2A // '*'

        // Frames to keep trying for the dense right code once the left is captured,
        // before emitting the left alone.
        const val GRACE_FRAMES = 12
        val LEFT_CODE = Regex("""^[A-Z]{2}\d{8}\d{7}""")
    }
}
