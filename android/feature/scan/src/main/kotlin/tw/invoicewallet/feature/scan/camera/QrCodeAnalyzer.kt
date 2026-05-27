package tw.invoicewallet.feature.scan.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX frame analyzer that watches for a Taiwan e-invoice's two QR codes (left =
 * text header + items, right = dense binary continuation). The codes rarely decode in
 * the same frame at typical distances, so we **accumulate across frames**: keep the
 * first left and right seen, emit as soon as both are captured — or the left alone
 * after a short grace window, so invoices whose dense right code never decodes still
 * scan. Same left/right classification as [MlKitInvoiceRecognizer].
 */
@OptIn(ExperimentalGetImage::class)
class QrCodeAnalyzer(
    maxZoomRatio: Float = 1f,
    private val applyZoom: (Float) -> Unit = {},
    private val onInvoiceQr: (left: String, rightBytes: ByteArray?) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .apply {
                // Auto-zoom: when ML Kit sees a small/dense code, it asks the camera to
                // zoom in so the code resolves. Only useful if the camera can zoom.
                if (maxZoomRatio > 1f) {
                    setZoomSuggestionOptions(
                        ZoomSuggestionOptions.Builder { ratio ->
                            applyZoom(ratio)
                            true
                        }
                            .setMaxSupportedZoomRatio(maxZoomRatio)
                            .build(),
                    )
                }
            }
            .build(),
    )

    @Volatile
    private var emitted = false
    private var left: String? = null
    private var right: ByteArray? = null
    private var framesSinceLeft = 0

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (emitted || mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                barcodes.forEach { barcode ->
                    val bytes = barcode.rawBytes
                    if (bytes != null && bytes.size >= 2 && bytes[0] == MARKER && bytes[1] == MARKER) {
                        if (right == null) right = bytes
                    } else {
                        val value = barcode.rawValue
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
            .addOnCompleteListener { imageProxy.close() }
    }

    private companion object {
        const val MARKER: Byte = 0x2A // '*'

        // Frames to keep trying for the dense right code once the left is captured,
        // before emitting the left alone (~0.5–1s at KEEP_ONLY_LATEST).
        const val GRACE_FRAMES = 12
        val LEFT_CODE = Regex("""^[A-Z]{2}\d{8}\d{7}""")
    }
}
