package tw.invoicewallet.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tw.invoicewallet.core.database.InvoiceWalletDatabase
import tw.invoicewallet.core.database.queryAuditLogEntity

@RunWith(AndroidJUnit4::class)
class QueryAuditLogDaoTest {

    private lateinit var db: InvoiceWalletDatabase
    private lateinit var dao: QueryAuditLogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InvoiceWalletDatabase::class.java).build()
        dao = db.queryAuditLogDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insert_then_getByGrant_returns_logs_newest_first() = runTest {
        dao.insert(queryAuditLogEntity(id = "old", executedAt = Instant.parse("2026-01-01T00:00:00Z")))
        dao.insert(queryAuditLogEntity(id = "new", executedAt = Instant.parse("2026-01-02T00:00:00Z")))

        dao.getByGrant("grant-1").map { it.id } shouldBe listOf("new", "old")
    }

    @Test
    fun countSince_counts_only_logs_at_or_after_the_threshold() = runTest {
        dao.insert(queryAuditLogEntity(id = "old", executedAt = Instant.parse("2026-01-01T00:00:00Z")))
        dao.insert(queryAuditLogEntity(id = "new", executedAt = Instant.parse("2026-01-10T00:00:00Z")))

        dao.countSince("grant-1", Instant.parse("2026-01-05T00:00:00Z")) shouldBe 1
    }
}
