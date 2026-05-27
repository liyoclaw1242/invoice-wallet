package tw.invoicewallet.feature.invoicelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.LotteryStatus
import tw.invoicewallet.core.model.formattedNumber

@Composable
fun InvoiceListRoute(
    onScanClick: () -> Unit,
    onInvoiceClick: (String) -> Unit,
    onLotteryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InvoiceListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    InvoiceListScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onScanClick = onScanClick,
        onInvoiceClick = onInvoiceClick,
        onLotteryClick = onLotteryClick,
        onSettingsClick = onSettingsClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    uiState: InvoiceListUiState,
    onQueryChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onInvoiceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLotteryClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Invoice Wallet") },
                actions = {
                    TextButton(onClick = onLotteryClick, modifier = Modifier.testTag("lottery-action")) { Text("對獎") }
                    TextButton(onClick = onSettingsClick, modifier = Modifier.testTag("settings-action")) { Text("設定") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanClick,
                modifier = Modifier.testTag("scan-fab"),
            ) {
                Text("掃描發票")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                label = { Text("搜尋商店、發票號碼或備註") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("search-field"),
            )
            if (uiState.invoices.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("invoice-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.invoices, key = { it.id }) { invoice ->
                        InvoiceCard(invoice = invoice, onClick = { onInvoiceClick(invoice.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("empty-state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("還沒有發票", style = MaterialTheme.typography.titleMedium)
        Text("點右下角「掃描發票」加入第一張", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InvoiceCard(invoice: Invoice, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = invoice.merchantName.ifBlank { "（未填商店）" },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${invoice.formattedNumber()}・${invoice.issueDate}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "NT$${invoice.totalAmount}・${invoice.lotteryStatus.label()}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun LotteryStatus.label(): String = when (this) {
    LotteryStatus.PENDING -> "未對獎"
    LotteryStatus.CHECKED_NO_PRIZE -> "未中獎"
    LotteryStatus.CHECKED_WON -> "中獎"
    LotteryStatus.CLAIMED -> "已領獎"
}
