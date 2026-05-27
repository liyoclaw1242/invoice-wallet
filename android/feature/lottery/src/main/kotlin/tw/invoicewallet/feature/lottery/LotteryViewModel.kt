package tw.invoicewallet.feature.lottery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.database.repository.LotteryRepository
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.LotteryNumber
import tw.invoicewallet.core.model.LotteryStatus
import javax.inject.Inject

data class InvoiceLotteryResult(val invoice: Invoice, val result: LotteryResult)

data class LotteryUiState(
    val isLoading: Boolean = false,
    val latestNumbers: LotteryNumber? = null,
    val results: List<InvoiceLotteryResult> = emptyList(),
) {
    val winners: List<InvoiceLotteryResult> get() = results.filter { it.result is LotteryResult.Won }
}

@HiltViewModel
class LotteryViewModel @Inject constructor(
    private val apiClient: LotteryApiClient,
    private val lotteryRepository: LotteryRepository,
    private val invoiceRepository: InvoiceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LotteryUiState())
    val uiState: StateFlow<LotteryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Best effort: refresh the cached winning numbers; offline → use whatever is cached.
            runCatching { apiClient.fetchRecent() }
                .getOrDefault(emptyList())
                .forEach { lotteryRepository.upsert(it) }

            val invoices = invoiceRepository.observeAll().first()
            val results = invoices.map { invoice ->
                val numbers = lotteryRepository.getByPeriod(invoice.issuePeriod)
                val result = numbers?.let { LotteryMatcher.match(invoice, it) }
                    ?: LotteryResult.NotApplicable
                persist(invoice, result)
                InvoiceLotteryResult(invoice, result)
            }

            _uiState.value = LotteryUiState(
                isLoading = false,
                latestNumbers = lotteryRepository.latest(),
                results = results,
            )
        }
    }

    /** Writes the checked result back to the invoice so the list badge reflects it.
     *  Leaves CLAIMED untouched and NotApplicable (undrawn) unchanged; only writes on change. */
    private suspend fun persist(invoice: Invoice, result: LotteryResult) {
        if (invoice.lotteryStatus == LotteryStatus.CLAIMED) return
        val (status, prize) = when (result) {
            is LotteryResult.Won -> LotteryStatus.CHECKED_WON to result.prize.amountTwd
            LotteryResult.NoPrize -> LotteryStatus.CHECKED_NO_PRIZE to null
            LotteryResult.NotApplicable -> return
        }
        if (invoice.lotteryStatus != status || invoice.lotteryPrize != prize) {
            invoiceRepository.upsert(invoice.copy(lotteryStatus = status, lotteryPrize = prize))
        }
    }
}
