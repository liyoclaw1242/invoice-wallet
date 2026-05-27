package tw.invoicewallet.feature.invoicelist

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus
import tw.invoicewallet.core.testing.MainDispatcherExtension

class InvoiceListViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    // Fixed to Jan 2026 so the month summary is deterministic (invoices are Jan 2026).
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-01-20T00:00:00Z")
    }

    @Test
    fun `exposes every invoice when no search query is set`() = runTest {
        val viewModel = InvoiceListViewModel(repositoryOf(invoice("a"), invoice("b")), clock)

        val state = viewModel.uiState.first { it.invoices.isNotEmpty() }

        state.invoices.map { it.id } shouldBe listOf("a", "b")
    }

    @Test
    fun `summary totals this month and counts pending lottery`() = runTest {
        val viewModel = InvoiceListViewModel(
            repositoryOf(
                invoice("a", amount = 100),
                invoice("b", amount = 250, status = LotteryStatus.CHECKED_WON),
            ),
            clock,
        )
        val state = viewModel.uiState.first { it.summary.totalCount > 0 }
        state.summary.month shouldBe 1
        state.summary.monthTotal shouldBe 350
        state.summary.monthCount shouldBe 2
        state.summary.pendingLotteryCount shouldBe 1
        state.summary.totalCount shouldBe 2
    }

    @Test
    fun `search filters by merchant name, invoice number or note`() = runTest {
        val viewModel = InvoiceListViewModel(
            repositoryOf(
                invoice("a", merchant = "全聯福利中心"),
                invoice("b", merchant = "誠品書店"),
            ),
            clock,
        )

        viewModel.onQueryChange("誠品")

        val state = viewModel.uiState.first { it.query == "誠品" }
        state.invoices.map { it.id } shouldBe listOf("b")
    }

    private fun repositoryOf(vararg invoices: Invoice): InvoiceRepository = object : InvoiceRepository {
        private val all = MutableStateFlow(invoices.toList())
        override fun observeAll(): Flow<List<Invoice>> = all
        override suspend fun upsert(invoice: Invoice): Invoice = invoice
        override suspend fun getById(id: String): Invoice? = all.value.find { it.id == id }
        override fun queryByDateRange(from: LocalDate, to: LocalDate): Flow<List<Invoice>> = all
        override suspend fun softDelete(id: String) = Unit
        override suspend fun search(keyword: String, limit: Int): List<Invoice> = emptyList()
    }

    private fun invoice(
        id: String,
        merchant: String = "商店",
        amount: Int = 100,
        status: LotteryStatus = LotteryStatus.PENDING,
    ) = Invoice(
        id = id,
        invoiceNumber = "AB1234567$id",
        issueDate = LocalDate(2026, 1, 15),
        issuePeriod = "11502",
        merchantName = merchant,
        merchantTaxId = "12345678",
        buyerTaxId = null,
        carrierIdEncrypted = null,
        totalAmount = amount,
        taxAmount = 5,
        randomCode = "1234",
        category = null,
        source = InvoiceSource.QR_CODE,
        ocrConfidence = null,
        lotteryStatus = status,
        lotteryPrize = null,
        imagePath = null,
        userNote = null,
        userTags = emptyList(),
        createdAt = Instant.parse("2026-01-15T10:00:00Z"),
        updatedAt = Instant.parse("2026-01-15T10:00:00Z"),
        deletedAt = null,
    )
}
