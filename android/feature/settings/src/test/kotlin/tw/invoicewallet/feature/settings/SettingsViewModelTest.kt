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
import tw.invoicewallet.core.model.InvoiceItem
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus
import tw.invoicewallet.core.testing.MainDispatcherExtension
import tw.invoicewallet.datasource.mcpserver.McpServerControls
import tw.invoicewallet.datasource.relayclient.RelayDeviceStore
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
            mcpServer = FakeMcpServerControls(),
            relayDeviceStore = FakeRelayDeviceStore(),
        )
        vm.uiState.test {
            val state = awaitItem()
            state.themeMode shouldBe ThemeMode.DARK
            state.carrierCode shouldBe "/ABC123"
        }
    }

    @Test
    fun `changing theme propagates to state`() = runTest {
        val vm = SettingsViewModel(
            FakeSettingsRepository(),
            FakeCarrierCodeStore(),
            FakeInvoiceRepository(),
            FakeMcpServerControls(),
            FakeRelayDeviceStore(),
        )
        vm.uiState.test {
            awaitItem().themeMode shouldBe ThemeMode.SYSTEM
            vm.setThemeMode(ThemeMode.LIGHT)
            awaitItem().themeMode shouldBe ThemeMode.LIGHT
        }
    }

    @Test
    fun `setting and clearing the carrier code persists and updates state`() = runTest {
        val store = FakeCarrierCodeStore()
        val vm = SettingsViewModel(
            FakeSettingsRepository(),
            store,
            FakeInvoiceRepository(),
            FakeMcpServerControls(),
            FakeRelayDeviceStore(),
        )
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
    fun `mcp toggle and token regeneration flow through state`() = runTest {
        val vm = SettingsViewModel(
            FakeSettingsRepository(),
            FakeCarrierCodeStore(),
            FakeInvoiceRepository(),
            FakeMcpServerControls(),
            FakeRelayDeviceStore(),
        )
        vm.uiState.test {
            awaitItem().mcpToken shouldBe "tok-1"
            vm.setMcpEnabled(true)
            awaitItem().mcpEnabled shouldBe true
            vm.regenerateMcpToken()
            awaitItem().mcpToken shouldBe "tok-2"
        }
    }

    @Test
    fun `importCarrierCsv parses, persists and returns a summary`() = runTest {
        val repo = FakeInvoiceRepository()
        val vm = SettingsViewModel(
            FakeSettingsRepository(),
            FakeCarrierCodeStore(),
            repo,
            FakeMcpServerControls(),
            FakeRelayDeviceStore(),
        )
        val csv = buildString {
            append("\uFEFF") // 財政部 exports start with a UTF-8 BOM
            append("a,b,c,d,e,f,g,h,i,j,k,l,m,n\n")
            append("手機條碼,20260326,YT00000001,308,開立已確認,否,42467936,新東陽,某地址,,1,308,308,大心\n")
            append("捐贈或作廢之發票，字軌號碼均會隱末3碼\n")
        }

        val summary = vm.importCarrierCsv(csv)

        summary.added shouldBe 1
        summary.updated shouldBe 0
        summary.skippedRows shouldBe 1
        repo.upsertedItemRows.single().first shouldBe "YT00000001"
    }

    @Test
    fun `buildExport serialises all invoices in the chosen format`() = runTest {
        val vm = SettingsViewModel(
            FakeSettingsRepository(),
            FakeCarrierCodeStore(),
            FakeInvoiceRepository(listOf(invoice("全聯福利中心"))),
            FakeMcpServerControls(),
            FakeRelayDeviceStore(),
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
    override suspend fun setMcpEnabled(enabled: Boolean) = state.update { it.copy(mcpEnabled = enabled) }
    override suspend fun setMcpLanMode(lanMode: Boolean) = state.update { it.copy(mcpLanMode = lanMode) }
    override suspend fun setRemoteEnabled(enabled: Boolean) = state.update { it.copy(remoteEnabled = enabled) }
}

private class FakeRelayDeviceStore : RelayDeviceStore {
    private var url: String? = null
    private var secret: String? = null
    override fun relayUrl(): String? = url
    override fun deviceSecret(): String? = secret
    override fun save(relayUrl: String, deviceSecret: String) {
        url = relayUrl
        secret = deviceSecret
    }
    override fun clear() {
        url = null
        secret = null
    }
}

private class FakeMcpServerControls(private var token: String = "tok-1") : McpServerControls {
    override val isRunning: Boolean = false
    override fun token(): String = token
    override fun regenerateToken(): String {
        token = "tok-2"
        return token
    }
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
    val upsertedItemRows = mutableListOf<Pair<String, List<InvoiceItem>>>() // (invoiceNumber, items)
    override suspend fun upsert(invoice: Invoice): Invoice = invoice
    override suspend fun upsertWithItems(invoice: Invoice, items: List<InvoiceItem>): Invoice {
        upsertedItemRows += invoice.invoiceNumber to items
        return invoice
    }
    override suspend fun getByInvoiceNumber(invoiceNumber: String): Invoice? = null
    override suspend fun getById(id: String): Invoice? = invoices.firstOrNull { it.id == id }
    override fun observeAll(): Flow<List<Invoice>> = flowOf(invoices)
    override fun queryByDateRange(from: LocalDate, to: LocalDate): Flow<List<Invoice>> = flowOf(invoices)
    override suspend fun softDelete(id: String) = Unit
    override suspend fun search(keyword: String, limit: Int): List<Invoice> = invoices
}
