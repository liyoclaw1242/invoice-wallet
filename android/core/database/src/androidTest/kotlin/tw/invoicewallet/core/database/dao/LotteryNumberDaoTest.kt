package tw.invoicewallet.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tw.invoicewallet.core.database.InvoiceWalletDatabase
import tw.invoicewallet.core.database.lotteryNumberEntity

@RunWith(AndroidJUnit4::class)
class LotteryNumberDaoTest {

    private lateinit var db: InvoiceWalletDatabase
    private lateinit var dao: LotteryNumberDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InvoiceWalletDatabase::class.java).build()
        dao = db.lotteryNumberDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun upsert_then_getByPeriod_returns_the_numbers() = runTest {
        dao.upsert(lotteryNumberEntity(period = "11502"))

        dao.getByPeriod("11502")?.specialPrize shouldBe "12345678"
    }

    @Test
    fun upserting_the_same_period_twice_updates_in_place() = runTest {
        dao.upsert(lotteryNumberEntity(period = "11502", specialPrize = "11111111"))
        dao.upsert(lotteryNumberEntity(period = "11502", specialPrize = "99999999"))

        dao.getByPeriod("11502")?.specialPrize shouldBe "99999999"
    }

    @Test
    fun getLatest_returns_the_highest_period() = runTest {
        dao.upsert(lotteryNumberEntity(period = "11502"))
        dao.upsert(lotteryNumberEntity(period = "11504"))

        dao.getLatest()?.period shouldBe "11504"
    }
}
