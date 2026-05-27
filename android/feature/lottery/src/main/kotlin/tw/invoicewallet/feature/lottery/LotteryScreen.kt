package tw.invoicewallet.feature.lottery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
            CircularProgressIndicator(Modifier.padding(padding).padding(24.dp).testTag("lottery-loading"))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).testTag("lottery-content"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { WinningNumbers(uiState.latestNumbers) }
            item {
                val summary = if (uiState.winners.isEmpty()) {
                    "本期未中獎"
                } else {
                    "恭喜！${uiState.winners.size} 張中獎"
                }
                Text(summary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("summary"))
            }
            items(uiState.results, key = { it.invoice.id }) { row ->
                ResultCard(row)
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
            Text("本期中獎號碼（${numbers.period}）", style = MaterialTheme.typography.titleMedium)
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.invoice.merchantName.ifBlank { "（未填商店）" }, style = MaterialTheme.typography.titleSmall)
            Text(row.invoice.formattedNumber(), style = MaterialTheme.typography.bodySmall)
            Text(row.result.label(), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun LotteryResult.label(): String = when (this) {
    is LotteryResult.Won -> "${prize.displayName()}　NT$${prize.amountTwd}"
    LotteryResult.NoPrize -> "未中獎"
    LotteryResult.NotApplicable -> "非當期或尚無號碼"
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
