package tw.invoicewallet.feature.pairing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The QR content the relay prints: `{"relay_url":...,"pairing_code":...}`. */
data class PairingPayload(val relayUrl: String, val pairingCode: String) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses scanned QR text; returns null if it isn't a valid pairing payload. */
        fun parse(qrText: String): PairingPayload? {
            val obj = runCatching { json.parseToJsonElement(qrText.trim()) as? JsonObject }.getOrNull() ?: return null
            val url = (obj["relay_url"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
            val code = (obj["pairing_code"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
            return PairingPayload(relayUrl = url.trimEnd('/'), pairingCode = code)
        }
    }
}
