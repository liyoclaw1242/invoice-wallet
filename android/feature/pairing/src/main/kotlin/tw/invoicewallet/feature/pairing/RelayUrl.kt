package tw.invoicewallet.feature.pairing

/**
 * Normalises a user-entered relay URL to HTTPS. The relay always sits behind TLS
 * (Cloudflare Tunnel), and Android forbids cleartext by default — so a missing or
 * `http://` scheme is upgraded to `https://`, and a trailing slash is dropped.
 */
internal fun normalizeRelayUrl(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return trimmed
    return when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring("http://".length)
        else -> "https://$trimmed"
    }
}
