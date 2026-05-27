package tw.invoicewallet.feature.pairing

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tw.invoicewallet.datasource.relayclient.RelayDeviceStore
import javax.inject.Inject

enum class PairingStatus { IDLE, PAIRING, PAIRED, ERROR }

data class PairingUiState(val status: PairingStatus = PairingStatus.IDLE, val message: String = "")

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val client: PairingClient,
    private val deviceStore: RelayDeviceStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    /** Handles scanned QR text: parse then pair. */
    fun onScanned(qrText: String) {
        val payload = PairingPayload.parse(qrText)
        if (payload == null) {
            _uiState.update { it.copy(status = PairingStatus.ERROR, message = "QR 內容無法解析") }
            return
        }
        pair(payload.relayUrl, payload.pairingCode)
    }

    fun pair(relayUrl: String, pairingCode: String, fcmToken: String = "") {
        if (relayUrl.isBlank() || pairingCode.isBlank()) {
            _uiState.update { it.copy(status = PairingStatus.ERROR, message = "請輸入 relay 網址與配對碼") }
            return
        }
        // Force HTTPS — the relay is always behind TLS and Android blocks cleartext.
        val url = normalizeRelayUrl(relayUrl)
        _uiState.update { it.copy(status = PairingStatus.PAIRING, message = "") }
        viewModelScope.launch {
            when (val result = client.claim(url, pairingCode, fcmToken, Build.MODEL ?: "Android")) {
                is PairingResult.Success -> {
                    deviceStore.save(url, result.deviceSecret)
                    _uiState.update { it.copy(status = PairingStatus.PAIRED, message = "配對成功") }
                }
                is PairingResult.Failure ->
                    _uiState.update { it.copy(status = PairingStatus.ERROR, message = result.reason) }
            }
        }
    }
}
