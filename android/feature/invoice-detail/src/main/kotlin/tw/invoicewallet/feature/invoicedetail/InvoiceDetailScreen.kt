package tw.invoicewallet.feature.invoicedetail

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import tw.invoicewallet.core.designsystem.components.CategoryBadge
import tw.invoicewallet.core.designsystem.components.CategoryGuesser
import tw.invoicewallet.core.designsystem.components.CompactPill
import tw.invoicewallet.core.designsystem.components.CompactPillTone
import tw.invoicewallet.core.designsystem.components.PillButton
import tw.invoicewallet.core.designsystem.components.QuietPill
import tw.invoicewallet.core.designsystem.components.SectionLabel
import tw.invoicewallet.core.designsystem.theme.WalletTheme
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceItem
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus
import tw.invoicewallet.core.model.formattedNumber

@Composable
fun InvoiceDetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InvoiceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    InvoiceDetailScreen(
        state = state,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDelete,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    state: InvoiceDetailState,
    onSave: (note: String, tags: List<String>) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Leave the screen as soon as the invoice is deleted.
    LaunchedEffect(state) {
        if (state is InvoiceDetailState.Deleted) onBack()
    }

    WalletTheme {
        Scaffold(
            modifier = modifier,
            containerColor = WalletTheme.colors.surfaceBase,
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Text("✕", style = WalletTheme.typography.title, color = WalletTheme.colors.inkSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = WalletTheme.colors.surfaceBase,
                    ),
                )
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (state) {
                    is InvoiceDetailState.Loaded -> LoadedContent(state.invoice, state.items, onSave, onDelete)
                    InvoiceDetailState.Loading -> CircularProgressIndicator(
                        Modifier.padding(WalletTheme.spacing.lg).align(Alignment.Center).testTag("detail-loading"),
                    )
                    InvoiceDetailState.NotFound -> Text(
                        "找不到這張發票",
                        style = WalletTheme.typography.bodyLg,
                        color = WalletTheme.colors.inkSecondary,
                        modifier = Modifier.padding(WalletTheme.spacing.lg).align(Alignment.Center)
                            .testTag("detail-not-found"),
                    )
                    InvoiceDetailState.Deleted -> Unit
                }
            }
        }
    }
}

@Composable
private fun LoadedContent(
    invoice: Invoice,
    items: List<InvoiceItem>,
    onSave: (String, List<String>) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var note by remember(invoice.id) { mutableStateOf(invoice.userNote.orEmpty()) }
    var tags by remember(invoice.id) { mutableStateOf(invoice.userTags.joinToString(", ")) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WalletTheme.spacing.lg, vertical = WalletTheme.spacing.md)
            .testTag("detail-loaded"),
        verticalArrangement = Arrangement.spacedBy(WalletTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Hero: store name as the "word", merchant tax id as the "phonetic", amount as headline. ──
        Hero(invoice)

        // Date + source + lottery status as a row of small pills, centered.
        MetaPills(invoice)

        Spacer(Modifier.height(WalletTheme.spacing.lg))
        HorizontalDivider(color = WalletTheme.colors.divider)

        // ── Line items ──
        ItemsSection(items)

        HorizontalDivider(color = WalletTheme.colors.divider)

        // ── User-owned editor ──
        EditorSection(
            note = note,
            onNoteChange = { note = it },
            tags = tags,
            onTagsChange = { tags = it },
        )

        Spacer(Modifier.height(WalletTheme.spacing.lg))

        // ── Actions ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WalletTheme.spacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(
                text = "儲存",
                onClick = { onSave(note, tags.split(",").map(String::trim).filter(String::isNotBlank)) },
                modifier = Modifier.testTag("save-button"),
            )
            QuietPill(
                text = "刪除",
                onClick = { confirmingDelete = true },
                modifier = Modifier.testTag("delete-button"),
            )
        }

        Spacer(Modifier.height(WalletTheme.spacing.lg))
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("刪除發票？", style = WalletTheme.typography.title) },
            text = {
                Text(
                    "這張發票會從清單移除(軟刪除,可日後復原)。",
                    style = WalletTheme.typography.bodyMd,
                    color = WalletTheme.colors.inkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("confirm-delete"),
                ) { Text("刪除", color = WalletTheme.colors.accentTealDeep) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text("取消", color = WalletTheme.colors.inkSecondary)
                }
            },
            containerColor = WalletTheme.colors.surfaceElevated,
        )
    }
}

