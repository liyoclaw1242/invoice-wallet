package tw.invoicewallet.feature.scan.merchant

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resolves a store name from the g0v open company dataset
 * (`company.g0v.ronny.tw`). The data is public business-registry information and only
 * the (public) tax ID leaves the device. The response shape varies, so parsing is
 * lenient and any failure yields null.
 */
class GovMerchantNameSource(
    private val client: HttpClient,
    private val baseUrl: String = "https://company.g0v.ronny.tw/api/show",
) : MerchantNameSource {

    override suspend fun fetch(taxId: String): String? {
        val body = client.get("$baseUrl/$taxId").bodyAsText()
        // The response shape varies and may be `{"data":null}` or non-JSON; stay tolerant.
        return runCatching {
            val data = JSON.parseToJsonElement(body).jsonObject["data"]?.jsonObject
            data?.let {
                NAME_FIELDS.firstNotNullOfOrNull { field ->
                    it[field]?.jsonPrimitive?.contentOrNull?.takeIf { value -> value.isNotBlank() }
                }
            }
        }.getOrNull()
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val NAME_FIELDS = listOf("公司名稱", "name", "營業人名稱")
    }
}
