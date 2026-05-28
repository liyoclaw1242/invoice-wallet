package tw.invoicewallet.feature.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import tw.invoicewallet.core.designsystem.components.CompactPill
import tw.invoicewallet.core.designsystem.components.CompactPillTone
import tw.invoicewallet.core.designsystem.components.PillButton
import tw.invoicewallet.core.designsystem.components.QuietPill
import tw.invoicewallet.core.designsystem.theme.WalletTheme
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.formattedNumber
import tw.invoicewallet.feature.scan.ScanState

/**
 * Stateless scan screen. Camera-first: the live preview fills most of the area, with a
 * minimal counter pill at the top. Gallery picks divert into a focused "confirm" sheet
 * (single-image flow); the [ScanRoute] in :app overlays a snackbar host on top of this
 * so banner events for the camera path render above the camera.
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
    WalletTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(WalletTheme.colors.surfaceBase),
        ) {
            when (state) {
                ScanState.Idle -> IdleContent(savedCount, onPickImage, cameraContent)
                ScanState.Recognizing -> RecognizingContent()
                is ScanState.Detected -> ConfirmContent(state.draft, onConfirm, onCancel)
                is ScanState.Saved -> ResultContent(
                    tag = "scan-saved",
                    title = "已儲存",
                    detail = "發票已存入加密錢包。",
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
}

@Composable
private fun IdleContent(savedCount: Int, onPickImage: () -> Unit, cameraContent: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = WalletTheme.spacing.lg, vertical = WalletTheme.spacing.md)
            .testTag("scan-idle"),
        verticalArrangement = Arrangement.spacedBy(WalletTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Counter pill — quietly tracks the batch without dominating the screen.
        if (savedCount > 0) {
            CompactPill(
                text = "本次已存 $savedCount 張",
                tone = CompactPillTone.Teal,
                modifier = Modifier.testTag("saved-count"),
            )
        } else {
            CompactPill(text = "對準發票上的 QR code", tone = CompactPillTone.Neutral)
        }

        cameraContent()

        // Gallery is the secondary path — quieter affordance.
        QuietPill(
            text = "改從相簿選擇照片",
            onClick = onPickImage,
            modifier = Modifier.testTag("pick-image-button"),
        )
    }
}

@Composable
private fun RecognizingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WalletTheme.spacing.lg)
            .testTag("scan-recognizing"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = WalletTheme.colors.accentTealDeep)
        Spacer(Modifier.height(WalletTheme.spacing.md))
        Text(
            "辨識中…",
            style = WalletTheme.typography.bodyLg,
            color = WalletTheme.colors.inkSecondary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmContent(draft: Invoice, onConfirm: (Invoice) -> Unit, onCancel: () -> Unit) {
    var merchantName by remember(draft.id) { mutableStateOf(draft.merchantName) }
    var totalText by remember(draft.id) { mutableStateOf(draft.totalAmount.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WalletTheme.spacing.lg, vertical = WalletTheme.spacing.md)
            .testTag("scan-detected"),
        verticalArrangement = Arrangement.spacedBy(WalletTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "確認發票資料",
            style = WalletTheme.typography.displaySm,
            color = WalletTheme.colors.inkPrimary,
        )

        ScanField(
            value = merchantName,
            onValueChange = { merchantName = it },
            placeholder = "商店名稱",
            testTag = "field-merchant",
        )
        ScanField(
            value = totalText,
            onValueChange = { totalText = it.filter(Char::isDigit) },
            placeholder = "總金額 (NT$)",
            testTag = "field-total",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ReadOnlyRow("發票號碼", draft.formattedNumber())
        ReadOnlyRow("開立日期", draft.issueDate.toString())
        ReadOnlyRow("賣方統編", draft.merchantTaxId ?: "—")

        Spacer(Modifier.height(WalletTheme.spacing.md))
        PillButton(
            text = "確認儲存",
            onClick = {
                onConfirm(
                    draft.copy(
                        merchantName = merchantName,
                        totalAmount = totalText.toIntOrNull() ?: draft.totalAmount,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(0.7f).testTag("confirm-button"),
        )
        QuietPill(
            text = "取消",
            onClick = onCancel,
            modifier = Modifier.testTag("cancel-button"),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = WalletTheme.typography.bodyMd) },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        shape = WalletTheme.shapes.card,
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
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = WalletTheme.typography.microCaps,
            color = WalletTheme.colors.inkTertiary,
        )
        Text(
            value,
            style = WalletTheme.typography.bodyLg,
            color = WalletTheme.colors.inkPrimary,
        )
    }
}

@Composable
private fun ResultContent(tag: String, title: String, detail: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WalletTheme.spacing.lg)
            .testTag(tag),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = WalletTheme.typography.displaySm,
            color = WalletTheme.colors.inkPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(WalletTheme.spacing.sm))
        Text(
            detail,
            style = WalletTheme.typography.bodyLg,
            color = WalletTheme.colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(WalletTheme.spacing.lg))
        PillButton(
            text = actionLabel,
            onClick = onAction,
            modifier = Modifier.testTag("result-action"),
        )
    }
}
