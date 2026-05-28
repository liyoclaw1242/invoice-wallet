package tw.invoicewallet.feature.scan.qr

import kotlinx.datetime.LocalDate
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Parses Taiwan MOF e-invoice (電子發票證明聯) QR codes.
 *
 * The LEFT code starts with 53 fixed-width chars, then a variable-length AES
 * verification field, then the 營業人自定區 (`*` run), then `:`-delimited
 * `品目筆數:總筆數:編碼旗標:品名:數量:單價...`. The AES field length and whether
 * it is `:`-terminated vary between POS vendors, so we locate the `*` run rather
 * than slicing at a fixed offset.
 *
 * The RIGHT code is raw bytes starting with `**`, then the rest of the
 * `:`-delimited item list, decoded with the encoding the LEFT code declared
 * (modern receipts are UTF-8; older ones Big5 — never assume).
 */
object EInvoiceQrParser {

    private const val FIXED_PREFIX_LENGTH_ROC = 53
    private val RIGHT_PREFIX = byteArrayOf(0x2A, 0x2A)

    fun parseLeft(leftQr: String): LeftCode {
        if (leftQr.length < FIXED_PREFIX_LENGTH_ROC) {
            throw EInvoiceQrException.MalformedLeftCode(
                "left code length ${leftQr.length} < required $FIXED_PREFIX_LENGTH_ROC fixed chars",
            )
        }

        val invoiceNumber = leftQr.substring(0, 10)

        // Date encoding split: the spec says 7-char ROC YYYMMDD (e.g. 1150421 = 民國 115
        // 年 04/21 = 2026-04-21), but some POS (KFC, 瑪可希維) emit 8-char Gregorian
        // YYYYMMDD instead, shifting every subsequent fixed-field position by +1. Detect
        // by the year prefix: ROC year is always 1xx (100–139 covers 2011–2050 — way past
        // any realistic invoice lifetime), so a leading "19" / "20" can only be Gregorian.
        val isGregorianDate = leftQr.length >= 18 &&
            (leftQr.startsWith("19", 10) || leftQr.startsWith("20", 10))
        val dateEnd: Int
        val issueDate: kotlinx.datetime.LocalDate
        if (isGregorianDate) {
            dateEnd = 18
            issueDate = parseGregorianDate(leftQr.substring(10, 18))
        } else {
            dateEnd = 17
            issueDate = parseRocDate(leftQr.substring(10, 17))
        }

        val fixedPrefixLen = dateEnd + 36 // randomCode(4) + untaxed(8) + total(8) + buyer(8) + seller(8)
        if (leftQr.length < fixedPrefixLen) {
            throw EInvoiceQrException.MalformedLeftCode(
                "left code length ${leftQr.length} < required $fixedPrefixLen fixed chars",
            )
        }

        val randomCode = leftQr.substring(dateEnd, dateEnd + 4)
        val untaxedAmount = parseHexAmount(leftQr.substring(dateEnd + 4, dateEnd + 12), "untaxed amount")
        val totalAmount = parseHexAmount(leftQr.substring(dateEnd + 12, dateEnd + 20), "total amount")
        val buyerTaxId = leftQr.substring(dateEnd + 20, dateEnd + 28).takeUnless { it == "00000000" }
        val sellerTaxId = leftQr.substring(dateEnd + 28, dateEnd + 36)

        val rest = leftQr.substring(fixedPrefixLen)
        val starStart = rest.indexOf('*')
        // Some POS (notably gas-station 自助加油機) emit a short left QR — fixed prefix
        // + AES only, no `*` 自定區 and no item detail. All items live in the right code
        // there. Treat that as a valid header-only invoice rather than refusing the whole
        // scan; the right-code parser uses [InvoiceTextEncoding.UTF8] as the default.
        if (starStart < 0) {
            return LeftCode(
                invoiceNumber = invoiceNumber,
                issueDate = issueDate,
                randomCode = randomCode,
                untaxedAmount = untaxedAmount,
                totalAmount = totalAmount,
                buyerTaxId = buyerTaxId,
                sellerTaxId = sellerTaxId,
                encoding = InvoiceTextEncoding.UTF8,
                items = emptyList(),
            )
        }
        var starEnd = starStart
        while (starEnd < rest.length && rest[starEnd] == '*') starEnd++

        val tail = rest.substring(starEnd).trimStart(':').split(':')
        if (tail.size < 3) {
            throw EInvoiceQrException.MalformedLeftCode("missing 品目筆數/總筆數/編碼 sections")
        }
        // The right code is space-padded to a fixed length; that padding bleeds into the
        // left's encoding flag when the two are concatenated/decoded as one string, so
        // trim before the strict match.
        val encoding = when (tail[2].trim()) {
            "0" -> InvoiceTextEncoding.BIG5
            "1" -> InvoiceTextEncoding.UTF8
            else -> throw EInvoiceQrException.MalformedLeftCode("unknown encoding flag '${tail[2]}'")
        }

        return LeftCode(
            invoiceNumber = invoiceNumber,
            issueDate = issueDate,
            randomCode = randomCode,
            untaxedAmount = untaxedAmount,
            totalAmount = totalAmount,
            buyerTaxId = buyerTaxId,
            sellerTaxId = sellerTaxId,
            encoding = encoding,
            items = groupItems(tail.drop(3)),
        )
    }

