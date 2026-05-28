package tw.invoicewallet.feature.scan

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceItem

/** In-memory InvoiceRepository for ViewModel tests. */
class FakeInvoiceRepository : InvoiceRepository {
    private val state = MutableStateFlow<List<Invoice>>(emptyList())
    private val itemsByInvoice = mutableMapOf<String, List<InvoiceItem>>()

    override suspend fun upsert(invoice: Invoice): Invoice {
        state.value = state.value.filterNot { it.id == invoice.id } + invoice
        return invoice
    }

    override suspend fun upsertWithItems(invoice: Invoice, items: List<InvoiceItem>): Invoice {
        itemsByInvoice[invoice.id] = items
        return upsert(invoice)
    }

    override suspend fun getItems(invoiceId: String): List<InvoiceItem> = itemsByInvoice[invoiceId].orEmpty()

    override suspend fun getById(id: String): Invoice? = state.value.find { it.id == id }

    override fun observeAll(): Flow<List<Invoice>> = state

    override fun queryByDateRange(from: LocalDate, to: LocalDate): Flow<List<Invoice>> =
        state.map { rows -> rows.filter { it.issueDate >= from && it.issueDate <= to } }

    override suspend fun softDelete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun search(keyword: String, limit: Int): List<Invoice> =
        state.value.filter { it.merchantName.contains(keyword) }.take(limit)
}
