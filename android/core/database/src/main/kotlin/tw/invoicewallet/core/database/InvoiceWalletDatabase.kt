package tw.invoicewallet.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import tw.invoicewallet.core.database.dao.InvoiceDao
import tw.invoicewallet.core.database.entity.AuthGrantEntity
import tw.invoicewallet.core.database.entity.InvoiceEntity
import tw.invoicewallet.core.database.entity.InvoiceItemEntity
import tw.invoicewallet.core.database.entity.LotteryNumberEntity
import tw.invoicewallet.core.database.entity.QueryAuditLogEntity

@Database(
    entities = [
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        LotteryNumberEntity::class,
        AuthGrantEntity::class,
        QueryAuditLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class InvoiceWalletDatabase : RoomDatabase() {
    abstract fun invoiceDao(): InvoiceDao

    companion object {
        const val DATABASE_NAME = "invoice-wallet.db"
    }
}
