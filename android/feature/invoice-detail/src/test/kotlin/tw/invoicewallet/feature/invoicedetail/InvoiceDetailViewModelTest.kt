package tw.invoicewallet.feature.invoicedetail

import androidx.lifecycle.SavedStateHandle
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
import tw.invoicewallet.core.model.InvoiceItem
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus
import tw.invoicewallet.core.testing.MainDispatcherExtension

class InvoiceDetailViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val fixedNow = Instant.parse("2026-06-01T00:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = fixedNow
    }

    @Test
    fun `loads the invoice for the given id`() = runTest {
        val viewModel = viewModel(FakeRepo(invoice("inv-1")), "inv-1")

        val state = viewModel.state.first { it !is InvoiceDetailState.Loading }

        state.shouldBeInstanceOf<InvoiceDetailState.Loaded>()
        state.invoice.id shouldBe "inv-1"
    }

    @Test
    fun `loads the invoice's line items`() = runTest {
        val repo = FakeRepo(invoice("inv-1")).apply {
            seedItems(
                "inv-1",
                listOf(
                    InvoiceItem("it-1", "inv-1", "漢堡", 1.0, 85, 85, null, 0),
                    InvoiceItem("it-2", "inv-1", "奶茶", 2.0, 30, 60, null, 1),
                ),
            )
        }
        val viewModel = viewModel(repo, "inv-1")

        val state = viewModel.state.first { it !is InvoiceDetailState.Loading }

        state.shouldBeInstanceOf<InvoiceDetailState.Loaded>()
        state.items.map { it.name } shouldBe listOf("漢堡", "奶茶")
    }

    @Test
    fun `keeps the line items after saving note and tags`() = runTest {
        val repo = FakeRepo(invoice("inv-1")).apply {
            seedItems("inv-1", listOf(InvoiceItem("it-1", "inv-1", "漢堡", 1.0, 85, 85, null, 0)))
        }
        val viewModel = viewModel(repo, "inv-1")
        viewModel.state.first { it is InvoiceDetailState.Loaded }

        viewModel.onSave(note = "午餐", tags = emptyList())

        val state = viewModel.state.value
        state.shouldBeInstanceOf<InvoiceDetailState.Loaded>()
        state.items.map { it.name } shouldBe listOf("漢堡")
    }

    @Test
    fun `reports NotFound when the id is unknown`() = runTest {
        val viewModel = viewModel(FakeRepo(), "missing")

        viewModel.state.first { it !is InvoiceDetailState.Loading } shouldBe InvoiceDetailState.NotFound
    }

    @Test
    fun `onSave persists the edited note and tags`() = runTest {
        val repo = FakeRepo(invoice("inv-1"))
        val viewModel = viewModel(repo, "inv-1")
        viewModel.state.first { it is InvoiceDetailState.Loaded }

        viewModel.onSave(note = "午餐", tags = listOf("food", "lunch"))

        val saved = repo.getById("inv-1")!!
        saved.userNote shouldBe "午餐"
        saved.userTags shouldBe listOf("food", "lunch")
        saved.updatedAt shouldBe fixedNow
    }

    @Test
    fun `onDelete soft-deletes and moves to Deleted`() = runTest {
        val repo = FakeRepo(invoice("inv-1"))
        val viewModel = viewModel(repo, "inv-1")
        viewModel.state.first { it is InvoiceDetailState.Loaded }

        viewModel.onDelete()

        repo.deletedIds shouldBe listOf("inv-1")
        viewModel.state.value shouldBe InvoiceDetailState.Deleted
    }

    private fun viewModel(repo: InvoiceRepository, id: String) = InvoiceDetailViewModel(
        repository = repo,
        clock = clock,
        savedStateHandle = SavedStateHandle(mapOf(InvoiceDetailViewModel.INVOICE_ID_ARG to id)),
    )

    private class FakeRepo(vararg seed: Invoice) : InvoiceRepository {
        private val all = MutableStateFlow(seed.toList())
        val deletedIds = mutableListOf<String>()
        private val itemsByInvoice = mutableMapOf<String, List<InvoiceItem>>()

        fun seedItems(invoiceId: String, items: List<InvoiceItem>) {
            itemsByInvoice[invoiceId] = items
        }

        override suspend fun upsert(invoice: Invoice): Invoice {
            all.value = all.value.filterNot { it.id == invoice.id } + invoice
            return invoice
        }

        override suspend fun getItems(invoiceId: String): List<InvoiceItem> = itemsByInvoice[invoiceId].orEmpty()

        override suspend fun getById(id: String): Invoice? = all.value.find { it.id == id }
        override fun observeAll(): Flow<List<Invoice>> = all
        override fun queryByDateRange(from: LocalDate, to: LocalDate): Flow<List<Invoice>> = all
        override suspend fun softDelete(id: String) {
            deletedIds += id
            all.value = all.value.filterNot { it.id == id }
        }

        override suspend fun search(keyword: String, limit: Int): List<Invoice> = emptyList()
    }

    private fun invoice(id: String) = Invoice(
        id = id,
        invoiceNumber = "AB12345678",
        issueDate = LocalDate(2026, 1, 15),
        issuePeriod = "11502",
        merchantName = "全聯",
        merchantTaxId = "12345678",
        buyerTaxId = null,
        carrierIdEncrypted = null,
        totalAmount = 100,
        taxAmount = 5,
        randomCode = "1234",
        category = null,
        source = InvoiceSource.QR_CODE,
        ocrConfidence = null,
        lotteryStatus = LotteryStatus.PENDING,
        lotteryPrize = null,
        imagePath = null,
        userNote = null,
        userTags = emptyList(),
        createdAt = Instant.parse("2026-01-15T10:00:00Z"),
        updatedAt = Instant.parse("2026-01-15T10:00:00Z"),
        deletedAt = null,
    )
}
