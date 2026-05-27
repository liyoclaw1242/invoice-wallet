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
 *
 * For a gallery photo we try the image as-is first, then a high-contrast binarized
 * variant: thermal-printed e-invoice QR codes are faint/dense and often only decode
 * after binarization — the same trick desktop decoders need on these receipts.
 */
class MlKitInvoiceRecognizer(private val context: Context) : InvoiceRecognizer {

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )
    private val textRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    override suspend fun recognize(image: Uri): RecognitionResult {
        val bitmap = ImagePreprocess.loadBitmap(context, image)
            ?: return recognizeFromFilePath(image)

        var qrLeft: String? = null
        var qrRight: ByteArray? = null

        // Pass 1: the photo as captured.
        scanInput(InputImage.fromBitmap(bitmap, 0)).let { (l, r) ->
            qrLeft = l
            qrRight = r
        }

        // Pass 2: binarized — recovers faint/dense codes the raw image misses.
        if (qrLeft == null || qrRight == null) {
            val binarized = ImagePreprocess.binarize(bitmap)
            scanInput(InputImage.fromBitmap(binarized, 0)).let { (l, r) ->
                if (qrLeft == null) qrLeft = l
                if (qrRight == null) qrRight = r
            }
            binarized.recycle()
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
