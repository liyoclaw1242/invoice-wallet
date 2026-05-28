package tw.invoicewallet.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic palette for the wallet — warm paper-stock background, dusty sage-teal accent,
 * coral kept for illustrations and tiny highlights.
 *
 * Pulled apart from Material's [androidx.compose.material3.ColorScheme] because the
 * editorial look needs more than one cream "surface" tone and doesn't map cleanly to
 * Material's primary / secondary / tertiary roles.
 */
@Immutable
data class WalletColors(
    val surfaceBase: Color,
    val surfaceElevated: Color,
    val surfaceTinted: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val accentTeal: Color,
    val accentTealDeep: Color,
    val accentCoral: Color,
    val divider: Color,
    val win: Color,
    val warn: Color,
)

/** The canonical light palette — what every screen renders against. */
val WalletLightColors = WalletColors(
    surfaceBase = Color(0xFFEFEAE0),
    surfaceElevated = Color(0xFFF7F2E8),
    surfaceTinted = Color(0xFFE5DFD3),
    inkPrimary = Color(0xFF2D2926),
    inkSecondary = Color(0xFF7A7268),
    inkTertiary = Color(0xFFB5AC9F),
    accentTeal = Color(0xFF8FB5B2),
    accentTealDeep = Color(0xFF5A7A78),
    accentCoral = Color(0xFFE89B85),
    divider = Color(0x0A000000),
    win = Color(0xFF8FB5B2), // the wallet treats "winning" as the primary positive state
    warn = Color(0xFFC4994C),
)

/** Held by [WalletTheme] so any composable can `WalletTheme.colors.accentTeal`. */
internal val LocalWalletColors = compositionLocalOf<WalletColors> {
    error("WalletColors not provided — wrap content in WalletTheme { … }.")
}

object WalletTheme {
    val colors: WalletColors
        @Composable
        @ReadOnlyComposable
        get() = LocalWalletColors.current

    val typography: WalletTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalWalletTypography.current

    val shapes: WalletShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalWalletShapes.current

    val spacing: WalletSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalWalletSpacing.current
}
