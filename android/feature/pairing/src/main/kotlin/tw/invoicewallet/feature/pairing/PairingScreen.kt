package tw.invoicewallet.feature.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PairingRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: PairingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    PairingScreen(
        uiState = uiState,
        onPair = { url, code -> viewModel.pair(url, code) },
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    uiState: PairingUiState,
    onPair: (relayUrl: String, code: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("配對 Relay") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        var relayUrl by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "輸入 relay 啟動時顯示的網址與配對碼（或掃描其 QR）。配對只需一次。",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = relayUrl,
                onValueChange = { relayUrl = it },
                label = { Text("Relay 網址（如 https://xxx.trycloudflare.com）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("relay-url"),
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("配對碼（如 A3F9-K2P7）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("pairing-code"),
            )
            Button(
                onClick = { onPair(relayUrl.trim(), code.trim()) },
                enabled = uiState.status != PairingStatus.PAIRING,
                modifier = Modifier.testTag("pair-button"),
            ) {
                Text(if (uiState.status == PairingStatus.PAIRING) "配對中…" else "配對")
            }
            if (uiState.message.isNotBlank()) {
                Text(
                    uiState.message,
                    color = if (uiState.status == PairingStatus.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.testTag("pair-status"),
                )
            }
        }
    }
}
