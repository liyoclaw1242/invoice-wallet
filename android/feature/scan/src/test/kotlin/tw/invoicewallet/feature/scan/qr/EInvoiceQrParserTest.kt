package tw.invoicewallet.feature.scan.qr

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/** Drives the parser from the real e-invoice QR fixtures (src/test/resources). */
class EInvoiceQrParserTest {

    private val fixtures = loadFixtures()

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }

    @Test
    fun `real fixtures parse with the expected header fields`() {
        fixtures.fixtures.forEach { f ->
            withClue(f.id) {
                val parsed = EInvoiceQrParser.parse(f.leftQr, f.rightQrHex?.hexToBytes())
                parsed.invoiceNumber shouldBe f.expected.invoiceNumber
                parsed.issueDate shouldBe LocalDate.parse(f.expected.issueDate)
                parsed.randomCode shouldBe f.expected.randomCode
                parsed.untaxedAmount shouldBe f.expected.untaxedAmount
                parsed.totalAmount shouldBe f.expected.totalAmount
                parsed.buyerTaxId shouldBe f.expected.buyerTaxId
                parsed.sellerTaxId shouldBe f.expected.sellerTaxId
                parsed.encoding shouldBe f.expected.encoding.toEncoding()
            }
        }
    }

    @Test
    fun `fixtures whose items are fully known parse their item list exactly`() {
        fixtures.fixtures
            .filter { f -> f.rightQrHex != null && f.expected.items.all { it.name != null } }
            .forEach { f ->
                withClue(f.id) {
                    val parsed = EInvoiceQrParser.parse(f.leftQr, f.rightQrHex!!.hexToBytes())
                    parsed.items shouldBe f.expected.items.map {
                        ParsedItem(it.name!!, it.quantity!!.toDouble(), it.unitPrice!!)
                    }
                }
            }
    }

    @Test
    fun `right code with trailing space padding still yields both items (real 麥味登 invoice)`() {
        // Decoded from a real receipt (invoice #4). The right QR is space-padded to a
        // fixed length, so the last price arrives as "25" + spaces — must not drop it.
        val left = "ZF131378791150410957900000000000000550000000098952903" +
            "hD1D6Z3tgPPiaySVPqrf9Q==:**********:2:2:1:超厚雞肉起司滿分堡:1:60"
        val right = ("**:奶茶(M)(熱):1:25" + " ".repeat(78)).toByteArray(Charsets.UTF_8)

        val parsed = EInvoiceQrParser.parse(left, right)

        parsed.invoiceNumber shouldBe "ZF13137879"
        parsed.totalAmount shouldBe 85
        parsed.sellerTaxId shouldBe "98952903"
        parsed.items shouldBe listOf(
            ParsedItem("超厚雞肉起司滿分堡", 1.0, 60),
            ParsedItem("奶茶(M)(熱)", 1.0, 25),
        )
    }

    @Test
    fun `encoding flag padded by right-code trailing spaces still parses (real 魯魯札札 invoice)`() {
        // Real cafe receipt — the left's encoding flag arrives as "0  " (Big5 + the right
        // QR's trailing whitespace padding). Strict equality used to throw 'unknown
        // encoding flag'.
        val left = "ZD05155535115041761580000013A0000014A0000000092318193" +
            "CEF9Ji/8LlRNvIY2nGVqLw==:**********:2:2:0  "

        val parsed = EInvoiceQrParser.parse(left, rightBytes = null)

        parsed.invoiceNumber shouldBe "ZD05155535"
        parsed.totalAmount shouldBe 330
        parsed.encoding shouldBe InvoiceTextEncoding.BIG5
    }

    @Test
    fun `header-only left QR (gas-station 自助加油) is accepted with empty items`() {
        // Real 福懋加油站 receipt — the left QR has no `*` 自定區 / detail section at all;
        // all items live in the right code. Previously threw 'missing 營業人自定區'.
        val left = "YX1488236711504189559000003B6000003E60000000060912553" +
            "i+Zgta+pNhR9vEF4oC4vWQ=="

        val parsed = EInvoiceQrParser.parse(left, rightBytes = null)

        parsed.invoiceNumber shouldBe "YX14882367"
        parsed.totalAmount shouldBe 998
        parsed.sellerTaxId shouldBe "60912553"
        parsed.items shouldBe emptyList()
        // Header-only left → default to UTF-8; the right code (when present) is decoded
        // with this fallback rather than throwing.
        parsed.encoding shouldBe InvoiceTextEncoding.UTF8
    }

    @Test
    fun `Gregorian YYYYMMDD date variant parses (real KFC invoice)`() {
        // KFC's POS emits the issue date as Gregorian 20260422 (8 chars) instead of the
        // spec's ROC 1150422 (7 chars). Whole fixed prefix shifts +1 char as a result.
        // Detected by year prefix "20" (ROC year is 1xx, never starts with 19/20).
        // Header reconstructed: ZD97984811 + 20260422 + 2630 + 00000042 (untaxed=66)
        //                      + 00000045 (total=69) + 00000000 (no buyer) + 16092461.
        val left = "ZD9798481120260422263000000042000000450000000016092461" +
            "abcdefghijklmnopqrstuv==:**********:1:1:1:"

        val parsed = EInvoiceQrParser.parse(left, rightBytes = null)

        parsed.invoiceNumber shouldBe "ZD97984811"
        parsed.issueDate shouldBe LocalDate(2026, 4, 22)
        parsed.randomCode shouldBe "2630"
        parsed.totalAmount shouldBe 69
        parsed.sellerTaxId shouldBe "16092461"
    }

    @Test
    fun `decimal qty and price (gas-station litres) survive the item parser`() {
        // Standalone right-code parse — 95Plus 無鉛 (lead-free), 30.32 L × NT$33.9.
        val rightBytes = "**95Plus無鉛:30.32:33.9:".toByteArray(Charsets.UTF_8)

        val items = EInvoiceQrParser.parseRightItems(rightBytes, InvoiceTextEncoding.UTF8)

        items shouldBe listOf(
            ParsedItem(name = "95Plus無鉛", quantity = 30.32, unitPrice = 34),
        )
    }

    @Test
    fun `malformed fixtures are rejected with an EInvoiceQrException`() {
        fixtures.malformed.forEach { m ->
            withClue("${m.id}: ${m.reason}") {
                shouldThrow<EInvoiceQrException> {
                    EInvoiceQrParser.parse(m.leftQr, m.rightQrHex?.hexToBytes())
                }
            }
        }
    }

    private fun loadFixtures(): FixtureFile {
        val raw = checkNotNull(this::class.java.getResourceAsStream("/einvoice-qr-fixtures.json")) {
            "einvoice-qr-fixtures.json missing from test resources"
        }.bufferedReader().use { it.readText() }
        return JSON.decodeFromString(FixtureFile.serializer(), raw)
    }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun String.toEncoding() = when (this) {
        "utf8" -> InvoiceTextEncoding.UTF8
        "big5" -> InvoiceTextEncoding.BIG5
        else -> error("unknown encoding '$this'")
    }

    @Serializable
    private data class FixtureFile(val fixtures: List<Fixture>, val malformed: List<Malformed>)

    @Serializable
    private data class Fixture(
        val id: String,
        val leftQr: String,
        val rightQrHex: String? = null,
        val expected: Expected,
    )

    @Serializable
    private data class Expected(
        val invoiceNumber: String,
        val issueDate: String,
        val randomCode: String,
        val untaxedAmount: Int,
        val totalAmount: Int,
        val sellerTaxId: String,
        val buyerTaxId: String? = null,
        val encoding: String,
        val items: List<ExpectedItem>,
    )

    @Serializable
    private data class ExpectedItem(val name: String? = null, val quantity: Int? = null, val unitPrice: Int? = null)

    @Serializable
    private data class Malformed(
        val id: String,
        val leftQr: String,
        val rightQrHex: String? = null,
        val reason: String,
    )
}
