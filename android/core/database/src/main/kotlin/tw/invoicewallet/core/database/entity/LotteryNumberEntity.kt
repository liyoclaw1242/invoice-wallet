package tw.invoicewallet.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "lottery_numbers")
data class LotteryNumberEntity(
    @PrimaryKey @ColumnInfo(name = "period") val period: String,
    @ColumnInfo(name = "special_prize") val specialPrize: String,
    @ColumnInfo(name = "grand_prize") val grandPrize: String,
    @ColumnInfo(name = "first_prize") val firstPrize: List<String>,
    @ColumnInfo(name = "additional_sixth") val additionalSixth: List<String>,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
)
