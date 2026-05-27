package tw.invoicewallet.feature.scan.merchant

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Resolves a store name from the g0v open registry (`company.g0v.ronny.tw`). Public
 * business-registry data; only the (public) tax ID leaves the device.
 *
 * The shape varies by registration type, so we probe several name fields across the
 * `data` root and a nested 財政部 (tax-registry) block:
 *  - 公司登記 → `data.公司名稱`
 *  - 商業登記 → `data.商業名稱`
 *  - 稅籍     → `data.財政部.營業人名稱`
 * Any failure / no match yields null.
 */
class GovMerchantNameSource(
    private val client: HttpClient,
    private val baseUrl: String = "https://company.g0v.ronny.tw/api/show",
) : MerchantNameSource {

    override suspend fun fetch(taxId: String): String? {
        val body = client.get("$baseUrl/$taxId").bodyAsText()
        return runCatching {
            val data = JSON.parseToJsonElement(body).jsonObject["data"] as? JsonObject
                ?: return@runCatching null
            val candidates = buildList {
                add(data)
                (data["財政部"] as? JsonObject)?.let(::add)
            }
            candidates.firstNotNullOfOrNull { obj ->
                NAME_FIELDS.firstNotNullOfOrNull { field ->
                    (obj[field] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                }
            }
        }.getOrNull()
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val NAME_FIELDS = listOf("公司名稱", "商業名稱", "營業人名稱", "name")
    }
}
