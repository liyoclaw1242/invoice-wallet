package tw.invoicewallet.feature.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keeps the carrier barcode in [EncryptedSharedPreferences] — the master key lives in
 * the Android Keystore, so the code never sits in plaintext on disk. Mirrors the
 * approach used for the database passphrase (ARCHITECTURE §5.2).
 */
class EncryptedCarrierCodeStore(context: Context) : CarrierCodeStore {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun get(): String? = prefs.getString(KEY_CARRIER, null)

    override fun set(value: String) {
        prefs.edit().putString(KEY_CARRIER, value).commit()
    }

    override fun clear() {
        prefs.edit().remove(KEY_CARRIER).commit()
    }

    private companion object {
        const val PREFS_NAME = "invoice-wallet-carrier"
        const val KEY_CARRIER = "carrier_code"
    }
}
