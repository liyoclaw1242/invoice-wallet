package tw.invoicewallet.feature.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceItem
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

    // The gallery-picker path keeps its modal Idle→Recognizing→Detected→Saved/Error flow,
    // because a single picked image really does warrant a check before persisting.
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    // The live-camera path runs continuously: no page transitions, just a saved-count chip
    // and transient banner events. Closer to other invoice apps' batch-scan UX.
    private val _session = MutableStateFlow(ScanSessionState())
    val session: StateFlow<ScanSessionState> = _session.asStateFlow()

    private val _events = MutableSharedFlow<ScanEvent>(extraBufferCapacity = EVENT_BUFFER)
    val events: SharedFlow<ScanEvent> = _events.asSharedFlow()

    // The same QR stays in frame for many consecutive frames; the dedup window keeps us
    // from double-saving (or spamming "已存在" banners) for the same invoice within DEDUP_WINDOW_MS.
    private val recentNumbers = mutableMapOf<String, Long>()

    /** An image was picked — recognise it on-device, then build a draft. */
    fun onImageSelected(image: Uri) {
        viewModelScope.launch {
            _state.value = ScanState.Recognizing
            _state.value = try {
                val bundle = buildDraft(recognizer.recognize(image))
                bundle?.let { ScanState.Detected(withMerchantName(it.invoice), it.items) }
                    ?: ScanState.Error("這張照片裡找不到發票資訊，可改用手動輸入。")
            } catch (e: Exception) {
                ScanState.Error("辨識失敗：${e.message ?: "未知錯誤"}")
            }
        }
    }

    /**
     * Live-camera path: parse, dedup within a short window, auto-save (or report duplicate),
     * and emit a banner event. **No** state transition — the camera keeps rolling.
     */
    fun onQrDetected(leftQr: String, rightBytes: ByteArray?) {
        viewModelScope.launch {
            val parsed = try {
                parseTolerant(leftQr, rightBytes)
            } catch (e: EInvoiceQrException) {
                _events.tryEmit(ScanEvent.Failed(e.message ?: "無法解析發票 QR code"))
                return@launch
            }

            val nowMs = System.currentTimeMillis()
            val lastSeenMs = recentNumbers[parsed.invoiceNumber]
            if (lastSeenMs != null && nowMs - lastSeenMs < DEDUP_WINDOW_MS) return@launch
            recentNumbers[parsed.invoiceNumber] = nowMs

            val existing = invoiceRepository.getByInvoiceNumber(parsed.invoiceNumber)
            val merchantName = (
                existing?.merchantName?.takeIf { it.isNotBlank() }
                    ?: merchantDirectory.nameFor(parsed.sellerTaxId)
                    ?: parsed.sellerTaxId
                )
            val label = "$merchantName · NT$${parsed.totalAmount}"

            if (existing != null) {
                // Already in the wallet — don't overwrite the user's notes/tags/lottery state.
                _events.tryEmit(ScanEvent.Duplicate(label = label))
                return@launch
            }

            val draft = parsed.toDraftInvoice(id = newId(), now = clock.now()).copy(merchantName = merchantName)
            val items = parsed.toDraftItems(draft.id)
            invoiceRepository.upsertWithItems(draft, items)
            _session.update { it.copy(savedCount = it.savedCount + 1) }
            _events.tryEmit(ScanEvent.Saved(invoiceId = draft.id, label = label, itemCount = items.size))
        }
    }

    /** Snackbar action: undo the most recently auto-saved invoice. */
    fun undoSavedInvoice(invoiceId: String) {
        viewModelScope.launch {
            invoiceRepository.softDelete(invoiceId)
            _session.update { it.copy(savedCount = (it.savedCount - 1).coerceAtLeast(0)) }
        }
    }

    /** Parses the left code, using the right code when it is sound. A faint/partial
     *  right code must not lose the whole invoice — fall back to the left code alone
     *  (header, total, and the left item are still correct). */
    private fun parseTolerant(leftQr: String, rightBytes: ByteArray?) =
        runCatching { EInvoiceQrParser.parse(leftQr, rightBytes) }
            .getOrElse { EInvoiceQrParser.parse(leftQr, null) }

    /** The user accepted (and possibly edited) the draft — persist it with its items.
     *  The header may have been edited but keeps the same id, so the items decoded into
     *  [ScanState.Detected] still belong to it; re-key them in case the id was regenerated. */
    fun onUserConfirm(invoice: Invoice) {
        val items = (_state.value as? ScanState.Detected)?.items.orEmpty()
            .map { it.copy(invoiceId = invoice.id) }
        viewModelScope.launch {
            val saved = invoiceRepository.upsertWithItems(invoice, items)
            _state.value = ScanState.Saved(saved.id)
        }
    }

    /** Discard the current capture and return to Idle. */
    fun onCancel() {
        _state.value = ScanState.Idle
    }

    /** A draft header plus the line items decoded with it. */
    private data class DraftBundle(val invoice: Invoice, val items: List<InvoiceItem>)

    /** QR is authoritative; fall back to OCR-extracted fields when no QR is present. */
    private fun buildDraft(result: RecognitionResult): DraftBundle? {
        val now = clock.now()
        result.qrLeft?.let { left ->
            runCatching { parseTolerant(left, result.qrRightBytes) }
                .getOrNull()
                ?.let { parsed ->
                    val id = newId()
                    return DraftBundle(parsed.toDraftInvoice(id = id, now = now), parsed.toDraftItems(id))
                }
        }
        val ocr = fieldExtractor.extract(result.ocr).toDraftInvoice(id = newId(), now = now) ?: return null
        return DraftBundle(ocr, emptyList())
    }

    /** Looks up the store name from the seller tax ID when the draft has no name yet. */
    private suspend fun withMerchantName(draft: Invoice): Invoice {
        val taxId = draft.merchantTaxId
        if (draft.merchantName.isNotBlank() || taxId.isNullOrBlank()) return draft
        val name = merchantDirectory.nameFor(taxId)
        return if (name.isNullOrBlank()) draft else draft.copy(merchantName = name)
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private companion object {
        const val DEDUP_WINDOW_MS = 3_000L
        const val EVENT_BUFFER = 4
    }
}

/** Long-lived continuous-scan state — just a chip on the camera. */
data class ScanSessionState(val savedCount: Int = 0)

/** Transient banner event from the continuous-scan path. */
sealed interface ScanEvent {
    data class Saved(val invoiceId: String, val label: String, val itemCount: Int) : ScanEvent
    data class Duplicate(val label: String) : ScanEvent
    data class Failed(val message: String) : ScanEvent
}
