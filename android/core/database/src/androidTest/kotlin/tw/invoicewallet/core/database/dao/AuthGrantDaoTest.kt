package tw.invoicewallet.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tw.invoicewallet.core.database.InvoiceWalletDatabase
import tw.invoicewallet.core.database.authGrantEntity

@RunWith(AndroidJUnit4::class)
class AuthGrantDaoTest {

    private lateinit var db: InvoiceWalletDatabase
    private lateinit var dao: AuthGrantDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InvoiceWalletDatabase::class.java).build()
        dao = db.authGrantDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun upsert_then_getById_returns_the_grant() = runTest {
        dao.upsert(authGrantEntity(id = "grant-1"))

        dao.getById("grant-1")?.clientName shouldBe "Claude Mobile"
    }

    @Test
    fun observeActive_excludes_revoked_grants() = runTest {
        dao.upsert(authGrantEntity(id = "active"))
        dao.upsert(authGrantEntity(id = "revoked", revokedAt = Instant.parse("2026-02-01T00:00:00Z")))

        dao.observeActive().first().map { it.id } shouldBe listOf("active")
    }

    @Test
    fun revoke_stamps_revokedAt_and_drops_it_from_active() = runTest {
        dao.upsert(authGrantEntity(id = "grant-1"))

        dao.revoke("grant-1", Instant.parse("2026-02-01T00:00:00Z"))

        dao.getById("grant-1")?.revokedAt shouldBe Instant.parse("2026-02-01T00:00:00Z")
        dao.observeActive().first() shouldBe emptyList()
    }
}
