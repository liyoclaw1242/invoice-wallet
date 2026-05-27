package tw.invoicewallet.feature.pairing

import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import tw.invoicewallet.core.testing.MainDispatcherExtension
import tw.invoicewallet.datasource.relayclient.RelayDeviceStore

class PairingViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `successful pairing stores the secret and reports paired`() = runTest {
        val store = FakeRelayDeviceStore()
        val vm = PairingViewModel(
            client = PairingClient(
                HttpClient(MockEngine { respond("""{"device_secret":"sec-xyz"}""", HttpStatusCode.OK, jsonHeaders()) }),
            ),
            deviceStore = store,
        )

        vm.uiState.test {
            awaitItem().status shouldBe PairingStatus.IDLE
            vm.pair("https://relay.example.tw", "A3F9-K2P7")
            awaitItem().status shouldBe PairingStatus.PAIRING
            awaitItem().status shouldBe PairingStatus.PAIRED
        }
        store.relayUrl() shouldBe "https://relay.example.tw"
        store.deviceSecret() shouldBe "sec-xyz"
    }

    @Test
    fun `failed pairing reports the error and stores nothing`() = runTest {
        val store = FakeRelayDeviceStore()
        val vm = PairingViewModel(
            client = PairingClient(
                HttpClient(
                    MockEngine {
                        respond("""{"error":"pairing: invalid code"}""", HttpStatusCode.NotFound, jsonHeaders())
                    },
                ),
            ),
            deviceStore = store,
        )

        vm.uiState.test {
            awaitItem()
            vm.pair("https://relay.example.tw", "NOPE-NOPE")
            awaitItem().status shouldBe PairingStatus.PAIRING
            awaitItem().status shouldBe PairingStatus.ERROR
        }
        store.isPaired shouldBe false
    }

    @Test
    fun `blank input is rejected without a network call`() = runTest {
        val vm = PairingViewModel(
            client = PairingClient(HttpClient(MockEngine { error("should not be called") })),
            deviceStore = FakeRelayDeviceStore(),
        )
        vm.uiState.test {
            awaitItem()
            vm.pair("", "")
            awaitItem().status shouldBe PairingStatus.ERROR
        }
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")
}

private class FakeRelayDeviceStore : RelayDeviceStore {
    private var url: String? = null
    private var secret: String? = null
    override fun relayUrl(): String? = url
    override fun deviceSecret(): String? = secret
    override fun save(relayUrl: String, deviceSecret: String) {
        url = relayUrl
        secret = deviceSecret
    }
    override fun clear() {
        url = null
        secret = null
    }
}
