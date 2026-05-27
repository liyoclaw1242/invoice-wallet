package tw.invoicewallet.core.database

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import tw.invoicewallet.core.database.entity.AuthGrantEntity
import tw.invoicewallet.core.database.entity.InvoiceEntity
import tw.invoicewallet.core.database.entity.InvoiceItemEntity
import tw.invoicewallet.core.database.entity.LotteryNumberEntity
import tw.invoicewallet.core.database.entity.QueryAuditLogEntity

private val T0 = Instant.parse("2026-01-15T10:00:00Z")

internal fun invoiceEntity(
    id: String = "inv-1",
    invoiceNumber: String = "AB12345678",
    issueDate: LocalDate = LocalDate(2026, 1, 15),
    merchantName: String = "商店",
    merchantTaxId: String? = "12345678",
    userNote: String? = null,
    deletedAt: Instant? = null,
) = InvoiceEntity(
    id = id,
    invoiceNumber = invoiceNumber,
    issueDate = issueDate,
    issuePeriod = "11502",
    merchantName = merchantName,
    merchantTaxId = merchantTaxId,
    buyerTaxId = null,
    carrierIdEncrypted = null,
    totalAmount = 100,
    taxAmount = 5,
    currency = "TWD",
    randomCode = "1234",
    category = null,
    source = "QR_CODE",
    ocrConfidence = null,
    lotteryStatus = "PENDING",
    lotteryPrize = null,
    imagePath = null,
    userNote = userNote,
    userTags = emptyList(),
    createdAt = T0,
    updatedAt = T0,
    deletedAt = deletedAt,
)

internal fun invoiceItemEntity(id: String, invoiceId: String = "inv-1", name: String = "商品", sequence: Int = 0) =
    InvoiceItemEntity(
        id = id,
        invoiceId = invoiceId,
        name = name,
        quantity = 1.0,
        unitPrice = 100,
        amount = 100,
        category = null,
        sequence = sequence,
    )

internal fun lotteryNumberEntity(period: String = "11502", specialPrize: String = "12345678") = LotteryNumberEntity(
    period = period,
    specialPrize = specialPrize,
    grandPrize = "87654321",
    firstPrize = listOf("11112222"),
    additionalSixth = listOf("333"),
    fetchedAt = T0,
)

internal fun authGrantEntity(id: String = "grant-1", channel: String = "RELAY", revokedAt: Instant? = null) =
    AuthGrantEntity(
        id = id,
        clientName = "Claude Mobile",
        channel = channel,
        scopes = listOf("list_invoices"),
        excludedCategories = null,
        dateRangeDays = 90,
        grantedAt = T0,
        expiresAt = null,
        revokedAt = revokedAt,
    )

internal fun queryAuditLogEntity(id: String, grantId: String = "grant-1", executedAt: Instant = T0) =
    QueryAuditLogEntity(
        id = id,
        grantId = grantId,
        toolName = "list_invoices",
        resultCount = 3,
        executedAt = executedAt,
    )
