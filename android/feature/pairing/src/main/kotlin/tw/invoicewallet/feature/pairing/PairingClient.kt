package tw.invoicewallet.feature.pairing

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Result of a pairing attempt. */
sealed interface PairingResult {
    data class Success(val deviceSecret: String) : PairingResult

    data class Failure(val reason: String) : PairingResult
}

/** Calls the relay's `/pair/claim` to exchange a pairing code for a device_secret. */
class PairingClient(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun claim(relayUrl: String, pairingCode: String, fcmToken: String, deviceName: String): PairingResult {
        val body = buildJsonObject {
            put("pairing_code", pairingCode)
            put("fcm_token", fcmToken)
            put("device_name", deviceName)
        }.toString()

        return runCatching {
            val response = client.post(relayUrl.trimEnd('/') + "/pair/claim") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            val text = response.bodyAsText()
            if (response.status != HttpStatusCode.OK) {
                return PairingResult.Failure(errorMessage(text, response.status.value))
            }
            val secret = (json.parseToJsonElement(text) as? JsonObject)
                ?.get("device_secret")?.let { (it as? JsonPrimitive)?.content }
            if (secret.isNullOrBlank()) {
                PairingResult.Failure("relay 未回傳 device_secret")
            } else {
                PairingResult.Success(secret)
            }
        }.getOrElse { PairingResult.Failure(it.message ?: "連線失敗") }
    }

    private fun errorMessage(body: String, status: Int): String {
        val msg = runCatching {
            (json.parseToJsonElement(body) as? JsonObject)?.get("error")?.let { (it as? JsonPrimitive)?.content }
        }.getOrNull()
        return msg ?: "配對失敗（HTTP $status）"
    }
}
