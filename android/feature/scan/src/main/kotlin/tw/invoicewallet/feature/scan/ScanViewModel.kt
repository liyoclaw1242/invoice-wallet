package tw.invoicewallet.feature.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.feature.scan.ocr.InvoiceFieldExtractor
import tw.invoicewallet.feature.scan.qr.EInvoiceQrException
import tw.invoicewallet.feature.scan.qr.EInvoiceQrParser
import tw.invoicewallet.feature.scan.recognition.InvoiceRecognizer
import tw.invoicewallet.feature.scan.recognition.RecognitionResult
import java.util.UUID
import javax.inject.Inject

/** Drives scan → confirm → save, from either a picked image or a pasted QR string. */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val recognizer: InvoiceRecognizer,
    private val clock: Clock,
) : ViewModel() {

    private val fieldExtractor = InvoiceFieldExtractor()

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** An image was picked — recognise it on-device, then build a draft. */
    fun onImageSelected(image: Uri) {
        viewModelScope.launch {
            _state.value = ScanState.Recognizing
            _state.value = try {
                val draft = buildDraft(recognizer.recognize(image))
                draft?.let { ScanState.Detected(it) }
                    ?: ScanState.Error("這張照片裡找不到發票資訊，可改用手動輸入。")
            } catch (e: Exception) {
                ScanState.Error("辨識失敗：${e.message ?: "未知錯誤"}")
            }
        }
    }

    /** A QR string was entered/detected directly; parse it into an editable draft. */
    fun onQrDetected(leftQr: String, rightBytes: ByteArray?) {
        _state.value = try {
            val parsed = EInvoiceQrParser.parse(leftQr, rightBytes)
            ScanState.Detected(parsed.toDraftInvoice(id = newId(), now = clock.now()))
        } catch (e: EInvoiceQrException) {
            ScanState.Error(e.message ?: "無法解析發票 QR code")
        }
    }

    /** The user accepted (and possibly edited) the draft — persist it. */
    fun onUserConfirm(invoice: Invoice) {
        viewModelScope.launch {
            val saved = invoiceRepository.upsert(invoice)
            _state.value = ScanState.Saved(saved.id)
        }
    }

    /** Discard the current capture and return to Idle. */
    fun onCancel() {
        _state.value = ScanState.Idle
    }

    /** QR is authoritative; fall back to OCR-extracted fields when no QR is present. */
    private fun buildDraft(result: RecognitionResult): Invoice? {
        val now = clock.now()
        result.qrLeft?.let { left ->
            runCatching { EInvoiceQrParser.parse(left, result.qrRightBytes) }
                .getOrNull()
                ?.let { return it.toDraftInvoice(id = newId(), now = now) }
        }
        return fieldExtractor.extract(result.ocr).toDraftInvoice(id = newId(), now = now)
    }

    private fun newId(): String = UUID.randomUUID().toString()
}
