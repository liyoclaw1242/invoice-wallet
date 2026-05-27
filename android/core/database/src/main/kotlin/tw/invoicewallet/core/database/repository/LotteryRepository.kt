package tw.invoicewallet.core.database.repository

import kotlinx.coroutines.flow.Flow
import tw.invoicewallet.core.model.LotteryNumber

/** Access to cached lottery winning numbers. */
interface LotteryRepository {
    suspend fun upsert(number: LotteryNumber)

    suspend fun getByPeriod(period: String): LotteryNumber?

    fun observeAll(): Flow<List<LotteryNumber>>

    suspend fun latest(): LotteryNumber?
}
