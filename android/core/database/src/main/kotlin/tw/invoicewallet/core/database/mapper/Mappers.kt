package tw.invoicewallet.core.database.mapper

import tw.invoicewallet.core.database.entity.AuthGrantEntity
import tw.invoicewallet.core.database.entity.InvoiceEntity
import tw.invoicewallet.core.database.entity.InvoiceItemEntity
import tw.invoicewallet.core.database.entity.LotteryNumberEntity
import tw.invoicewallet.core.database.entity.QueryAuditLogEntity
import tw.invoicewallet.core.model.AuthChannel
import tw.invoicewallet.core.model.AuthGrant
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceItem
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryNumber
import tw.invoicewallet.core.model.LotteryStatus
import tw.invoicewallet.core.model.QueryAuditLog

// --- Invoice ---

fun InvoiceEntity.asExternalModel(): Invoice = Invoice(
    id = id,
    invoiceNumber = invoiceNumber,
    issueDate = issueDate,
    issuePeriod = issuePeriod,
    merchantName = merchantName,
    merchantTaxId = merchantTaxId,
    buyerTaxId = buyerTaxId,
    carrierIdEncrypted = carrierIdEncrypted,
    totalAmount = totalAmount,
    taxAmount = taxAmount,
    currency = currency,
    randomCode = randomCode,
    category = category,
    source = InvoiceSource.valueOf(source),
    ocrConfidence = ocrConfidence,
    lotteryStatus = LotteryStatus.valueOf(lotteryStatus),
    lotteryPrize = lotteryPrize,
    imagePath = imagePath,
    userNote = userNote,
    userTags = userTags,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Invoice.asEntity(): InvoiceEntity = InvoiceEntity(
    id = id,
    invoiceNumber = invoiceNumber,
    issueDate = issueDate,
    issuePeriod = issuePeriod,
    merchantName = merchantName,
    merchantTaxId = merchantTaxId,
    buyerTaxId = buyerTaxId,
    carrierIdEncrypted = carrierIdEncrypted,
    totalAmount = totalAmount,
    taxAmount = taxAmount,
    currency = currency,
    randomCode = randomCode,
    category = category,
    source = source.name,
    ocrConfidence = ocrConfidence,
    lotteryStatus = lotteryStatus.name,
    lotteryPrize = lotteryPrize,
    imagePath = imagePath,
    userNote = userNote,
    userTags = userTags,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

// --- InvoiceItem ---

fun InvoiceItemEntity.asExternalModel(): InvoiceItem = InvoiceItem(
    id = id,
    invoiceId = invoiceId,
    name = name,
    quantity = quantity,
    unitPrice = unitPrice,
    amount = amount,
    category = category,
    sequence = sequence,
)

fun InvoiceItem.asEntity(): InvoiceItemEntity = InvoiceItemEntity(
    id = id,
    invoiceId = invoiceId,
    name = name,
    quantity = quantity,
    unitPrice = unitPrice,
    amount = amount,
    category = category,
    sequence = sequence,
)

// --- LotteryNumber ---

fun LotteryNumberEntity.asExternalModel(): LotteryNumber = LotteryNumber(
    period = period,
    specialPrize = specialPrize,
    grandPrize = grandPrize,
    firstPrize = firstPrize,
    additionalSixth = additionalSixth,
    fetchedAt = fetchedAt,
)

fun LotteryNumber.asEntity(): LotteryNumberEntity = LotteryNumberEntity(
    period = period,
    specialPrize = specialPrize,
    grandPrize = grandPrize,
    firstPrize = firstPrize,
    additionalSixth = additionalSixth,
    fetchedAt = fetchedAt,
)

// --- AuthGrant ---

fun AuthGrantEntity.asExternalModel(): AuthGrant = AuthGrant(
    id = id,
    clientName = clientName,
    channel = AuthChannel.valueOf(channel),
    scopes = scopes,
    excludedCategories = excludedCategories,
    dateRangeDays = dateRangeDays,
    grantedAt = grantedAt,
    expiresAt = expiresAt,
    revokedAt = revokedAt,
)

fun AuthGrant.asEntity(): AuthGrantEntity = AuthGrantEntity(
    id = id,
    clientName = clientName,
    channel = channel.name,
    scopes = scopes,
    excludedCategories = excludedCategories,
    dateRangeDays = dateRangeDays,
    grantedAt = grantedAt,
    expiresAt = expiresAt,
    revokedAt = revokedAt,
)

// --- QueryAuditLog ---

fun QueryAuditLogEntity.asExternalModel(): QueryAuditLog = QueryAuditLog(
    id = id,
    grantId = grantId,
    toolName = toolName,
    resultCount = resultCount,
    executedAt = executedAt,
)

fun QueryAuditLog.asEntity(): QueryAuditLogEntity = QueryAuditLogEntity(
    id = id,
    grantId = grantId,
    toolName = toolName,
    resultCount = resultCount,
    executedAt = executedAt,
)
