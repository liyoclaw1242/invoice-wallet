package tw.invoicewallet.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tw.invoicewallet.core.database.entity.LotteryNumberEntity

@Dao
interface LotteryNumberDao {
    @Upsert
    suspend fun upsert(lotteryNumber: LotteryNumberEntity)

    @Query("SELECT * FROM lottery_numbers WHERE period = :period")
    suspend fun getByPeriod(period: String): LotteryNumberEntity?

    @Query("SELECT * FROM lottery_numbers ORDER BY period DESC")
    fun observeAll(): Flow<List<LotteryNumberEntity>>

    @Query("SELECT * FROM lottery_numbers ORDER BY period DESC LIMIT 1")
    suspend fun getLatest(): LotteryNumberEntity?
}
