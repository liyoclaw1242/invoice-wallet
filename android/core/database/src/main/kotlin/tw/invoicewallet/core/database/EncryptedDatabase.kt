package tw.invoicewallet.core.database

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Builds the SQLCipher-encrypted [InvoiceWalletDatabase]. [passphrase] is the raw key
 * material (see [DatabasePassphraseProvider]); SQLCipher derives the page key from it.
 */
fun buildEncryptedDatabase(
    context: Context,
    passphrase: ByteArray,
    name: String = InvoiceWalletDatabase.DATABASE_NAME,
): InvoiceWalletDatabase {
    SqlCipherNativeLibrary.ensureLoaded()
    return Room.databaseBuilder(context, InvoiceWalletDatabase::class.java, name)
        .openHelperFactory(SupportOpenHelperFactory(passphrase))
        .build()
}

/** Loads `libsqlcipher.so` exactly once per process. */
private object SqlCipherNativeLibrary {
    @Volatile
    private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("sqlcipher")
            loaded = true
        }
    }
}
