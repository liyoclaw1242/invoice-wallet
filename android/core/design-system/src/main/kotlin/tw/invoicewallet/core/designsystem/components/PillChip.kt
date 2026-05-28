package tw.invoicewallet.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tw.invoicewallet.core.designsystem.theme.WalletTheme

/**
 * Selectable pill chip — the "+ Society" / "✓ Society" pattern from Vocabulary's
 * category picker. Toggles between an outlined idle state (with a leading "+") and a
 * tinted selected state (with a leading "✓").
 */
@Composable
fun PillChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = WalletTheme.shapes.pill
    val style = if (selected) {
        ChipStyle(
            border = BorderStroke(1.dp, WalletTheme.colors.accentTeal),
            bg = WalletTheme.colors.accentTeal.copy(alpha = 0.18f),
            ink = WalletTheme.colors.inkPrimary,
            prefix = "✓",
        )
    } else {
        ChipStyle(
            border = BorderStroke(1.dp, WalletTheme.colors.inkTertiary),
            bg = WalletTheme.colors.surfaceElevated,
            ink = WalletTheme.colors.inkSecondary,
            prefix = "+",
        )
    }
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(style.bg, shape)
            .border(style.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(style.prefix, style = WalletTheme.typography.pillLabel, color = style.ink)
        Text(label, style = WalletTheme.typography.pillLabel, color = style.ink)
    }
}

private data class ChipStyle(val border: BorderStroke, val bg: Color, val ink: Color, val prefix: String)
