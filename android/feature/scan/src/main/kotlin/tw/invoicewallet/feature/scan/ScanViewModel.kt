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
import tw.invoicewallet.feature.scan.merchant.MerchantDirectory
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
    private val merchantDirectory: MerchantDirectory,
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
                val draft = buildDraft(recognizer.recognize(image))?.let { withMerchantName(it) }
                draft?.let { ScanState.Detected(it) }
                    ?: ScanState.Error("這張照片裡找不到發票資訊，可改用手動輸入。")
            } catch (e: Exception) {
                ScanState.Error("辨識失敗：${e.message ?: "未知錯誤"}")
            }
        }
    }

    /** A QR string was entered/detected directly; parse it into an editable draft. */
    fun onQrDetected(leftQr: String, rightBytes: ByteArray?) {
        viewModelScope.launch {
            _state.value = ScanState.Recognizing
            _state.value = try {
                val parsed = parseTolerant(leftQr, rightBytes)
                ScanState.Detected(withMerchantName(parsed.toDraftInvoice(id = newId(), now = clock.now())))
            } catch (e: EInvoiceQrException) {
                ScanState.Error(e.message ?: "無法解析發票 QR code")
            }
        }
    }

    /** Parses the left code, using the right code when it is sound. A faint/partial
     *  right code must not lose the whole invoice — fall back to the left code alone
     *  (header, total, and the left item are still correct). */
    private fun parseTolerant(leftQr: String, rightBytes: ByteArray?) =
        runCatching { EInvoiceQrParser.parse(leftQr, rightBytes) }
            .getOrElse { EInvoiceQrParser.parse(leftQr, null) }

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
            runCatching { parseTolerant(left, result.qrRightBytes) }
                .getOrNull()
                ?.let { return it.toDraftInvoice(id = newId(), now = now) }
        }
        return fieldExtractor.extract(result.ocr).toDraftInvoice(id = newId(), now = now)
    }

    /** Looks up the store name from the seller tax ID when the draft has no name yet. */
    private suspend fun withMerchantName(draft: Invoice): Invoice {
        val taxId = draft.merchantTaxId
        if (draft.merchantName.isNotBlank() || taxId.isNullOrBlank()) return draft
        val name = merchantDirectory.nameFor(taxId)
        return if (name.isNullOrBlank()) draft else draft.copy(merchantName = name)
    }

    private fun newId(): String = UUID.randomUUID().toString()
}
