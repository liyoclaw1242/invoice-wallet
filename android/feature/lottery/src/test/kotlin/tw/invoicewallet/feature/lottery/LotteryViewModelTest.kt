package tw.invoicewallet.feature.lottery

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.database.repository.LotteryRepository
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryNumber
import tw.invoicewallet.core.model.LotteryStatus
import tw.invoicewallet.core.testing.MainDispatcherExtension

class LotteryViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `downloads numbers, matches invoices and surfaces winners`() = runTest {
        val apiClient = LotteryApiClient(HttpClient(MockEngine { respond(FEED) }))
        val viewModel = LotteryViewModel(
            apiClient = apiClient,
            lotteryRepository = FakeLotteryRepository(),
            invoiceRepository = FakeInvoiceRepository(
                invoice("win", "XX07225810"), // matches first prize 07225810 in 11502 → 頭獎
                invoice("lose", "XX00000000"),
            ),
        )

        val state = viewModel.uiState.first { !it.isLoading && it.results.isNotEmpty() }

        state.latestNumbers?.period shouldBe "11502"
        state.winners.map { it.invoice.id } shouldBe listOf("win")
        (state.winners.single().result as LotteryResult.Won).prize shouldBe LotteryPrize.FIRST
    }

    private class FakeLotteryRepository : LotteryRepository {
        private val byPeriod = mutableMapOf<String, LotteryNumber>()
        override suspend fun upsert(number: LotteryNumber) {
            byPeriod[number.period] = number
        }

        override suspend fun getByPeriod(period: String): LotteryNumber? = byPeriod[period]
        override fun observeAll(): Flow<List<LotteryNumber>> = MutableStateFlow(byPeriod.values.toList())
        override suspend fun latest(): LotteryNumber? = byPeriod.values.maxByOrNull { it.period }
    }

    private class FakeInvoiceRepository(vararg seed: Invoice) : InvoiceRepository {
        private val all = MutableStateFlow(seed.toList())
        override fun observeAll(): Flow<List<Invoice>> = all
        override suspend fun upsert(invoice: Invoice): Invoice = invoice
        override suspend fun getById(id: String): Invoice? = all.value.find { it.id == id }
        override fun queryByDateRange(from: LocalDate, to: LocalDate): Flow<List<Invoice>> = all
        override suspend fun softDelete(id: String) = Unit
        override suspend fun search(keyword: String, limit: Int): List<Invoice> = emptyList()
    }

    private fun invoice(id: String, number: String) = Invoice(
        id = id,
        invoiceNumber = number,
        issueDate = LocalDate(2026, 4, 15),
        issuePeriod = "11502",
        merchantName = "商店",
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
        createdAt = Instant.parse("2026-04-15T10:00:00Z"),
        updatedAt = Instant.parse("2026-04-15T10:00:00Z"),
        deletedAt = null,
    )

    private companion object {
        val FEED = """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel>
            <item><title><![CDATA[115年 03~04月]]></title>
            <description><![CDATA[<p>特別獎：19531471</p><p>特獎：85941329</p><p>頭獎：07225810、20231230、83518781</p>]]></description>
            </item></channel></rss>
        """.trimIndent()
    }
}
