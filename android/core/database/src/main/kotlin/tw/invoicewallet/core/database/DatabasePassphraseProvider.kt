package tw.invoicewallet.core.database

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Supplies a stable random passphrase for the SQLCipher database. The passphrase is
 * generated once and persisted in [EncryptedSharedPreferences], whose master key
 * lives in the Android Keystore (hardware-backed where available) — so the raw key
 * never sits in plaintext on disk.
 */
class DatabasePassphraseProvider(private val context: Context) {

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

    /** Returns the existing passphrase, generating + storing one on first call. */
    fun getOrCreatePassphrase(): ByteArray {
        prefs.getString(KEY_PASSPHRASE, null)?.let {
            return Base64.decode(it, Base64.NO_WRAP)
        }
        val generated = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PASSPHRASE, Base64.encodeToString(generated, Base64.NO_WRAP))
            .commit()
        return generated
    }

    companion object {
        private const val PREFS_NAME = "invoice-wallet-keystore"
        private const val KEY_PASSPHRASE = "db_passphrase"
        private const val PASSPHRASE_BYTES = 32
    }
}
