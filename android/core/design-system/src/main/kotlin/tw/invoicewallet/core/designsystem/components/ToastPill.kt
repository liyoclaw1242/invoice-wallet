package tw.invoicewallet.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tw.invoicewallet.core.designsystem.theme.WalletTheme

/**
 * Floating confirmation pill — the Vocabulary "Saved!" / "Text copied!" pattern.
 *
 * Auto-positioning, fade-in/out, and dismiss timing are the **caller's** job; this
 * composable just renders the pill. Typically placed near the top of the screen via a
 * Box or AnimatedVisibility, so it floats above content rather than pushing layout.
 */
@Composable
fun ToastPill(text: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Surface(
        modifier = modifier,
        shape = WalletTheme.shapes.pill,
        color = WalletTheme.colors.surfaceElevated,
        contentColor = WalletTheme.colors.inkPrimary,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .background(Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke()
            Text(text, style = WalletTheme.typography.pillLabel)
        }
    }
}
