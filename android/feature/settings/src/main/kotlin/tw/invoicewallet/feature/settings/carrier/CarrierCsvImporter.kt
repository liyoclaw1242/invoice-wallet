package tw.invoicewallet.feature.settings.carrier

import kotlinx.datetime.Clock
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceItem
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists parsed MOF carrier CSV rows into the wallet.
 *
 * Dedup policy (chosen by the user — CSV-authoritative):
 *   - new invoice → create with a fresh UUID and `source = CARRIER_API`.
 *   - existing invoice (matched by 字軌號碼) → overwrite the fields the CSV provides
 *     (date, totals, seller, buyer); **preserve** the user-owned columns the CSV
 *     can't see — `userNote`, `userTags`, `lotteryStatus`, `lotteryPrize`,
 *     `imagePath`, `carrierIdEncrypted`, `createdAt` — and replace the line items.
 *
 * Items always come fully from the CSV; the previous item set is dropped via
 * [InvoiceRepository.upsertWithItems].
 */
@Singleton
class CarrierCsvImporter @Inject constructor(private val repository: InvoiceRepository, private val clock: Clock) {

    suspend fun import(rows: List<CarrierInvoiceRow>): ImportResult {
        var added = 0
        var updated = 0
        val now = clock.now()
        rows.forEach { row ->
            val existing = repository.getByInvoiceNumber(row.invoiceNumber)
            val invoice = if (existing == null) {
                added++
                row.toNewInvoice(now)
            } else {
                updated++
                row.mergeOnto(existing, now)
            }
            repository.upsertWithItems(invoice, row.items.toModel(invoice.id))
        }
        return ImportResult(added = added, updated = updated, skipped = 0)
    }

    private fun CarrierInvoiceRow.toNewInvoice(now: kotlinx.datetime.Instant): Invoice = Invoice(
        id = UUID.randomUUID().toString(),
        invoiceNumber = invoiceNumber,
        issueDate = issueDate,
        issuePeriod = issueDate.toInvoicePeriod(),
        merchantName = sellerName,
        merchantTaxId = sellerTaxId,
        buyerTaxId = buyerTaxId,
        carrierIdEncrypted = null,
        totalAmount = totalAmount,
        // The CSV doesn't break the total into untaxed + tax; we leave tax at 0
        // rather than guessing 5 % (some categories are zero-rated).
        taxAmount = 0,
        randomCode = "",
        category = null,
        source = InvoiceSource.CARRIER_API,
        ocrConfidence = null,
        lotteryStatus = LotteryStatus.PENDING,
        lotteryPrize = null,
        imagePath = null,
        userNote = null,
        userTags = emptyList(),
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )

    /** CSV wins on the columns it provides; user-owned columns and provenance survive. */
    private fun CarrierInvoiceRow.mergeOnto(existing: Invoice, now: kotlinx.datetime.Instant): Invoice = existing.copy(
        issueDate = issueDate,
        issuePeriod = issueDate.toInvoicePeriod(),
        merchantName = sellerName,
        merchantTaxId = sellerTaxId,
        buyerTaxId = buyerTaxId,
        totalAmount = totalAmount,
        updatedAt = now,
        deletedAt = null, // re-importing a previously soft-deleted invoice resurrects it
    )

    private fun List<CarrierItemRow>.toModel(invoiceId: String): List<InvoiceItem> = mapIndexed { index, item ->
        InvoiceItem(
            id = UUID.randomUUID().toString(),
            invoiceId = invoiceId,
            name = item.name,
            quantity = item.quantity,
            unitPrice = item.unitPrice,
            amount = item.amount,
            category = null,
            sequence = index,
        )
    }

    private fun kotlinx.datetime.LocalDate.toInvoicePeriod(): String {
        val rocYear = year - 1911
        val period = (monthNumber + 1) / 2
        return "%03d%02d".format(rocYear, period)
    }
}

/** What an import did. [skipped] is reserved for future filtering needs. */
data class ImportResult(val added: Int, val updated: Int, val skipped: Int)
