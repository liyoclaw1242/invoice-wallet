package tw.invoicewallet.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tw.invoicewallet.core.database.InvoiceWalletDatabase
import tw.invoicewallet.core.model.AuthChannel
import tw.invoicewallet.core.model.AuthGrant
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryNumber
import tw.invoicewallet.core.model.LotteryStatus
import tw.invoicewallet.core.model.QueryAuditLog

/** Exercises each repository over a real Room database (the white-box integration tier). */
@RunWith(AndroidJUnit4::class)
class RoomRepositoryIntegrationTest {

    private lateinit var db: InvoiceWalletDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InvoiceWalletDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun invoice_repository_round_trips_over_real_room() = runTest {
        val repository = RoomInvoiceRepository(db.invoiceDao(), db.invoiceItemDao())
        val invoice = sampleInvoice

        repository.upsert(invoice)

        repository.getById(invoice.id) shouldBe invoice
        repository.observeAll().first().map { it.id } shouldBe listOf(invoice.id)
    }

    @Test
    fun lottery_repository_round_trips_over_real_room() = runTest {
        val repository = RoomLotteryRepository(db.lotteryNumberDao())
        val number = sampleLottery

        repository.upsert(number)

        repository.getByPeriod(number.period) shouldBe number
    }

    @Test
    fun auth_grant_repository_round_trips_and_records_audit() = runTest {
        val repository = RoomAuthGrantRepository(db.authGrantDao(), db.queryAuditLogDao())

        repository.grant(sampleGrant)
        repository.recordAudit(sampleAudit)

        repository.getById("grant-1")?.clientName shouldBe "Claude Mobile"
        repository.auditCountSince("grant-1", Instant.parse("2026-01-01T00:00:00Z")) shouldBe 1
    }

    private val t0 = Instant.parse("2026-01-15T10:00:00Z")

    private val sampleInvoice = Invoice(
        id = "inv-1",
        invoiceNumber = "AB12345678",
        issueDate = LocalDate(2026, 1, 15),
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
        userTags = listOf("食物"),
        createdAt = t0,
        updatedAt = t0,
        deletedAt = null,
    )

    private val sampleLottery = LotteryNumber(
        period = "11502",
        specialPrize = "12345678",
        grandPrize = "87654321",
        firstPrize = listOf("11112222"),
        additionalSixth = listOf("333"),
        fetchedAt = t0,
    )

    private val sampleGrant = AuthGrant(
        id = "grant-1",
        clientName = "Claude Mobile",
        channel = AuthChannel.RELAY,
        scopes = listOf("list_invoices"),
        excludedCategories = null,
        dateRangeDays = 90,
        grantedAt = t0,
        expiresAt = null,
        revokedAt = null,
    )

    private val sampleAudit = QueryAuditLog(
        id = "audit-1",
        grantId = "grant-1",
        toolName = "list_invoices",
        resultCount = 3,
        executedAt = t0,
    )
}
