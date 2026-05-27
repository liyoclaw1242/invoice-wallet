package tw.invoicewallet.feature.settings

import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus
import tw.invoicewallet.core.testing.MainDispatcherExtension
import tw.invoicewallet.feature.export.ExportFormat

class SettingsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `surfaces stored settings and carrier code`() = runTest {
        val vm = SettingsViewModel(
            settingsRepository = FakeSettingsRepository(AppSettings(themeMode = ThemeMode.DARK)),
            carrierCodeStore = FakeCarrierCodeStore("/ABC123"),
            invoiceRepository = FakeInvoiceRepository(),
        )
        vm.uiState.test {
            val state = awaitItem()
            state.themeMode shouldBe ThemeMode.DARK
            state.carrierCode shouldBe "/ABC123"
        }
    }

    @Test
    fun `changing theme propagates to state`() = runTest {
        val vm = SettingsViewModel(FakeSettingsRepository(), FakeCarrierCodeStore(), FakeInvoiceRepository())
        vm.uiState.test {
            awaitItem().themeMode shouldBe ThemeMode.SYSTEM
            vm.setThemeMode(ThemeMode.LIGHT)
            awaitItem().themeMode shouldBe ThemeMode.LIGHT
        }
    }

    @Test
    fun `setting and clearing the carrier code persists and updates state`() = runTest {
        val store = FakeCarrierCodeStore()
        val vm = SettingsViewModel(FakeSettingsRepository(), store, FakeInvoiceRepository())
        vm.uiState.test {
            awaitItem().carrierCode shouldBe ""
            vm.setCarrierCode("/XYZ.+9")
            awaitItem().carrierCode shouldBe "/XYZ.+9"
            store.get() shouldBe "/XYZ.+9"
            vm.clearCarrierCode()
            awaitItem().carrierCode shouldBe ""
            store.get() shouldBe null
        }
    }

    @Test
    fun `buildExport serialises all invoices in the chosen format`() = runTest {
        val vm = SettingsViewModel(
            FakeSettingsRepository(),
            FakeCarrierCodeStore(),
            FakeInvoiceRepository(listOf(invoice("全聯福利中心"))),
        )
        vm.buildExport(ExportFormat.JSON) shouldContain "全聯福利中心"
        vm.buildExport(ExportFormat.CSV) shouldContain "全聯福利中心"
    }

    private fun invoice(merchant: String) = Invoice(
        id = "a",
        invoiceNumber = "AB12345678",
        issueDate = LocalDate(2026, 1, 15),
        issuePeriod = "11502",
        merchantName = merchant,
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

private class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
    override suspend fun setDefaultScanMode(mode: DefaultScanMode) = state.update { it.copy(defaultScanMode = mode) }
    override suspend fun setOnboardingCompleted(completed: Boolean) =
        state.update { it.copy(onboardingCompleted = completed) }
}

private class FakeCarrierCodeStore(private var value: String? = null) : CarrierCodeStore {
    override fun get(): String? = value
    override fun set(value: String) {
        this.value = value
    }
    override fun clear() {
        value = null
    }
}

private class FakeInvoiceRepository(private val invoices: List<Invoice> = emptyList()) : InvoiceRepository {
    override suspend fun upsert(invoice: Invoice): Invoice = invoice
    override suspend fun getById(id: String): Invoice? = invoices.firstOrNull { it.id == id }
    override fun observeAll(): Flow<List<Invoice>> = flowOf(invoices)
    override fun queryByDateRange(from: LocalDate, to: LocalDate): Flow<List<Invoice>> = flowOf(invoices)
    override suspend fun softDelete(id: String) = Unit
    override suspend fun search(keyword: String, limit: Int): List<Invoice> = invoices
}
