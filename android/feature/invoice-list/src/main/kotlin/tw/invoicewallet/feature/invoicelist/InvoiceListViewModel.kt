package tw.invoicewallet.feature.invoicelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.designsystem.components.CategoryGuesser
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.LotteryStatus
import javax.inject.Inject

/** Live, searchable list of saved invoices with a hero summary the user can swipe through month-by-month. */
@HiltViewModel
class InvoiceListViewModel @Inject constructor(repository: InvoiceRepository, private val clock: Clock) : ViewModel() {

    private val query = MutableStateFlow("")
    private val timeZone = TimeZone.currentSystemDefault()

    // Today's year-month is the default focus; the user nudges with onMonthChange(±1).
    private val today: LocalDate get() = clock.now().toLocalDateTime(timeZone).date
    private val focusedMonth = MutableStateFlow(YearMonth.of(today))

    // null == "全部" (no category filter applied).
    private val selectedCategory = MutableStateFlow<String?>(null)

    val uiState: StateFlow<InvoiceListUiState> =
        combine(
            repository.observeAll(),
            query,
            focusedMonth,
            selectedCategory,
        ) { invoices, q, focus, category ->
            val bounds = invoiceMonthBounds(invoices)
            // Counts per category facet — derived once over all invoices so chips show
            // global totals (independent of search query / month focus).
            val facets = invoices
                .mapNotNull { categoryOf(it) }
                .groupingBy { it }
                .eachCount()
                .map { (slug, count) -> CategoryFacet(slug, CategoryGuesser.label(slug), count) }
                .sortedByDescending { it.count }
            // Filter pipeline: query → category. Both axes intersect.
            val visible = invoices
                .filter { it.matches(q) }
                .filter { category == null || categoryOf(it) == category }
            InvoiceListUiState(
                invoices = visible,
                query = q,
                summary = summarize(invoices, focus, bounds),
                categories = facets,
                selectedCategory = category,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = InvoiceListUiState(),
        )

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Toggle a category chip: pass the slug to filter, or `null` for 「全部」. */
    fun onCategoryChange(slug: String?) {
        selectedCategory.value = slug
    }

    /** Explicit invoice.category wins; otherwise heuristic-guess from merchant name. */
    private fun categoryOf(invoice: Invoice): String? =
        invoice.category?.takeIf { it.isNotBlank() } ?: CategoryGuesser.guess(invoice.merchantName)

    /** Step the hero one month forward (+1) or back (-1), clamped to the data's bounds. */
    fun shiftFocusedMonth(delta: Int) {
        val bounds = invoiceMonthBoundsOrToday()
        val next = focusedMonth.value.plusMonths(delta)
        focusedMonth.value = next.coerceIn(bounds.earliest, bounds.latest)
    }

    private fun invoiceMonthBoundsOrToday(): MonthBounds {
        val all = (uiState.value.invoices).ifEmpty { emptyList<Invoice>() }
        return invoiceMonthBounds(all)
    }

    private fun invoiceMonthBounds(invoices: List<Invoice>): MonthBounds {
        val now = YearMonth.of(today)
        if (invoices.isEmpty()) return MonthBounds(earliest = now, latest = now)
        val earliest = invoices.minOf { YearMonth.of(it.issueDate) }
        // The latest reachable month is always at most "today" — even if the user has an
        // invoice dated in the future for some reason, browsing past "now" feels broken.
        val latest = now
        return MonthBounds(earliest = earliest, latest = latest)
    }

    private fun summarize(all: List<Invoice>, focus: YearMonth, bounds: MonthBounds): InvoiceSummary {
        val month = all.filter { YearMonth.of(it.issueDate) == focus }
        // Drop the year prefix when it matches today's — feels redundant for the common
        // "browsing this year" case.
        val label = if (focus.year == today.year) "${focus.month} 月" else "${focus.year} 年 ${focus.month} 月"
        return InvoiceSummary(
            year = focus.year,
            month = focus.month,
            monthLabel = label,
            monthTotal = month.sumOf { it.totalAmount },
            monthCount = month.size,
            pendingLotteryCount = all.count { it.lotteryStatus == LotteryStatus.PENDING },
            totalCount = all.size,
            canGoPrev = focus > bounds.earliest,
            canGoNext = focus < bounds.latest,
        )
    }

    private fun Invoice.matches(q: String): Boolean {
        if (q.isBlank()) return true
        return merchantName.contains(q, ignoreCase = true) ||
            invoiceNumber.contains(q, ignoreCase = true) ||
            userNote?.contains(q, ignoreCase = true) == true
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Year-month, ordered, with one-month arithmetic. Lighter than pulling in java.time YearMonth. */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    operator fun plus(months: Int): YearMonth = plusMonths(months)

    fun plusMonths(delta: Int): YearMonth {
        // Convert to zero-based month-index, add, normalise.
        val total = year * 12 + (month - 1) + delta
        return YearMonth(year = total / 12, month = total % 12 + 1)
    }

    override fun compareTo(other: YearMonth): Int = if (year != other.year) {
        year - other.year
    } else {
        month - other.month
    }

    companion object {
        fun of(date: LocalDate): YearMonth = YearMonth(date.year, date.monthNumber)
    }
}

private data class MonthBounds(val earliest: YearMonth, val latest: YearMonth)

private fun YearMonth.coerceIn(earliest: YearMonth, latest: YearMonth): YearMonth = when {
    this < earliest -> earliest
    this > latest -> latest
    else -> this
}

/** Headline numbers shown above the list — for the currently focused [month] of [year]. */
data class InvoiceSummary(
    val year: Int = 0,
    val month: Int = 0,
    /** Pre-formatted label for the focused month — "5 月" / "2025 年 12 月". */
    val monthLabel: String = "",
    val monthTotal: Int = 0,
    val monthCount: Int = 0,
    val pendingLotteryCount: Int = 0,
    val totalCount: Int = 0,
    val canGoPrev: Boolean = false,
    val canGoNext: Boolean = false,
)

/** One category chip — the slug feeds icon/label lookup, count drives the chip's badge. */
data class CategoryFacet(val slug: String, val label: String, val count: Int)

data class InvoiceListUiState(
    val invoices: List<Invoice> = emptyList(),
    val query: String = "",
    val summary: InvoiceSummary = InvoiceSummary(),
    /** Categories actually present in the wallet, sorted by count descending. */
    val categories: List<CategoryFacet> = emptyList(),
    /** Currently selected category slug; null means 「全部」 is active. */
    val selectedCategory: String? = null,
)
