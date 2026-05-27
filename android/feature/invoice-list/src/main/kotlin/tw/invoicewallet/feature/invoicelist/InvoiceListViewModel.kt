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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.LotteryStatus
import javax.inject.Inject

/** Live, searchable list of saved invoices with a this-month spending summary. */
@HiltViewModel
class InvoiceListViewModel @Inject constructor(repository: InvoiceRepository, private val clock: Clock) : ViewModel() {

    private val query = MutableStateFlow("")
    private val timeZone = TimeZone.currentSystemDefault()

    val uiState: StateFlow<InvoiceListUiState> =
        combine(repository.observeAll(), query) { invoices, q ->
            InvoiceListUiState(
                invoices = invoices.filter { it.matches(q) },
                query = q,
                summary = summarize(invoices),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = InvoiceListUiState(),
        )

    fun onQueryChange(value: String) {
        query.value = value
    }

    private fun summarize(all: List<Invoice>): InvoiceSummary {
        val today = clock.now().toLocalDateTime(timeZone).date
        val thisMonth = all.filter { it.issueDate.year == today.year && it.issueDate.monthNumber == today.monthNumber }
        return InvoiceSummary(
            year = today.year,
            month = today.monthNumber,
            monthTotal = thisMonth.sumOf { it.totalAmount },
            monthCount = thisMonth.size,
            pendingLotteryCount = all.count { it.lotteryStatus == LotteryStatus.PENDING },
            totalCount = all.size,
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

/** Headline numbers shown above the list. */
data class InvoiceSummary(
    val year: Int = 0,
    val month: Int = 0,
    val monthTotal: Int = 0,
    val monthCount: Int = 0,
    val pendingLotteryCount: Int = 0,
    val totalCount: Int = 0,
)

data class InvoiceListUiState(
    val invoices: List<Invoice> = emptyList(),
    val query: String = "",
    val summary: InvoiceSummary = InvoiceSummary(),
)
