package tw.invoicewallet.core.database.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.datetime.Clock
import tw.invoicewallet.core.database.DatabasePassphraseProvider
import tw.invoicewallet.core.database.InvoiceWalletDatabase
import tw.invoicewallet.core.database.buildEncryptedDatabase
import tw.invoicewallet.core.database.dao.InvoiceDao
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.database.repository.RoomInvoiceRepository
import javax.inject.Singleton

/** Wires the SQLCipher-encrypted database and repositories into the Hilt graph. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePassphraseProvider(@ApplicationContext context: Context): DatabasePassphraseProvider =
        DatabasePassphraseProvider(context)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider,
    ): InvoiceWalletDatabase = buildEncryptedDatabase(context, passphraseProvider.getOrCreatePassphrase())

    @Provides
    fun provideInvoiceDao(database: InvoiceWalletDatabase): InvoiceDao = database.invoiceDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.System

    @Provides
    @Singleton
    fun provideInvoiceRepository(invoiceDao: InvoiceDao, clock: Clock): InvoiceRepository =
        RoomInvoiceRepository(invoiceDao, clock)
}
