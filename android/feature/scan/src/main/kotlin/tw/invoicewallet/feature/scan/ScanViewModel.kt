package tw.invoicewallet.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.feature.scan.qr.EInvoiceQrException
import tw.invoicewallet.feature.scan.qr.EInvoiceQrParser
import java.util.UUID

/**
 * Drives scan → confirm → save. The QR path is wired here; the OCR path
 * (`onOcrResult`) arrives with T2.2 once ML Kit extraction exists.
 */
class ScanViewModel(
    private val invoiceRepository: InvoiceRepository,
    private val clock: Clock = Clock.System,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** A QR pair was detected; parse it into an editable draft (or surface an error). */
    fun onQrDetected(leftQr: String, rightBytes: ByteArray?) {
        _state.value = try {
            val parsed = EInvoiceQrParser.parse(leftQr, rightBytes)
            ScanState.Detected(parsed.toDraftInvoice(id = idGenerator(), now = clock.now()))
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

    /** Discard the current capture and return to the live camera state. */
    fun onCancel() {
        _state.value = ScanState.Idle
    }
}
