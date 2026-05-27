package tw.invoicewallet.feature.invoicelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
                title = { Text("發票錢包") },
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
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("search-field"),
            )
            if (uiState.invoices.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("invoice-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (uiState.summary.totalCount > 0) {
                        item(key = "summary") { SummaryCard(uiState.summary) }
                    }
                    groupByMonth(uiState.invoices).forEach { (header, group) ->
                        item(key = "h-$header") { MonthHeader(header) }
                        items(group, key = { it.id }) { invoice ->
                            InvoiceCard(invoice = invoice, onClick = { onInvoiceClick(invoice.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: InvoiceSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${summary.month} 月花費",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                money(summary.monthTotal),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "本月 ${summary.monthCount} 張・共 ${summary.totalCount} 張發票" +
                    if (summary.pendingLotteryCount > 0) "・${summary.pendingLotteryCount} 張待對獎" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun MonthHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = invoice.merchantName.ifBlank { "（未填商店）" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val date = invoice.issueDate
                Text(
                    text = "${invoice.formattedNumber()}・${date.monthNumber}/${date.dayOfMonth}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LotteryBadge(invoice.lotteryStatus)
            }
            Text(
                text = money(invoice.totalAmount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LotteryBadge(status: LotteryStatus) {
    val (label, container, content) = badgeStyle(status)
    Surface(color = container, shape = MaterialTheme.shapes.small) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private data class BadgeStyle(val label: String, val container: Color, val content: Color)

@Composable
private fun badgeStyle(status: LotteryStatus): BadgeStyle {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        LotteryStatus.PENDING -> BadgeStyle("待對獎", scheme.secondaryContainer, scheme.onSecondaryContainer)
        LotteryStatus.CHECKED_NO_PRIZE -> BadgeStyle("未中獎", scheme.surfaceVariant, scheme.onSurfaceVariant)
        LotteryStatus.CHECKED_WON -> BadgeStyle("🎉 中獎", Color(0xFF1B5E20), Color.White)
        LotteryStatus.CLAIMED -> BadgeStyle("已領獎", scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
}

/** Groups invoices into month sections, preserving the newest-first input order. */
private fun groupByMonth(invoices: List<Invoice>): List<Pair<String, List<Invoice>>> =
    invoices.groupBy { it.issueDate.year to it.issueDate.monthNumber }
        .map { (ym, list) -> "${ym.first} 年 ${ym.second} 月" to list }

private fun money(amount: Int): String = "NT$%,d".format(amount)
