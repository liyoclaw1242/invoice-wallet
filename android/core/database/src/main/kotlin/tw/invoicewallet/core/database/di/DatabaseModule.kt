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
import tw.invoicewallet.core.database.dao.AuthGrantDao
import tw.invoicewallet.core.database.dao.InvoiceDao
import tw.invoicewallet.core.database.dao.InvoiceItemDao
import tw.invoicewallet.core.database.dao.LotteryNumberDao
import tw.invoicewallet.core.database.dao.QueryAuditLogDao
import tw.invoicewallet.core.database.repository.AuthGrantRepository
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.database.repository.LotteryRepository
import tw.invoicewallet.core.database.repository.RoomAuthGrantRepository
import tw.invoicewallet.core.database.repository.RoomInvoiceRepository
import tw.invoicewallet.core.database.repository.RoomLotteryRepository
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
    fun provideInvoiceItemDao(database: InvoiceWalletDatabase): InvoiceItemDao = database.invoiceItemDao()

    @Provides
    fun provideLotteryNumberDao(database: InvoiceWalletDatabase): LotteryNumberDao = database.lotteryNumberDao()

    @Provides
    fun provideAuthGrantDao(database: InvoiceWalletDatabase): AuthGrantDao = database.authGrantDao()

    @Provides
    fun provideQueryAuditLogDao(database: InvoiceWalletDatabase): QueryAuditLogDao = database.queryAuditLogDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.System

    @Provides
    @Singleton
    fun provideInvoiceRepository(
        invoiceDao: InvoiceDao,
        invoiceItemDao: InvoiceItemDao,
        clock: Clock,
    ): InvoiceRepository = RoomInvoiceRepository(invoiceDao, invoiceItemDao, clock)

    @Provides
    @Singleton
    fun provideLotteryRepository(lotteryNumberDao: LotteryNumberDao): LotteryRepository =
        RoomLotteryRepository(lotteryNumberDao)

    @Provides
    @Singleton
    fun provideAuthGrantRepository(
        authGrantDao: AuthGrantDao,
        queryAuditLogDao: QueryAuditLogDao,
        clock: Clock,
    ): AuthGrantRepository = RoomAuthGrantRepository(authGrantDao, queryAuditLogDao, clock)
}
