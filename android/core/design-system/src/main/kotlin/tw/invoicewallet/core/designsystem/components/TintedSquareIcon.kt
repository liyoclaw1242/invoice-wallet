package tw.invoicewallet.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tw.invoicewallet.core.designsystem.theme.WalletTheme

/** Tint family for the [TintedSquareIcon] background. */
enum class IconTint { Teal, Coral, Neutral }

/**
 * Small rounded square that holds a line-icon — the Vocabulary settings-row pattern
 * (left-side coloured square + line icon). The icon itself comes from [content] so the
 * caller picks a Material symbol or a custom drawable; this composable only owns the
 * tinted backdrop.
 */
@Composable
fun TintedSquareIcon(tint: IconTint = IconTint.Teal, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val bg: Color = when (tint) {
        IconTint.Teal -> WalletTheme.colors.accentTeal.copy(alpha = 0.22f)
        IconTint.Coral -> WalletTheme.colors.accentCoral.copy(alpha = 0.22f)
        IconTint.Neutral -> WalletTheme.colors.surfaceTinted
    }
    Box(
        modifier = modifier
            .size(32.dp)
            .background(bg, WalletTheme.shapes.squareIcon),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
