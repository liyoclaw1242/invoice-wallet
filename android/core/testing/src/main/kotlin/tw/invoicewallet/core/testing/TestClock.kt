package tw.invoicewallet.core.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Fixture-builder seed: a deterministic [Clock] for tests that need stable
 * timestamps. Domain-specific builders (e.g. an Invoice builder) are added in
 * Iteration 1 once :core:model exists.
 */
object TestClock {
    val DEFAULT_INSTANT: Instant = Instant.parse("2026-01-01T00:00:00Z")

    fun fixed(instant: Instant = DEFAULT_INSTANT): Clock = Clock.fixed(instant, ZoneOffset.UTC)
}
