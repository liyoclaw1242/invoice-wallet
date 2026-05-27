package tw.invoicewallet.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.kotest.assertions.throwables.shouldThrowAny
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
import tw.invoicewallet.core.database.invoiceEntity

@RunWith(AndroidJUnit4::class)
class InvoiceDaoTest {

    private lateinit var db: InvoiceWalletDatabase
    private lateinit var dao: InvoiceDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InvoiceWalletDatabase::class.java).build()
        dao = db.invoiceDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun inserting_a_new_invoice_and_querying_by_id_returns_it() = runTest {
        dao.insert(invoiceEntity(id = "inv-1"))

        dao.getById("inv-1")?.id shouldBe "inv-1"
    }

    @Test
    fun inserting_two_invoices_with_the_same_invoice_number_throws() = runTest {
        dao.insert(invoiceEntity(id = "inv-1", invoiceNumber = "AB12345678"))

        shouldThrowAny {
            dao.insert(invoiceEntity(id = "inv-2", invoiceNumber = "AB12345678"))
        }
    }

    @Test
    fun observeByDateRange_returns_only_in_range_ordered_by_date_desc() = runTest {
        dao.insert(invoiceEntity(id = "a", invoiceNumber = "AA00000001", issueDate = LocalDate(2026, 1, 10)))
        dao.insert(invoiceEntity(id = "b", invoiceNumber = "BB00000002", issueDate = LocalDate(2026, 1, 20)))
        dao.insert(invoiceEntity(id = "c", invoiceNumber = "CC00000003", issueDate = LocalDate(2026, 2, 5)))

        val ids = dao.observeByDateRange(LocalDate(2026, 1, 1), LocalDate(2026, 1, 31)).first().map { it.id }

        ids shouldBe listOf("b", "a")
    }

    @Test
    fun softDelete_sets_deletedAt_and_excludes_from_default_flow() = runTest {
        dao.insert(invoiceEntity(id = "inv-1"))
        val deletedAt = Instant.parse("2026-03-01T00:00:00Z")

        dao.softDelete("inv-1", deletedAt)

        dao.getById("inv-1")?.deletedAt shouldBe deletedAt
        dao.observeAll().first() shouldBe emptyList()
    }

    @Test
    fun findByMerchant_returns_invoices_matching_merchant_tax_id() = runTest {
        dao.insert(invoiceEntity(id = "a", invoiceNumber = "AA00000001", merchantTaxId = "111"))
        dao.insert(invoiceEntity(id = "b", invoiceNumber = "BB00000002", merchantTaxId = "222"))

        dao.findByMerchant("111").map { it.id } shouldBe listOf("a")
    }

    @Test
    fun observeAll_emits_a_new_value_when_an_invoice_is_inserted() = runTest {
        dao.observeAll().test {
            awaitItem() shouldBe emptyList()

            dao.insert(invoiceEntity(id = "inv-1"))

            awaitItem().map { it.id } shouldBe listOf("inv-1")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
