package tw.invoicewallet.feature.lottery

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import tw.invoicewallet.core.designsystem.components.SectionLabel
import tw.invoicewallet.core.designsystem.theme.WalletTheme
import tw.invoicewallet.core.model.LotteryNumber
import tw.invoicewallet.core.model.formattedNumber

@Composable
fun LotteryRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: LotteryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    LotteryScreen(uiState = uiState, onRefresh = viewModel::refresh, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryScreen(uiState: LotteryUiState, onRefresh: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    WalletTheme {
        Scaffold(
            modifier = modifier,
            containerColor = WalletTheme.colors.surfaceBase,
            topBar = {
                TopAppBar(
                    title = { Text("發票對獎", style = WalletTheme.typography.title) },
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Text(
                                "返回",
                                style = WalletTheme.typography.pillLabel,
                                color = WalletTheme.colors.inkSecondary,
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = onRefresh, modifier = Modifier.testTag("refresh")) {
                            Text(
                                "重新對獎",
                                style = WalletTheme.typography.pillLabel,
                                color = WalletTheme.colors.accentTealDeep,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = WalletTheme.colors.surfaceBase,
                    ),
                )
            },
        ) { padding ->
            if (uiState.isLoading) {
                LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
                return@Scaffold
            }

            // Winners first, then everything else (NoPrize / NotApplicable) in input order.
            val ordered = uiState.results.sortedByDescending { it.result is LotteryResult.Won }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("lottery-content"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = WalletTheme.spacing.lg,
                    vertical = WalletTheme.spacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(WalletTheme.spacing.sm),
            ) {
                item(key = "hero") { Hero(uiState) }
                item(key = "numbers") { WinningNumbers(uiState.latestNumbers) }
                if (ordered.isNotEmpty()) {
                    item(key = "results-label") { SectionLabel(text = "已對發票") }
                    items(ordered, key = { it.invoice.id }) { row -> ResultRow(row) }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.testTag("lottery-loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = WalletTheme.colors.accentTealDeep)
        Spacer(Modifier.height(WalletTheme.spacing.md))
        Text("對獎中…", style = WalletTheme.typography.bodyLg, color = WalletTheme.colors.inkSecondary)
    }
}

@Composable
private fun Hero(uiState: LotteryUiState) {
    val winners = uiState.winners
    val won = winners.isNotEmpty()
    val total = winners.sumOf { (it.result as LotteryResult.Won).prize.amountTwd }
    val pending = uiState.results.count { it.result is LotteryResult.NotApplicable }

    @DrawableRes val illustration = when {
        won -> R.drawable.illust_lottery_won
        pending > 0 -> R.drawable.illust_lottery_pending
        else -> R.drawable.illust_lottery_noprize
    }
    val (headline, sub) = when {
        won -> "中獎 ${winners.size} 張" to "可領獎金 NT$%,d".format(total)
        pending > 0 -> "對獎中" to "等待 $pending 張開獎"
        uiState.results.isEmpty() -> "尚無待對獎發票" to "掃進發票後就會出現在這裡。"
        else -> "這次沒有中獎" to "已對 ${uiState.results.size} 張發票，下一期再來。"
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = WalletTheme.spacing.md).testTag("summary"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WalletTheme.spacing.sm),
    ) {
        Image(
            painter = painterResource(illustration),
            contentDescription = null,
            modifier = Modifier.size(200.dp),
        )
        Text(
            headline,
            style = WalletTheme.typography.displayMd,
            color = if (won) WalletTheme.colors.accentTealDeep else WalletTheme.colors.inkPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            sub,
            style = WalletTheme.typography.bodyLg,
            color = WalletTheme.colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WinningNumbers(numbers: LotteryNumber?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WalletTheme.spacing.md)
            .background(WalletTheme.colors.surfaceElevated, WalletTheme.shapes.card)
            .padding(WalletTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(WalletTheme.spacing.xs),
    ) {
        if (numbers == null) {
            Text(
                "尚無中獎號碼（請連網後重新對獎）",
                style = WalletTheme.typography.bodyMd,
                color = WalletTheme.colors.inkSecondary,
            )
            return@Column
        }
        SectionLabel(text = "${numbers.period} 期中獎號碼")
        Spacer(Modifier.height(WalletTheme.spacing.xs))
        NumberRow(label = "特別獎", value = numbers.specialPrize)
        NumberRow(label = "特獎", value = numbers.grandPrize)
        NumberRow(label = "頭獎", value = numbers.firstPrize.joinToString("、"))
        if (numbers.additionalSixth.isNotEmpty()) {
            NumberRow(label = "增開六獎", value = numbers.additionalSixth.joinToString("、"))
        }
    }
}

@Composable
private fun NumberRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = WalletTheme.typography.bodyMd, color = WalletTheme.colors.inkSecondary)
        Text(value, style = WalletTheme.typography.mono, color = WalletTheme.colors.inkPrimary)
    }
}

@Composable
private fun ResultRow(row: InvoiceLotteryResult) {
    val won = row.result is LotteryResult.Won
    val bg = if (won) WalletTheme.colors.accentTeal.copy(alpha = 0.16f) else WalletTheme.colors.surfaceElevated

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, WalletTheme.shapes.card)
            .padding(horizontal = WalletTheme.spacing.md, vertical = WalletTheme.spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WalletTheme.spacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    row.invoice.merchantName.ifBlank { "(未填商店)" },
                    style = WalletTheme.typography.title,
                    color = WalletTheme.colors.inkPrimary,
                )
                Text(
                    row.invoice.formattedNumber(),
                    style = WalletTheme.typography.caption,
                    color = WalletTheme.colors.inkTertiary,
                )
            }
            when (val r = row.result) {
                is LotteryResult.Won -> Column(horizontalAlignment = Alignment.End) {
                    CompactPill(text = r.prize.displayName(), tone = CompactPillTone.Teal)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "NT$%,d".format(r.prize.amountTwd),
                        style = WalletTheme.typography.displaySm,
                        color = WalletTheme.colors.accentTealDeep,
                    )
                }
                LotteryResult.NoPrize -> Text(
                    "未中獎",
                    style = WalletTheme.typography.caption,
                    color = WalletTheme.colors.inkTertiary,
                )
                LotteryResult.NotApplicable -> CompactPill(text = "待開獎", tone = CompactPillTone.Coral)
            }
        }
    }
}

private fun LotteryPrize.displayName(): String = when (this) {
    LotteryPrize.SPECIAL -> "特別獎"
    LotteryPrize.GRAND -> "特獎"
    LotteryPrize.FIRST -> "頭獎"
    LotteryPrize.SECOND -> "二獎"
    LotteryPrize.THIRD -> "三獎"
    LotteryPrize.FOURTH -> "四獎"
    LotteryPrize.FIFTH -> "五獎"
    LotteryPrize.SIXTH -> "六獎"
}
