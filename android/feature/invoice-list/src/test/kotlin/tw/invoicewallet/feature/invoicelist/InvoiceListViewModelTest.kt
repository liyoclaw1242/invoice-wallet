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
    fun `shiftFocusedMonth steps the hero summary back and forward, clamped to data bounds`() = runTest {
        // Two months of data: Nov 2025 and Jan 2026 (skipping Dec for variety).
        val viewModel = InvoiceListViewModel(
            repositoryOf(
                invoice("nov", amount = 500, on = LocalDate(2025, 11, 5)),
                invoice("jan1", amount = 100, on = LocalDate(2026, 1, 10)),
                invoice("jan2", amount = 250, on = LocalDate(2026, 1, 20)),
            ),
            clock,
        )
        // Focus defaults to today's month (Jan 2026).
        viewModel.uiState.first { it.summary.totalCount > 0 }.summary.let { s ->
            s.month shouldBe 1
            s.year shouldBe 2026
            s.monthTotal shouldBe 350
            s.canGoPrev shouldBe true
            s.canGoNext shouldBe false // already at today
        }

        // Step back to Dec 2025 — no invoices, totals zero, can still go back/forward.
        viewModel.shiftFocusedMonth(-1)
        viewModel.uiState.first { it.summary.month == 12 }.summary.let { s ->
            s.year shouldBe 2025
            s.monthTotal shouldBe 0
            s.monthCount shouldBe 0
            s.canGoPrev shouldBe true
            s.canGoNext shouldBe true
        }

        // Step back again to Nov 2025 — the earliest invoice month, prev now disabled.
        viewModel.shiftFocusedMonth(-1)
        viewModel.uiState.first { it.summary.month == 11 }.summary.let { s ->
            s.year shouldBe 2025
            s.monthTotal shouldBe 500
            s.canGoPrev shouldBe false
            s.canGoNext shouldBe true
            s.monthLabel shouldBe "2025 年 11 月"
        }

        // Further back is clamped — focus stays at Nov 2025.
        viewModel.shiftFocusedMonth(-1)
        viewModel.uiState.first().summary.month shouldBe 11
    }

    @Test
    fun `category facets count guessed categories and onCategoryChange filters the list`() = runTest {
        val viewModel = InvoiceListViewModel(
            repositoryOf(
                invoice("a", merchant = "麥當勞"),
                invoice("b", merchant = "肯德基"),
                invoice("c", merchant = "全聯福利中心"),
                invoice("d", merchant = "福懋加油站"),
                invoice("e", merchant = "未知小店"),
            ),
            clock,
        )

        val initial = viewModel.uiState.first { it.invoices.size == 5 }
        initial.categories.map { it.slug to it.count }.toSet() shouldBe setOf(
            "food" to 2, // 麥當勞 + 肯德基
            "convstore" to 1, // 全聯
            "transit" to 1, // 加油站
        )
        initial.selectedCategory shouldBe null

        viewModel.onCategoryChange("food")

        val filtered = viewModel.uiState.first { it.selectedCategory == "food" }
        filtered.invoices.map { it.id }.toSet() shouldBe setOf("a", "b")
        // Switching back to 「全部」.
        viewModel.onCategoryChange(null)
        viewModel.uiState.first { it.selectedCategory == null }.invoices.size shouldBe 5
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
        on: LocalDate = LocalDate(2026, 1, 15),
    ) = Invoice(
        id = id,
        invoiceNumber = "AB1234567$id",
        issueDate = on,
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
