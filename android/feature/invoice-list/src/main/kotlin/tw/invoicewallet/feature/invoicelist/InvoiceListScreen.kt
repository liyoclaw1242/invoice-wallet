package tw.invoicewallet.feature.invoicelist

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import tw.invoicewallet.core.designsystem.R
import tw.invoicewallet.core.designsystem.components.CompactPill
import tw.invoicewallet.core.designsystem.components.CompactPillTone
import tw.invoicewallet.core.designsystem.components.PillButton
import tw.invoicewallet.core.designsystem.components.SectionLabel
import tw.invoicewallet.core.designsystem.theme.WalletTheme
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.LotteryStatus

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
    WalletTheme {
        Scaffold(
            modifier = modifier,
            containerColor = WalletTheme.colors.surfaceBase,
            topBar = {
                TopAppBar(
                    title = {},
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = WalletTheme.colors.surfaceBase,
                    ),
                    actions = {
                        TextButton(onClick = onSettingsClick, modifier = Modifier.testTag("settings-action")) {
                            Text(
                                "設定",
                                style = WalletTheme.typography.pillLabel,
                                color = WalletTheme.colors.inkSecondary,
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                PillButton(
                    text = "掃描發票",
                    onClick = onScanClick,
                    modifier = Modifier.testTag("scan-fab"),
                )
            },
            floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center,
        ) { padding ->
            if (uiState.invoices.isEmpty() && uiState.query.isBlank()) {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onScanClick = onScanClick,
                )
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("invoice-list"),
                contentPadding = PaddingValues(
                    start = WalletTheme.spacing.lg,
                    end = WalletTheme.spacing.lg,
                    top = WalletTheme.spacing.sm,
                    bottom = 96.dp, // leave room for the floating scan pill
                ),
                verticalArrangement = Arrangement.spacedBy(WalletTheme.spacing.sm),
            ) {
                if (uiState.summary.totalCount > 0) {
                    item(key = "hero") {
                        HeroStats(summary = uiState.summary, onLotteryClick = onLotteryClick)
                    }
                }
                item(key = "search") {
                    SearchField(query = uiState.query, onQueryChange = onQueryChange)
                }
                if (uiState.invoices.isEmpty()) {
                    item(key = "no-match") { NoSearchMatchRow(uiState.query) }
                } else {
                    groupByMonth(uiState.invoices).forEach { (header, group) ->
                        item(key = "h-$header") { SectionLabel(text = header) }
                        items(group, key = { it.id }) { invoice ->
                            InvoiceRow(invoice = invoice, onClick = { onInvoiceClick(invoice.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStats(summary: InvoiceSummary, onLotteryClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WalletTheme.spacing.md, bottom = WalletTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(WalletTheme.spacing.xs),
    ) {
        Text(
            text = "${summary.month} 月花費".uppercase(),
            style = WalletTheme.typography.microCaps,
            color = WalletTheme.colors.inkTertiary,
        )
        Text(
            text = money(summary.monthTotal),
            style = WalletTheme.typography.displayLg,
            color = WalletTheme.colors.inkPrimary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WalletTheme.spacing.xs),
        ) {
            Text(
                text = "本月 ${summary.monthCount} 張 · 共 ${summary.totalCount} 張",
                style = WalletTheme.typography.bodyMd,
                color = WalletTheme.colors.inkSecondary,
            )
        }
        if (summary.pendingLotteryCount > 0) {
            CompactPill(
                text = "${summary.pendingLotteryCount} 張待對獎",
                tone = CompactPillTone.Coral,
                onClick = onLotteryClick,
                modifier = Modifier.testTag("lottery-action"),
            )
        } else {
            CompactPill(
                text = "對獎",
                tone = CompactPillTone.Neutral,
                onClick = onLotteryClick,
                modifier = Modifier.testTag("lottery-action"),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text("搜尋商店、發票號碼、備註", style = WalletTheme.typography.bodyMd)
        },
        singleLine = true,
        shape = WalletTheme.shapes.pill,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = WalletTheme.colors.surfaceElevated,
            unfocusedContainerColor = WalletTheme.colors.surfaceElevated,
            focusedIndicatorColor = WalletTheme.colors.accentTeal,
            unfocusedIndicatorColor = WalletTheme.colors.divider,
            focusedTextColor = WalletTheme.colors.inkPrimary,
            unfocusedTextColor = WalletTheme.colors.inkPrimary,
            focusedPlaceholderColor = WalletTheme.colors.inkTertiary,
            unfocusedPlaceholderColor = WalletTheme.colors.inkTertiary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WalletTheme.spacing.xs)
            .testTag("search-field"),
    )
}

@Composable
private fun InvoiceRow(invoice: Invoice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = WalletTheme.spacing.sm, horizontal = WalletTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WalletTheme.spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = invoice.merchantName.ifBlank { "（未填商店）" },
                style = WalletTheme.typography.title,
                color = WalletTheme.colors.inkPrimary,
            )
            val d = invoice.issueDate
            Text(
                text = "%d/%02d/%02d".format(d.year, d.monthNumber, d.dayOfMonth),
                style = WalletTheme.typography.caption,
                color = WalletTheme.colors.inkTertiary,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = money(invoice.totalAmount),
                style = WalletTheme.typography.displaySm,
                color = WalletTheme.colors.inkPrimary,
            )
            LotteryMarker(invoice.lotteryStatus, invoice.lotteryPrize)
        }
    }
}

@Composable
private fun LotteryMarker(status: LotteryStatus, prize: Int?) {
    val (text, color) = when (status) {
        LotteryStatus.PENDING -> "待對獎" to WalletTheme.colors.inkTertiary
        LotteryStatus.CHECKED_NO_PRIZE -> "未中獎" to WalletTheme.colors.inkTertiary
        LotteryStatus.CHECKED_WON -> "中獎${prize?.let { " " + money(it) }.orEmpty()}" to
            WalletTheme.colors.accentTealDeep
        LotteryStatus.CLAIMED -> "已領獎" to WalletTheme.colors.accentTealDeep
    }
    Text(text, style = WalletTheme.typography.caption, color = color)
}

@Composable
private fun NoSearchMatchRow(query: String) {
    Text(
        text = "找不到符合「$query」的發票。",
        style = WalletTheme.typography.bodyMd,
        color = WalletTheme.colors.inkTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WalletTheme.spacing.xl),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onScanClick: () -> Unit) {
    Column(
        modifier = modifier.testTag("empty-state").padding(horizontal = WalletTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.illust_empty_list),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
        )
        Spacer(Modifier.height(WalletTheme.spacing.md))
        Text(
            text = "還沒有發票",
            style = WalletTheme.typography.displaySm,
            color = WalletTheme.colors.inkPrimary,
        )
        Spacer(Modifier.height(WalletTheme.spacing.xs))
        Text(
            text = "對著發票掃,自動歸入錢包。",
            style = WalletTheme.typography.bodyLg,
            color = WalletTheme.colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(WalletTheme.spacing.lg))
        PillButton(text = "開始掃描", onClick = onScanClick, modifier = Modifier.testTag("empty-state-scan"))
    }
}

/** Groups invoices into month sections, preserving newest-first input order. */
private fun groupByMonth(invoices: List<Invoice>): List<Pair<String, List<Invoice>>> =
    invoices.groupBy { it.issueDate.year to it.issueDate.monthNumber }
        .map { (ym, list) -> "${ym.first} 年 ${ym.second} 月" to list }

private fun money(amount: Int): String = "NT$%,d".format(amount)
