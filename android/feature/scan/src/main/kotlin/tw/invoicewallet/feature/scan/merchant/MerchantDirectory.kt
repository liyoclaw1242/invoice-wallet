package tw.invoicewallet.feature.scan.merchant

/** Resolves a store name from a (public) business tax ID. */
interface MerchantDirectory {
    suspend fun nameFor(taxId: String): String?
}

/** Fetches a store name from an external registry — one lookup, no caching. */
interface MerchantNameSource {
    suspend fun fetch(taxId: String): String?
}