@Composable
private fun Hero(invoice: Invoice) {
    // Explicit invoice.category wins; otherwise heuristic-guess from the merchant name —
    // same rule as the home page's chip filter, so a row labelled 「食」 on home opens to
    // a detail page stamped with the food badge.
    val categorySlug = invoice.category
        ?.takeIf { it.isNotBlank() }
        ?: CategoryGuesser.guess(invoice.merchantName)

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = WalletTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WalletTheme.spacing.sm),
    ) {
        // The badge composes nothing when there's no resolved category — no awkward empty
        // circle on unknown merchants.
        CategoryBadge(slug = categorySlug)
        Text(
            text = invoice.merchantName.ifBlank { "(未填商店)" },
            style = WalletTheme.typography.displayLg,
            color = WalletTheme.colors.inkPrimary,
            textAlign = TextAlign.Center,
        )
        // Phonetic-style pill carries the tax id (or invoice number when no tax id is present).
        val pillText = invoice.merchantTaxId
            ?.let { "統編 $it" }
            ?: invoice.formattedNumber()
        CompactPill(text = pillText, tone = CompactPillTone.Neutral)
        Spacer(Modifier.height(WalletTheme.spacing.xs))
        Text(
            text = money(invoice.totalAmount),
            style = WalletTheme.typography.displayMd,
            color = WalletTheme.colors.inkPrimary,
        )
    }
}

@Composable
private fun MetaPills(invoice: Invoice) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WalletTheme.spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val d = invoice.issueDate
        CompactPill(text = "%d/%02d/%02d".format(d.year, d.monthNumber, d.dayOfMonth))
        CompactPill(text = sourceLabel(invoice.source))
        lotteryPill(invoice.lotteryStatus, invoice.lotteryPrize)?.let { (text, tone) ->
            CompactPill(text = text, tone = tone, modifier = Modifier.testTag("lottery-pill"))
        }
    }
}

private fun sourceLabel(source: InvoiceSource): String = when (source) {
    InvoiceSource.QR_CODE -> "QR 掃描"
    InvoiceSource.OCR -> "照片辨識"
    InvoiceSource.CARRIER_API -> "載具匯入"
    InvoiceSource.MANUAL -> "手動輸入"
}

private fun lotteryPill(status: LotteryStatus, prize: Int?): Pair<String, CompactPillTone>? = when (status) {
    LotteryStatus.PENDING -> "待對獎" to CompactPillTone.Coral
    LotteryStatus.CHECKED_WON -> ("中獎" + (prize?.let { " " + money(it) } ?: "")) to CompactPillTone.Teal
    LotteryStatus.CLAIMED -> "已領獎" to CompactPillTone.Teal
    LotteryStatus.CHECKED_NO_PRIZE -> null // a neutral "no prize" pill adds noise; omit
}

@Composable
private fun ItemsSection(items: List<InvoiceItem>) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WalletTheme.spacing.xs)) {
        SectionLabel(text = "品項明細")
        if (items.isEmpty()) {
            Text(
                text = "此發票沒有品項資料(部分商家的 QR 只帶發票表頭)。",
                style = WalletTheme.typography.caption,
                color = WalletTheme.colors.inkTertiary,
                modifier = Modifier
                    .padding(start = WalletTheme.spacing.md, top = WalletTheme.spacing.xs)
                    .testTag("items-empty"),
            )
            return@Column
        }
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WalletTheme.spacing.xs, vertical = WalletTheme.spacing.xs)
                    .testTag("item-row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WalletTheme.spacing.sm),
            ) {
                Text(
                    text = item.name,
                    style = WalletTheme.typography.bodyLg,
                    color = WalletTheme.colors.inkPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = quantityLabel(item),
                    style = WalletTheme.typography.caption,
                    color = WalletTheme.colors.inkTertiary,
                )
                Text(
                    text = money(item.amount),
                    style = WalletTheme.typography.bodyLg,
                    color = WalletTheme.colors.inkPrimary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSection(note: String, onNoteChange: (String) -> Unit, tags: String, onTagsChange: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WalletTheme.spacing.xs),
    ) {
        SectionLabel(text = "備註與標籤")
        EditorField(
            value = note,
            onValueChange = onNoteChange,
            placeholder = "為這張發票寫點什麼…",
            testTag = "note-field",
            modifier = Modifier.fillMaxWidth(),
        )
        EditorField(
            value = tags,
            onValueChange = onTagsChange,
            placeholder = "標籤(以逗號分隔)",
            testTag = "tags-field",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = WalletTheme.typography.bodyMd) },
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
        modifier = modifier
            .background(WalletTheme.colors.surfaceElevated, WalletTheme.shapes.card)
            .testTag(testTag),
    )
}

/** "× 2" for whole counts, "× 1.5" otherwise; trims the trailing ".0". */
private fun quantityLabel(item: InvoiceItem): String {
    val qty = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
    return "× $qty"
}

private fun money(amount: Int): String = "NT$%,d".format(amount)
