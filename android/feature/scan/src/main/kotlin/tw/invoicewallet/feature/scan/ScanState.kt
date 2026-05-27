package tw.invoicewallet.feature.scan

import tw.invoicewallet.core.model.Invoice

/** UI state for the scan-and-confirm flow. */
sealed interface ScanState {
    /** Nothing captured yet (camera live). */
    data object Idle : ScanState

    /** An image was picked and is being recognised on-device. */
    data object Recognizing : ScanState

    /** A QR code was parsed into an editable draft awaiting the user's confirmation. */
    data class Detected(val draft: Invoice) : ScanState

    /** The confirmed invoice was persisted. */
    data class Saved(val invoiceId: String) : ScanState

    /** Capture/parse failed; [message] is user-facing. */
    data class Error(val message: String) : ScanState
}
