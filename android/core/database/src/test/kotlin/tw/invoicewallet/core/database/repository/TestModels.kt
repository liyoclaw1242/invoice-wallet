package tw.invoicewallet.core.database.repository

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import tw.invoicewallet.core.model.AuthChannel
import tw.invoicewallet.core.model.AuthGrant
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceItem
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryNumber
import tw.invoicewallet.core.model.LotteryStatus

private val T0 = Instant.parse("2026-01-15T10:00:00Z")

internal fun invoice(
    id: String = "inv-1",
    invoiceNumber: String = "AB12345678",
    issueDate: LocalDate = LocalDate(2026, 1, 15),
    merchantTaxId: String? = "12345678",
    userNote: String? = null,
) = Invoice(
    id = id,
    invoiceNumber = invoiceNumber,
    issueDate = issueDate,
    issuePeriod = "11502",
    merchantName = "商店",
    merchantTaxId = merchantTaxId,
    buyerTaxId = null,
    carrierIdEncrypted = null,
    totalAmount = 100,
    taxAmount = 5,
    randomCode = "1234",
    category = null,
    source = InvoiceSource.QR_CODE,
    ocrConfidence = null,
    lotteryStatus = LotteryStatus.PENDING,
    lotteryPrize = null,
    imagePath = null,
    userNote = userNote,
    userTags = emptyList(),
    createdAt = T0,
    updatedAt = T0,
    deletedAt = null,
)

internal fun invoiceItem(
    id: String = "item-1",
    invoiceId: String = "inv-1",
    name: String = "超厚雞肉起司滿分堡",
    quantity: Double = 1.0,
    unitPrice: Int = 85,
    sequence: Int = 0,
) = InvoiceItem(
    id = id,
    invoiceId = invoiceId,
    name = name,
    quantity = quantity,
    unitPrice = unitPrice,
    amount = (quantity * unitPrice).toInt(),
    category = null,
    sequence = sequence,
)

internal fun lotteryNumber(period: String = "11502") = LotteryNumber(
    period = period,
    specialPrize = "12345678",
    grandPrize = "87654321",
    firstPrize = listOf("11112222"),
    additionalSixth = listOf("333"),
    fetchedAt = T0,
)

internal fun authGrant(id: String = "grant-1") = AuthGrant(
    id = id,
    clientName = "Claude Mobile",
    channel = AuthChannel.RELAY,
    scopes = listOf("list_invoices"),
    excludedCategories = null,
    dateRangeDays = 90,
    grantedAt = T0,
    expiresAt = null,
    revokedAt = null,
)
