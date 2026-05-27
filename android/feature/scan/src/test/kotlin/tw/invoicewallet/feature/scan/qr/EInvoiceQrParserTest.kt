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
                        ParsedItem(it.name!!, it.quantity!!, it.unitPrice!!)
                    }
                }
            }
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
