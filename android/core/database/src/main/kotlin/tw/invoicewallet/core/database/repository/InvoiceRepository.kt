package tw.invoicewallet.core.database.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import tw.invoicewallet.core.model.Invoice

/** Read/write access to invoices, in domain-model terms (no persistence types leak out). */
interface InvoiceRepository {
    suspend fun upsert(invoice: Invoice): Invoice

    suspend fun getById(id: String): Invoice?

    fun observeAll(): Flow<List<Invoice>>

    fun queryByDateRange(from: LocalDate, to: LocalDate): Flow<List<Invoice>>

    suspend fun softDelete(id: String)

    suspend fun search(keyword: String, limit: Int): List<Invoice>
}
