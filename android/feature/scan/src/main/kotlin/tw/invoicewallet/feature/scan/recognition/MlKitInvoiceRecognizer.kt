package tw.invoicewallet.feature.scan.recognition

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import tw.invoicewallet.feature.scan.ocr.RecognizedText

/**
 * On-device recognition via ML Kit: QR barcode scanning + Chinese OCR. Runs entirely
 * locally — the image never leaves the phone.
 */
class MlKitInvoiceRecognizer(private val context: Context) : InvoiceRecognizer {

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )
    private val textRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    override suspend fun recognize(image: Uri): RecognitionResult {
        val input = InputImage.fromFilePath(context, image)
        val barcodes = barcodeScanner.process(input).await()
        val text = textRecognizer.process(input).await()

        var qrLeft: String? = null
        var qrRight: ByteArray? = null
        barcodes.forEach { barcode ->
            val bytes = barcode.rawBytes
            if (bytes != null && bytes.size >= 2 && bytes[0] == RIGHT_MARKER && bytes[1] == RIGHT_MARKER) {
                qrRight = bytes
            } else {
                val value = barcode.rawValue
                if (value != null && LEFT_CODE.containsMatchIn(value)) qrLeft = value
            }
        }

        val lines = text.textBlocks.flatMap { block -> block.lines.map { it.text } }
        return RecognitionResult(qrLeft = qrLeft, qrRightBytes = qrRight, ocr = RecognizedText(lines))
    }

    private companion object {
        const val RIGHT_MARKER: Byte = 0x2A // '*'

        // Left code begins with invoice no (2 letters + 8 digits) followed by the 7-digit ROC date.
        val LEFT_CODE = Regex("""^[A-Z]{2}\d{8}\d{7}""")
    }
}
