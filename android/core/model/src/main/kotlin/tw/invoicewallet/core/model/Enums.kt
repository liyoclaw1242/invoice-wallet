package tw.invoicewallet.core.model

/** How an invoice entered the wallet. */
enum class InvoiceSource { OCR, QR_CODE, CARRIER_API, MANUAL }

/** Lifecycle of an invoice's lottery check. */
enum class LotteryStatus { PENDING, CHECKED_NO_PRIZE, CHECKED_WON, CLAIMED }

/** Channel through which an external AI client was granted access. */
enum class AuthChannel { RELAY, LOCAL_MCP, CONTENT_PROVIDER }
