package tw.invoicewallet.core.database.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import tw.invoicewallet.core.database.dao.InvoiceDao
import tw.invoicewallet.core.database.dao.InvoiceItemDao
import tw.invoicewallet.core.database.mapper.asEntity
import tw.invoicewallet.core.database.mapper.asExternalModel
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceItem

class RoomInvoiceRepository(
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val clock: Clock = Clock.System,
) : InvoiceRepository {

    override suspend fun upsert(invoice: Invoice): Invoice {
        invoiceDao.upsert(invoice.asEntity())
        return invoice
    }

    override suspend fun upsertWithItems(invoice: Invoice, items: List<InvoiceItem>): Invoice {
        invoiceDao.upsert(invoice.asEntity())
        invoiceItemDao.deleteByInvoiceId(invoice.id)
        if (items.isNotEmpty()) invoiceItemDao.insertAll(items.map { it.asEntity() })
        return invoice
    }

    override suspend fun getItems(invoiceId: String): List<InvoiceItem> =
        invoiceItemDao.getByInvoiceId(invoiceId).map { it.asExternalModel() }

    override fun observeItems(invoiceId: String): Flow<List<InvoiceItem>> =
        invoiceItemDao.observeByInvoiceId(invoiceId).map { rows -> rows.map { it.asExternalModel() } }

    override suspend fun getById(id: String): Invoice? = invoiceDao.getById(id)?.asExternalModel()

    override suspend fun getByInvoiceNumber(invoiceNumber: String): Invoice? =
        invoiceDao.findByInvoiceNumber(invoiceNumber)?.asExternalModel()

    override fun observeAll(): Flow<List<Invoice>> =
        invoiceDao.observeAll().map { rows -> rows.map { it.asExternalModel() } }

    override fun queryByDateRange(from: LocalDate, to: LocalDate): Flow<List<Invoice>> =
        invoiceDao.observeByDateRange(from, to).map { rows -> rows.map { it.asExternalModel() } }

    override suspend fun softDelete(id: String) = invoiceDao.softDelete(id, clock.now())

    override suspend fun search(keyword: String, limit: Int): List<Invoice> =
        invoiceDao.search(keyword, limit).map { it.asExternalModel() }
}
