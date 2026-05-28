package tw.invoicewallet.feature.scan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.formattedNumber
import tw.invoicewallet.feature.scan.ScanState

/**
 * Stateless scan screen. Renders the [ScanState] and hoists all actions to the caller.
 * The live camera preview is supplied by the app via [cameraContent]; QR codes are
 * detected in real time, so the user normally just points the camera and confirms.
 */
@Composable
fun ScanScreen(
    state: ScanState,
    onPickImage: () -> Unit,
    onConfirm: (Invoice) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    savedCount: Int = 0,
    cameraContent: @Composable () -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            ScanState.Idle -> IdleContent(savedCount, onPickImage, cameraContent)
            ScanState.Recognizing -> RecognizingContent()
            is ScanState.Detected -> ConfirmContent(state.draft, onConfirm, onCancel)
            is ScanState.Saved -> ResultContent(
                tag = "scan-saved",
                title = "✅ 已儲存",
                detail = "發票已存入加密錢包",
                actionLabel = "再掃一張",
                onAction = onCancel,
            )

            is ScanState.Error -> ResultContent(
                tag = "scan-error",
                title = "無法辨識",
                detail = state.message,
                actionLabel = "重試",
                onAction = onCancel,
            )
        }
    }
}

@Composable
private fun IdleContent(savedCount: Int, onPickImage: () -> Unit, cameraContent: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()).testTag("scan-idle"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Headline adapts to the session: first scan is a how-to, subsequent ones cheer the count.
        if (savedCount == 0) {
            Text("把鏡頭對準發票的 QR code", style = MaterialTheme.typography.titleMedium)
            Text(
                "辨識後會自動存入錢包，可一張接一張掃，不用每張按確認。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "本次已存 $savedCount 張，繼續掃下一張。",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("saved-count"),
            )
        }
        cameraContent()
        OutlinedButton(
            onClick = onPickImage,
            modifier = Modifier.fillMaxWidth().testTag("pick-image-button"),
        ) {
            Text("改從相簿選擇照片")
        }
    }
}

@Composable
private fun RecognizingContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag("scan-recognizing"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("辨識中…", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ConfirmContent(draft: Invoice, onConfirm: (Invoice) -> Unit, onCancel: () -> Unit) {
    var merchantName by remember(draft.id) { mutableStateOf(draft.merchantName) }
    var totalText by remember(draft.id) { mutableStateOf(draft.totalAmount.toString()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())
            .testTag("scan-detected"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("確認發票資料", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = merchantName,
            onValueChange = { merchantName = it },
            label = { Text("商店名稱") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("field-merchant"),
        )
        OutlinedTextField(
            value = totalText,
            onValueChange = { totalText = it.filter(Char::isDigit) },
            label = { Text("總金額 (NT$)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("field-total"),
        )
        ReadOnlyField("發票號碼", draft.formattedNumber())
        ReadOnlyField("開立日期", draft.issueDate.toString())
        ReadOnlyField("賣方統編", draft.merchantTaxId ?: "—")
        Button(
            onClick = {
                onConfirm(
                    draft.copy(
                        merchantName = merchantName,
                        totalAmount = totalText.toIntOrNull() ?: draft.totalAmount,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth().testTag("confirm-button"),
        ) {
            Text("確認儲存")
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().testTag("cancel-button"),
        ) {
            Text("取消")
        }
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ResultContent(tag: String, title: String, detail: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth().testTag("result-action")) {
            Text(actionLabel)
        }
    }
}
