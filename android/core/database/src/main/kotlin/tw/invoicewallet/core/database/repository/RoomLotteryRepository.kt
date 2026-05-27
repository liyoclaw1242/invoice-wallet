package tw.invoicewallet.core.database.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tw.invoicewallet.core.database.dao.LotteryNumberDao
import tw.invoicewallet.core.database.mapper.asEntity
import tw.invoicewallet.core.database.mapper.asExternalModel
import tw.invoicewallet.core.model.LotteryNumber

class RoomLotteryRepository(private val lotteryNumberDao: LotteryNumberDao) : LotteryRepository {

    override suspend fun upsert(number: LotteryNumber) = lotteryNumberDao.upsert(number.asEntity())

    override suspend fun getByPeriod(period: String): LotteryNumber? =
        lotteryNumberDao.getByPeriod(period)?.asExternalModel()

    override fun observeAll(): Flow<List<LotteryNumber>> =
        lotteryNumberDao.observeAll().map { rows -> rows.map { it.asExternalModel() } }

    override suspend fun latest(): LotteryNumber? = lotteryNumberDao.getLatest()?.asExternalModel()
}
