package tw.invoicewallet.datasource.relayclient

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Holds the relay URL + device_secret from pairing. Sensitive → Keystore-encrypted. */
interface RelayDeviceStore {
    fun relayUrl(): String?

    fun deviceSecret(): String?

    fun save(relayUrl: String, deviceSecret: String)

    fun clear()

    val isPaired: Boolean
        get() = relayUrl() != null && deviceSecret() != null
}

class EncryptedRelayDeviceStore(context: Context) : RelayDeviceStore {

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

    override fun relayUrl(): String? = prefs.getString(KEY_URL, null)

    override fun deviceSecret(): String? = prefs.getString(KEY_SECRET, null)

    override fun save(relayUrl: String, deviceSecret: String) {
        prefs.edit().putString(KEY_URL, relayUrl).putString(KEY_SECRET, deviceSecret).commit()
    }

    override fun clear() {
        prefs.edit().remove(KEY_URL).remove(KEY_SECRET).commit()
    }

    private companion object {
        const val PREFS_NAME = "invoice-wallet-relay"
        const val KEY_URL = "relay_url"
        const val KEY_SECRET = "device_secret"
    }
}
