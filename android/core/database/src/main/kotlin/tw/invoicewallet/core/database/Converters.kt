package tw.invoicewallet.core.database

import androidx.room.TypeConverter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Room type converters for the value types used across the schema. */
internal class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter
    fun localDateToIso(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun isoToLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun stringListToJson(value: List<String>?): String? = value?.let { Json.encodeToString(it) }

    @TypeConverter
    fun jsonToStringList(value: String?): List<String>? = value?.let { Json.decodeFromString<List<String>>(it) }
}
