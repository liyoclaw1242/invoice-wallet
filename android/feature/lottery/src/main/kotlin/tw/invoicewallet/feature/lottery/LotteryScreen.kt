package tw.invoicewallet.feature.lottery

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import tw.invoicewallet.core.model.LotteryNumber
import tw.invoicewallet.core.model.formattedNumber

private val WinGreen = Color(0xFF1B5E20)
private val WinGreenContainer = Color(0xFFC8E6C9)

@Composable
fun LotteryRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: LotteryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    LotteryScreen(uiState = uiState, onRefresh = viewModel::refresh, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryScreen(uiState: LotteryUiState, onRefresh: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("發票對獎") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = { TextButton(onClick = onRefresh, modifier = Modifier.testTag("refresh")) { Text("重新對獎") } },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("lottery-loading"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text("對獎中…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            }
            return@Scaffold
        }
        // Winners first, then the rest in their original (newest-first) order.
        val ordered = uiState.results.sortedByDescending { it.result is LotteryResult.Won }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).testTag("lottery-content"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SummaryCard(uiState) }
            item { WinningNumbers(uiState.latestNumbers) }
            items(ordered, key = { it.invoice.id }) { row -> ResultCard(row) }
        }
    }
}

@Composable
private fun SummaryCard(uiState: LotteryUiState) {
    val winners = uiState.winners
    val won = winners.isNotEmpty()
    val total = winners.sumOf { (it.result as LotteryResult.Won).prize.amountTwd }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("summary"),
        colors = CardDefaults.cardColors(
            containerColor = if (won) WinGreen else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (won) {
                Text(
                    "🎉 中獎 ${winners.size} 張",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text("可領獎金 NT$%,d".format(total), style = MaterialTheme.typography.titleMedium, color = Color.White)
            } else {
                Text("這次沒有中獎", style = MaterialTheme.typography.titleMedium)
                Text("已對 ${uiState.results.size} 張發票", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun WinningNumbers(numbers: LotteryNumber?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (numbers == null) {
                Text("尚無中獎號碼（請連網後重新對獎）", style = MaterialTheme.typography.bodyMedium)
                return@Card
            }
            Text(
                "本期中獎號碼（${numbers.period}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text("特別獎 ${numbers.specialPrize}")
            Text("特獎 ${numbers.grandPrize}")
            Text("頭獎 ${numbers.firstPrize.joinToString("、")}")
            if (numbers.additionalSixth.isNotEmpty()) {
                Text("增開六獎 ${numbers.additionalSixth.joinToString("、")}")
            }
        }
    }
}

@Composable
private fun ResultCard(row: InvoiceLotteryResult) {
    val won = row.result is LotteryResult.Won
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (won) WinGreenContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    row.invoice.merchantName.ifBlank { "（未填商店）" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    row.invoice.formattedNumber(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (val r = row.result) {
                is LotteryResult.Won -> Column(horizontalAlignment = Alignment.End) {
                    Text(r.prize.displayName(), style = MaterialTheme.typography.labelMedium, color = WinGreen)
                    Text(
                        "NT$%,d".format(r.prize.amountTwd),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = WinGreen,
                    )
                }
                LotteryResult.NoPrize -> Text(
                    "未中獎",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LotteryResult.NotApplicable -> Text(
                    "待開獎",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
