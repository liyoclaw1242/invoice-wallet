package tw.invoicewallet.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tw.invoicewallet.core.database.InvoiceWalletDatabase
import tw.invoicewallet.core.database.invoiceItemEntity

@RunWith(AndroidJUnit4::class)
class InvoiceItemDaoTest {

    private lateinit var db: InvoiceWalletDatabase
    private lateinit var dao: InvoiceItemDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InvoiceWalletDatabase::class.java).build()
        dao = db.invoiceItemDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertAll_then_getByInvoiceId_returns_items_ordered_by_sequence() = runTest {
        dao.insertAll(
            listOf(
                invoiceItemEntity(id = "i2", sequence = 1),
                invoiceItemEntity(id = "i1", sequence = 0),
            ),
        )

        dao.getByInvoiceId("inv-1").map { it.id } shouldBe listOf("i1", "i2")
    }

    @Test
    fun deleteByInvoiceId_removes_all_items_of_that_invoice() = runTest {
        dao.insertAll(listOf(invoiceItemEntity(id = "i1")))

        dao.deleteByInvoiceId("inv-1")

        dao.getByInvoiceId("inv-1") shouldBe emptyList()
    }

    @Test
    fun observeByInvoiceId_emits_current_items() = runTest {
        dao.insertAll(listOf(invoiceItemEntity(id = "i1")))

        dao.observeByInvoiceId("inv-1").first().map { it.id } shouldBe listOf("i1")
    }
}