    /** Decodes the item list carried in the RIGHT code, using the LEFT code's [encoding]. */
    fun parseRightItems(rightBytes: ByteArray, encoding: InvoiceTextEncoding): List<ParsedItem> {
        if (rightBytes.size < 2 || rightBytes[0] != RIGHT_PREFIX[0] || rightBytes[1] != RIGHT_PREFIX[1]) {
            throw EInvoiceQrException.MalformedRightCode("right code must start with ** (0x2A2A)")
        }
        val payload = rightBytes.copyOfRange(2, rightBytes.size)
        val decoded = try {
            decodeStrict(payload, encoding.toCharset())
        } catch (e: CharacterCodingException) {
            throw EInvoiceQrException.MalformedRightCode("cannot decode right code as $encoding: ${e.message}")
        }
        return groupItems(decoded.trimStart(':').split(':'))
    }

    /** Convenience: parse both codes and merge into a single invoice. */
    fun parse(leftQr: String, rightBytes: ByteArray?): ParsedInvoice {
        val left = parseLeft(leftQr)
        val rightItems = rightBytes?.let { parseRightItems(it, left.encoding) }.orEmpty()
        return ParsedInvoice(
            invoiceNumber = left.invoiceNumber,
            issueDate = left.issueDate,
            randomCode = left.randomCode,
            untaxedAmount = left.untaxedAmount,
            totalAmount = left.totalAmount,
            buyerTaxId = left.buyerTaxId,
            sellerTaxId = left.sellerTaxId,
            encoding = left.encoding,
            items = left.items + rightItems,
        )
    }

    private fun parseRocDate(field: String): LocalDate {
        val year = field.substring(0, 3).toIntOrNull()
        val month = field.substring(3, 5).toIntOrNull()
        val day = field.substring(5, 7).toIntOrNull()
        if (year == null || month == null || day == null) {
            throw EInvoiceQrException.MalformedLeftCode("invalid ROC date '$field'")
        }
        return try {
            LocalDate(year + 1911, month, day)
        } catch (e: IllegalArgumentException) {
            throw EInvoiceQrException.MalformedLeftCode("invalid ROC date '$field': ${e.message}")
        }
    }

    /** Non-spec Gregorian YYYYMMDD variant used by some POS (KFC, 瑪可希維). */
    private fun parseGregorianDate(field: String): LocalDate {
        val year = field.substring(0, 4).toIntOrNull()
        val month = field.substring(4, 6).toIntOrNull()
        val day = field.substring(6, 8).toIntOrNull()
        if (year == null || month == null || day == null) {
            throw EInvoiceQrException.MalformedLeftCode("invalid Gregorian date '$field'")
        }
        return try {
            LocalDate(year, month, day)
        } catch (e: IllegalArgumentException) {
            throw EInvoiceQrException.MalformedLeftCode("invalid Gregorian date '$field': ${e.message}")
        }
    }

    private fun parseHexAmount(field: String, label: String): Int = field.toLongOrNull(radix = 16)?.toInt()
        ?: throw EInvoiceQrException.MalformedLeftCode("invalid hex $label '$field'")

    /** Groups a flat `name:qty:price:name:qty:price...` token list into items.
     *
     *  - Right code is space-padded to a fixed length → trim qty/price.
     *  - Gas-station invoices carry **decimal qty AND decimal price** (30.32 L × NT$33.9),
     *    so both go through Double parsing first. Unit-price rounds to the nearest dollar
     *    to fit the InvoiceItem.unitPrice: Int contract — < NT$1 precision loss is fine
     *    for receipt items; the invoice header's totalAmount is the source of truth for
     *    money totals anyway. */
    private fun groupItems(tokens: List<String>): List<ParsedItem> {
        val items = mutableListOf<ParsedItem>()
        var i = 0
        while (i + 2 < tokens.size) {
            val quantity = tokens[i + 1].trim().toDoubleOrNull()
            val unitPrice = tokens[i + 2].trim().toDoubleOrNull()
            if (quantity == null || unitPrice == null) break
            items += ParsedItem(
                name = tokens[i],
                quantity = quantity,
                unitPrice = kotlin.math.round(unitPrice).toInt(),
            )
            i += 3
        }
        return items
    }

    private fun InvoiceTextEncoding.toCharset(): Charset = when (this) {
        InvoiceTextEncoding.UTF8 -> Charsets.UTF_8
        InvoiceTextEncoding.BIG5 -> Charset.forName("Big5")
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }
}
