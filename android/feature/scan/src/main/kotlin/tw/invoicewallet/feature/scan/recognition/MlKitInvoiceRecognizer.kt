package tw.invoicewallet.feature.scan.recognition

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import tw.invoicewallet.feature.scan.ocr.RecognizedText

/**
 * On-device recognition: QR via zxing-cpp (robust to faint/skewed thermal-print codes),
 * with an ML Kit barcode fallback, plus ML Kit Chinese OCR. Runs entirely locally — the
 * image never leaves the phone. For a hard photo we also try a binarized variant, the
 * trick these dense receipt codes need to decode at all.
 */
class MlKitInvoiceRecognizer(private val context: Context) : InvoiceRecognizer {

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )
    private val textRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val zxing = ZxingDecoder()

    override suspend fun recognize(image: Uri): RecognitionResult {
        val bitmap = ImagePreprocess.loadBitmap(context, image)
            ?: return recognizeFromFilePath(image)

        var qrLeft: String? = null
        var qrRight: ByteArray? = null
        fun merge(found: Pair<String?, ByteArray?>) {
            if (qrLeft == null) qrLeft = found.first
            if (qrRight == null) qrRight = found.second
        }
        fun missing() = qrLeft == null || qrRight == null

        // CPU-bound decoding off the main thread. zxing-cpp first (strongest), then a
        // binarized variant, then ML Kit barcode as a second engine.
        withContext(Dispatchers.Default) {
            var binarized: Bitmap? = null
            try {
                merge(zxing.scan(bitmap))
                if (missing()) {
                    binarized = ImagePreprocess.binarize(bitmap)
                    merge(zxing.scan(binarized))
                }
                if (missing()) merge(scanInput(InputImage.fromBitmap(bitmap, 0)))
                if (missing()) {
                    val bw = binarized ?: ImagePreprocess.binarize(bitmap).also { binarized = it }
                    merge(scanInput(InputImage.fromBitmap(bw, 0)))
                }
            } finally {
                binarized?.recycle()
            }
        }

        val text = textRecognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val lines = text.textBlocks.flatMap { block -> block.lines.map { it.text } }
        bitmap.recycle()
        return RecognitionResult(qrLeft = qrLeft, qrRightBytes = qrRight, ocr = RecognizedText(lines))
    }

    /** Fallback when the bitmap can't be loaded for preprocessing. */
    private suspend fun recognizeFromFilePath(image: Uri): RecognitionResult {
        val input = InputImage.fromFilePath(context, image)
        val (left, right) = scanInput(input)
        val text = textRecognizer.process(input).await()
        val lines = text.textBlocks.flatMap { block -> block.lines.map { it.text } }
        return RecognitionResult(qrLeft = left, qrRightBytes = right, ocr = RecognizedText(lines))
    }

    private suspend fun scanInput(input: InputImage): Pair<String?, ByteArray?> {
        val barcodes = barcodeScanner.process(input).await()
        var left: String? = null
        var right: ByteArray? = null
        barcodes.forEach { barcode ->
            val bytes = barcode.rawBytes
            if (bytes != null && bytes.size >= 2 && bytes[0] == RIGHT_MARKER && bytes[1] == RIGHT_MARKER) {
                right = bytes
            } else {
                val value = barcode.rawValue
                if (value != null && LEFT_CODE.containsMatchIn(value)) left = value
            }
        }
        return left to right
    }

    private companion object {
        const val RIGHT_MARKER: Byte = 0x2A // '*'

        // Left code begins with invoice no (2 letters + 8 digits) followed by the 7-digit ROC date.
        val LEFT_CODE = Regex("""^[A-Z]{2}\d{8}\d{7}""")
    }
}
