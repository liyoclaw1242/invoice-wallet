package tw.invoicewallet.feature.scan.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX frame analyzer that watches for a Taiwan e-invoice's QR codes in the live
 * preview. Emits once, the first time a left code is seen (with the right code if it
 * is in the same frame). Same left/right classification as [MlKitInvoiceRecognizer].
 */
@OptIn(ExperimentalGetImage::class)
class QrCodeAnalyzer(private val onInvoiceQr: (left: String, rightBytes: ByteArray?) -> Unit) :
    ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
    )

    @Volatile
    private var emitted = false

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (emitted || mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                var left: String? = null
                var right: ByteArray? = null
                barcodes.forEach { barcode ->
                    val bytes = barcode.rawBytes
                    if (bytes != null && bytes.size >= 2 && bytes[0] == MARKER && bytes[1] == MARKER) {
                        right = bytes
                    } else {
                        val value = barcode.rawValue
                        if (value != null && LEFT_CODE.containsMatchIn(value)) left = value
                    }
                }
                val detectedLeft = left
                if (!emitted && detectedLeft != null) {
                    emitted = true
                    onInvoiceQr(detectedLeft, right)
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private companion object {
        const val MARKER: Byte = 0x2A // '*'
        val LEFT_CODE = Regex("""^[A-Z]{2}\d{8}\d{7}""")
    }
}
