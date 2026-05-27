package tw.invoicewallet.datasource.mcpserver

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/** Holds the MCP server's Bearer token. Implementations keep it off plaintext disk. */
interface McpTokenStore {
    /** Returns the token, generating + persisting one on first use. */
    fun getOrCreate(): String

    /** Replaces the token with a fresh one and returns it. */
    fun regenerate(): String
}

/**
 * Keystore-backed token storage (master key in the Android Keystore), mirroring the
 * database passphrase approach. The token is 32 random bytes, URL-safe Base64.
 */
class EncryptedMcpTokenStore(context: Context) : McpTokenStore {

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

    override fun getOrCreate(): String = prefs.getString(KEY_TOKEN, null) ?: regenerate()

    override fun regenerate(): String {
        val token = generate()
        prefs.edit().putString(KEY_TOKEN, token).commit()
        return token
    }

    private fun generate(): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private companion object {
        const val PREFS_NAME = "invoice-wallet-mcp"
        const val KEY_TOKEN = "mcp_token"
        const val TOKEN_BYTES = 32
    }
}
