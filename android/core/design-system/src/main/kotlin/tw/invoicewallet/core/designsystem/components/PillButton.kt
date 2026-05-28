package tw.invoicewallet.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tw.invoicewallet.core.designsystem.theme.WalletTheme

/**
 * Wide pill-shaped primary button — sage-teal fill, ink-primary label.
 * The Vocabulary "Continue" / "Save" CTA.
 */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = WalletTheme.shapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = WalletTheme.colors.accentTeal,
            contentColor = WalletTheme.colors.inkPrimary,
            disabledContainerColor = WalletTheme.colors.surfaceTinted,
            disabledContentColor = WalletTheme.colors.inkTertiary,
        ),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
    ) {
        Text(text, style = WalletTheme.typography.pillLabel)
    }
}

/** Outlined pill — quieter secondary action. */
@Composable
fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = WalletTheme.shapes.pill,
        border = BorderStroke(1.dp, WalletTheme.colors.inkTertiary),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
    ) {
        Text(text, style = WalletTheme.typography.pillLabel, color = WalletTheme.colors.inkPrimary)
    }
}

/** Naked text pill — tertiary action ("Skip", "Not now"). */
@Composable
fun QuietPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = WalletTheme.shapes.pill,
    ) {
        Text(text, style = WalletTheme.typography.pillLabel, color = WalletTheme.colors.inkSecondary)
    }
}
