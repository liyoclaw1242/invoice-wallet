package tw.invoicewallet.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 8-pt grid. `screenH` is the standard horizontal screen padding (24 — the editorial
 * look needs more air than Material's default 16). `sectionGap` is between major blocks
 * on a screen, so layout doesn't have to invent its own spacing each time.
 */
@Immutable
data class WalletSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val xxxl: Dp = 64.dp,
    val screenH: Dp = 24.dp,
    val sectionGap: Dp = 32.dp,
)

val WalletDefaultSpacing = WalletSpacing()

internal val LocalWalletSpacing = compositionLocalOf<WalletSpacing> {
    error("WalletSpacing not provided — wrap content in WalletTheme { … }.")
}
