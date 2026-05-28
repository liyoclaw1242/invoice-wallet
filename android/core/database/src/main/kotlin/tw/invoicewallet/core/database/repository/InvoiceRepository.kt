package tw.invoicewallet.core.database.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceItem

/** Read/write access to invoices, in domain-model terms (no persistence types leak out). */
interface InvoiceRepository {
    suspend fun upsert(invoice: Invoice): Invoice

    suspend fun getById(id: String): Invoice?

    fun observeAll(): Flow<List<Invoice>>

    fun queryByDateRange(from: LocalDate, to: LocalDate): Flow<List<Invoice>>

    suspend fun softDelete(id: String)

    suspend fun search(keyword: String, limit: Int): List<Invoice>

    /**
     * Upserts an invoice together with its line items, replacing any items previously
     * stored for it. Header-only writers (lottery, note/tag edits) keep using [upsert]
     * so they never clobber items. Default delegates to [upsert] for fakes that ignore items.
     */
    suspend fun upsertWithItems(invoice: Invoice, items: List<InvoiceItem>): Invoice = upsert(invoice)

    /** The line items belonging to [invoiceId], ordered by their sequence. */
    suspend fun getItems(invoiceId: String): List<InvoiceItem> = emptyList()

    /** Observes the line items belonging to [invoiceId], ordered by their sequence. */
    fun observeItems(invoiceId: String): Flow<List<InvoiceItem>> = flowOf(emptyList())
}
