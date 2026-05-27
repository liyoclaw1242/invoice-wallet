package tw.invoicewallet.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import tw.invoicewallet.core.database.mapper.asEntity
import tw.invoicewallet.core.database.mapper.asExternalModel
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus

@RunWith(AndroidJUnit4::class)
class EncryptionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "encryption-test.db"
    private val correctPassphrase = "correct-passphrase".toByteArray()
    private val wrongPassphrase = "wrong-passphrase".toByteArray()

    @After
    fun cleanup() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun data_written_with_the_correct_passphrase_is_readable_again() = runTest {
        val invoice = sampleInvoice()

        buildEncryptedDatabase(context, correctPassphrase, dbName).apply {
            invoiceDao().insert(invoice.asEntity())
            close()
        }

        buildEncryptedDatabase(context, correctPassphrase, dbName).apply {
            invoiceDao().getById(invoice.id)?.asExternalModel() shouldBe invoice
            close()
        }
    }

    @Test
    fun wrong_passphrase_cannot_open_the_database() = runTest {
        buildEncryptedDatabase(context, correctPassphrase, dbName).apply {
            invoiceDao().insert(sampleInvoice().asEntity())
            close()
        }

        val reopened = buildEncryptedDatabase(context, wrongPassphrase, dbName)
        // Room opens lazily; the first query forces SQLCipher to decrypt the file.
        shouldThrowAny {
            reopened.invoiceDao().getById("anything")
        }
        reopened.close()
    }

    private fun sampleInvoice() = Invoice(
        id = "019589f0-0000-7000-8000-0000000000aa",
        invoiceNumber = "ZZ99887766",
        issueDate = LocalDate(2026, 2, 1),
        issuePeriod = "11502",
        merchantName = "誠品書店",
        merchantTaxId = "98765432",
        buyerTaxId = null,
        carrierIdEncrypted = null,
        totalAmount = 480,
        taxAmount = 23,
        randomCode = "8888",
        category = "books",
        source = InvoiceSource.OCR,
        ocrConfidence = 0.92f,
        lotteryStatus = LotteryStatus.PENDING,
        lotteryPrize = null,
        imagePath = null,
        userNote = null,
        userTags = emptyList(),
        createdAt = Instant.parse("2026-02-01T08:30:00Z"),
        updatedAt = Instant.parse("2026-02-01T08:30:00Z"),
        deletedAt = null,
    )
}
