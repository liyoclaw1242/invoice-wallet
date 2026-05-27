package tw.invoicewallet.feature.pairing

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RelayUrlTest {

    @Test
    fun `prepends https when scheme is missing`() {
        normalizeRelayUrl("relay.yourdomain.tw") shouldBe "https://relay.yourdomain.tw"
        normalizeRelayUrl("  relay.yourdomain.tw/  ") shouldBe "https://relay.yourdomain.tw"
    }

    @Test
    fun `upgrades http to https`() {
        normalizeRelayUrl("http://relay.yourdomain.tw") shouldBe "https://relay.yourdomain.tw"
    }

    @Test
    fun `leaves https untouched and trims trailing slash`() {
        normalizeRelayUrl("https://relay.yourdomain.tw/") shouldBe "https://relay.yourdomain.tw"
    }
}
