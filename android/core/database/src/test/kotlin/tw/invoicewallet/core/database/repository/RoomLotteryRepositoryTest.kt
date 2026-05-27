package tw.invoicewallet.core.database.repository

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RoomLotteryRepositoryTest {

    private val dao = FakeLotteryNumberDao()
    private val repository = RoomLotteryRepository(dao)

    @Test
    fun `upsert then getByPeriod returns the domain model`() = runTest {
        val number = lotteryNumber(period = "11502")

        repository.upsert(number)

        repository.getByPeriod("11502") shouldBe number
    }

    @Test
    fun `latest returns the highest period`() = runTest {
        repository.upsert(lotteryNumber(period = "11502"))
        repository.upsert(lotteryNumber(period = "11504"))

        repository.latest()?.period shouldBe "11504"
    }
}
