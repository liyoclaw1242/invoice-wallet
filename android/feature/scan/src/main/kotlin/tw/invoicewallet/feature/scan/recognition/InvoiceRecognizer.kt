package tw.invoicewallet.feature.scan.recognition

import android.net.Uri
import tw.invoicewallet.feature.scan.ocr.RecognizedText

/** Raw recognition output for an invoice image: detected QR codes + OCR text. */
class RecognitionResult(
    val qrLeft: String? = null,
    val qrRightBytes: ByteArray? = null,
    val ocr: RecognizedText = RecognizedText(emptyList()),
)

/**
 * Turns an invoice image into [RecognitionResult]. Implemented on-device with ML Kit
 * (no network); behind an interface so the ViewModel is testable with a fake.
 */
interface InvoiceRecognizer {
    suspend fun recognize(image: Uri): RecognitionResult
}
