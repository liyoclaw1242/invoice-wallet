package tw.invoicewallet.feature.invoicedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceItem
import javax.inject.Inject

sealed interface InvoiceDetailState {
    data object Loading : InvoiceDetailState
    data class Loaded(val invoice: Invoice, val items: List<InvoiceItem> = emptyList()) : InvoiceDetailState
    data object NotFound : InvoiceDetailState
    data object Deleted : InvoiceDetailState
}

@HiltViewModel
class InvoiceDetailViewModel @Inject constructor(
    private val repository: InvoiceRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val invoiceId: String = checkNotNull(savedStateHandle[INVOICE_ID_ARG]) {
        "InvoiceDetailViewModel requires a '$INVOICE_ID_ARG' argument"
    }

    private val _state = MutableStateFlow<InvoiceDetailState>(InvoiceDetailState.Loading)
    val state: StateFlow<InvoiceDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = repository.getById(invoiceId)
                ?.let { InvoiceDetailState.Loaded(it, repository.getItems(invoiceId)) }
                ?: InvoiceDetailState.NotFound
        }
    }

    /** Persists edits to the user-owned fields (note + tags). Header-only write — keeps items. */
    fun onSave(note: String, tags: List<String>) {
        val loaded = _state.value as? InvoiceDetailState.Loaded ?: return
        viewModelScope.launch {
            val updated = repository.upsert(
                loaded.invoice.copy(
                    userNote = note.ifBlank { null },
                    userTags = tags,
                    updatedAt = clock.now(),
                ),
            )
            _state.value = InvoiceDetailState.Loaded(updated, loaded.items)
        }
    }

    fun onDelete() {
        viewModelScope.launch {
            repository.softDelete(invoiceId)
            _state.value = InvoiceDetailState.Deleted
        }
    }

    companion object {
        const val INVOICE_ID_ARG = "invoiceId"
    }
}
