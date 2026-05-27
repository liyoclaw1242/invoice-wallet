package tw.invoicewallet.feature.scan.merchant

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache over a [MerchantNameSource] so each tax ID hits the network at most
 * once per process. Source failures are swallowed (returns null) so a lookup never
 * breaks the scan flow — the user just fills the name in manually.
 */
class CachingMerchantDirectory(private val source: MerchantNameSource) : MerchantDirectory {

    private val cache = ConcurrentHashMap<String, String>()

    override suspend fun nameFor(taxId: String): String? {
        cache[taxId]?.let { return it }
        val name = runCatching { source.fetch(taxId) }.getOrNull()
        if (!name.isNullOrBlank()) cache[taxId] = name
        return name
    }
}
