package tw.invoicewallet.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tw.invoicewallet.core.database.dao.InvoiceDao
import tw.invoicewallet.core.database.mapper.asEntity
import tw.invoicewallet.core.database.mapper.asExternalModel
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus

@RunWith(AndroidJUnit4::class)
class InvoiceWalletDatabaseTest {

    private lateinit var db: InvoiceWalletDatabase
    private lateinit var invoiceDao: InvoiceDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InvoiceWalletDatabase::class.java).build()
        invoiceDao = db.invoiceDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_then_getById_roundtrips_through_entity_converters_and_mapper() = runTest {
        val invoice = sampleInvoice()

        invoiceDao.insert(invoice.asEntity())

        invoiceDao.getById(invoice.id)?.asExternalModel() shouldBe invoice
    }

    private fun sampleInvoice() = Invoice(
        id = "019589f0-0000-7000-8000-000000000001",
        invoiceNumber = "AB12345678",
        issueDate = LocalDate(2026, 1, 15),
        issuePeriod = "11502",
        merchantName = "全聯福利中心",
        merchantTaxId = "12345678",
        buyerTaxId = null,
        carrierIdEncrypted = null,
        totalAmount = 105,
        taxAmount = 5,
        randomCode = "1234",
        category = "grocery",
        source = InvoiceSource.QR_CODE,
        ocrConfidence = null,
        lotteryStatus = LotteryStatus.PENDING,
        lotteryPrize = null,
        imagePath = null,
        userNote = null,
        userTags = listOf("食物", "日常"),
        createdAt = Instant.parse("2026-01-15T10:00:00Z"),
        updatedAt = Instant.parse("2026-01-15T10:00:00Z"),
        deletedAt = null,
    )
}
