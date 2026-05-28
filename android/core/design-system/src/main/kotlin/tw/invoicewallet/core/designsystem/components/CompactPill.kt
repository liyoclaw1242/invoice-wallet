package tw.invoicewallet.core.designsystem.components

import androidx.compose.foundation.background
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

/** Tone of a small inline pill — picks the bg / ink combo for the [CompactPill]. */
enum class CompactPillTone { Neutral, Teal, Coral }

/**
 * Small inline pill — the "Day 1 of your learning streak" / phonetic-tag / "已掃 N 張"
 * pattern. Sits in a heading row or above content; never the primary tap target.
 * Optional [leading] for an icon glyph (e.g. flame, speaker).
 */
@Composable
fun CompactPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: CompactPillTone = CompactPillTone.Neutral,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val (bg, ink) = when (tone) {
        CompactPillTone.Neutral -> WalletTheme.colors.surfaceTinted to WalletTheme.colors.inkSecondary
        CompactPillTone.Teal -> WalletTheme.colors.accentTeal.copy(alpha = 0.22f) to WalletTheme.colors.inkPrimary
        CompactPillTone.Coral -> WalletTheme.colors.accentCoral.copy(alpha = 0.22f) to WalletTheme.colors.inkPrimary
    }
    val shape = WalletTheme.shapes.pill
    Row(
        modifier = modifier
            .heightIn(min = 28.dp)
            .clip(shape)
            .background(bg, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(text, style = WalletTheme.typography.caption, color = ink as Color)
        trailing?.invoke()
    }
}
