package tw.invoicewallet.core.database.repository

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class RoomInvoiceRepositoryTest {

    private val now = Instant.parse("2026-03-01T00:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = now
    }
    private val dao = FakeInvoiceDao()
    private val repository = RoomInvoiceRepository(dao, fixedClock)

    @Test
    fun `upsert stores the invoice and getById maps it back to the domain model`() = runTest {
        val invoice = invoice(id = "inv-1")

        repository.upsert(invoice)

        repository.getById("inv-1") shouldBe invoice
    }

    @Test
    fun `softDelete stamps the clock's time and removes it from observeAll`() = runTest {
        repository.upsert(invoice(id = "inv-1"))

        repository.softDelete("inv-1")

        repository.getById("inv-1")?.deletedAt shouldBe now
        repository.observeAll().first() shouldBe emptyList()
    }

    @Test
    fun `queryByDateRange returns only invoices within the range, newest first`() = runTest {
        repository.upsert(invoice(id = "a", invoiceNumber = "AA00000001", issueDate = LocalDate(2026, 1, 10)))
        repository.upsert(invoice(id = "b", invoiceNumber = "BB00000002", issueDate = LocalDate(2026, 1, 20)))
        repository.upsert(invoice(id = "c", invoiceNumber = "CC00000003", issueDate = LocalDate(2026, 2, 5)))

        val ids = repository.queryByDateRange(LocalDate(2026, 1, 1), LocalDate(2026, 1, 31))
            .first()
            .map { it.id }

        ids shouldBe listOf("b", "a")
    }
}
