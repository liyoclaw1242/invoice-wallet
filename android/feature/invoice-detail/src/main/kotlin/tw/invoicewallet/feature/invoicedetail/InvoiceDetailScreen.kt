package tw.invoicewallet.feature.invoicedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.formattedNumber

@Composable
fun InvoiceDetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InvoiceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    InvoiceDetailScreen(
        state = state,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDelete,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    state: InvoiceDetailState,
    onSave: (note: String, tags: List<String>) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Leave the screen as soon as the invoice is deleted.
    LaunchedEffect(state) {
        if (state is InvoiceDetailState.Deleted) onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("發票明細") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        when (state) {
            is InvoiceDetailState.Loaded -> LoadedContent(state.invoice, onSave, onDelete, Modifier.padding(padding))
            InvoiceDetailState.Loading ->
                CircularProgressIndicator(Modifier.padding(padding).padding(24.dp).testTag("detail-loading"))
            InvoiceDetailState.NotFound ->
                Text("找不到這張發票", Modifier.padding(padding).padding(24.dp).testTag("detail-not-found"))
            InvoiceDetailState.Deleted -> Unit
        }
    }
}

@Composable
private fun LoadedContent(
    invoice: Invoice,
    onSave: (String, List<String>) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var note by remember(invoice.id) { mutableStateOf(invoice.userNote.orEmpty()) }
    var tags by remember(invoice.id) { mutableStateOf(invoice.userTags.joinToString(", ")) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()).testTag("detail-loaded"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(invoice.merchantName.ifBlank { "（未填商店）" }, style = MaterialTheme.typography.headlineSmall)
        Field("發票號碼", invoice.formattedNumber())
        Field("開立日期", invoice.issueDate.toString())
        Field("賣方統編", invoice.merchantTaxId ?: "—")
        Field("買方統編", invoice.buyerTaxId ?: "—")
        Field("總金額", "NT$${invoice.totalAmount}")
        Field("稅額", "NT$${invoice.taxAmount}")
        Field("隨機碼", invoice.randomCode.ifBlank { "—" })
        Field("來源", invoice.source.name)
        Field("對獎狀態", invoice.lotteryStatus.name)

        HorizontalDivider()
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("備註") },
            modifier = Modifier.fillMaxWidth().testTag("note-field"),
        )
        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it },
            label = { Text("標籤（以逗號分隔）") },
            modifier = Modifier.fillMaxWidth().testTag("tags-field"),
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onSave(note, tags.split(",").map(String::trim).filter(String::isNotBlank)) },
                modifier = Modifier.testTag("save-button"),
            ) {
                Text("儲存")
            }
            OutlinedButton(onClick = { confirmingDelete = true }, modifier = Modifier.testTag("delete-button")) {
                Text("刪除")
            }
        }

        Text(
            "品項明細尚未儲存（掃描目前僅保存發票表頭）。",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("刪除發票？") },
            text = { Text("這張發票會從清單移除（軟刪除，可日後復原）。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("confirm-delete"),
                ) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
