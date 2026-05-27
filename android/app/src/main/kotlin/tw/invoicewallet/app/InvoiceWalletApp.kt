package tw.invoicewallet.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import tw.invoicewallet.feature.scan.ScanViewModel
import tw.invoicewallet.feature.scan.ui.ScanScreen

@Composable
fun InvoiceWalletApp() {
    InvoiceWalletScaffold { padding ->
        ScanRoute(modifier = Modifier.padding(padding))
    }
}

/** App chrome (title bar). Stateless so it can be rendered in tests without Hilt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceWalletScaffold(content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Invoice Wallet") }) },
    ) { padding ->
        content(padding)
    }
}

@Composable
private fun ScanRoute(modifier: Modifier = Modifier) {
    val viewModel: ScanViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    ScanScreen(
        state = state,
        onParse = { viewModel.onQrDetected(it, rightBytes = null) },
        onConfirm = viewModel::onUserConfirm,
        onCancel = viewModel::onCancel,
        modifier = modifier,
    )
}
