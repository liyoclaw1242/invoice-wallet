package tw.invoicewallet.feature.settings

/**
 * Stores the user's mobile-barcode carrier (手機條碼載具) — sensitive, so the
 * implementation keeps it in Keystore-backed encrypted storage, never plaintext.
 */
interface CarrierCodeStore {
    /** The saved carrier code, or null if the user hasn't set one. */
    fun get(): String?

    fun set(value: String)

    fun clear()
}
