package tw.invoicewallet.feature.pairing

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PairingPayloadTest {

    @Test
    fun `parses relay url and code from qr json`() {
        val payload = PairingPayload.parse("""{"relay_url":"https://r.example.tw/","pairing_code":"A3F9-K2P7"}""")
        payload shouldBe PairingPayload("https://r.example.tw", "A3F9-K2P7")
    }

    @Test
    fun `returns null for non-pairing text`() {
        PairingPayload.parse("not json") shouldBe null
        PairingPayload.parse("""{"foo":"bar"}""") shouldBe null
    }
}

class PairingClientTest {

    private fun client(status: HttpStatusCode, body: String) = PairingClient(
        HttpClient(
            MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
        ),
    )

    @Test
    fun `returns the device secret on success`() = runTest {
        val result = client(HttpStatusCode.OK, """{"device_secret":"sec-123"}""")
            .claim("https://r", "A3F9-K2P7", "fcm", "Mi MIX 2")
        result shouldBe PairingResult.Success("sec-123")
    }

    @Test
    fun `surfaces the relay error on non-200`() = runTest {
        val result = client(HttpStatusCode.Gone, """{"error":"pairing: code expired"}""")
            .claim("https://r", "OLD", "fcm", "d")
        result.shouldBeInstanceOf<PairingResult.Failure>()
        (result as PairingResult.Failure).reason shouldBe "pairing: code expired"
    }
}
