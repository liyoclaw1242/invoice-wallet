package tw.invoicewallet.feature.invoicelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.model.Invoice
import javax.inject.Inject

/** Live, searchable list of saved invoices (newest first, per the repository order). */
@HiltViewModel
class InvoiceListViewModel @Inject constructor(repository: InvoiceRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<InvoiceListUiState> =
        combine(repository.observeAll(), query) { invoices, q ->
            InvoiceListUiState(invoices = invoices.filter { it.matches(q) }, query = q)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = InvoiceListUiState(),
        )

    fun onQueryChange(value: String) {
        query.value = value
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

data class InvoiceListUiState(val invoices: List<Invoice> = emptyList(), val query: String = "")
