package tw.invoicewallet.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Wraps content in the Vocabulary-style wallet theme.
 *
 * Provides our own [WalletColors] / [WalletTypography] / [WalletShapes] / [WalletSpacing]
 * via composition locals, **and** bridges into Material 3's [MaterialTheme] so the
 * occasional M3 component (Switch, AlertDialog, Snackbar) we keep using inherits the
 * right paper/teal palette without us having to restyle each one. New screens should
 * reach for `WalletTheme.colors.…` over `MaterialTheme.colorScheme.…`.
 */
@Composable
fun WalletTheme(
    colors: WalletColors = WalletLightColors,
    typography: WalletTypography = WalletDefaultTypography,
    shapes: WalletShapes = WalletDefaultShapes,
    spacing: WalletSpacing = WalletDefaultSpacing,
    content: @Composable () -> Unit,
) {
    val m3Colors = lightColorScheme(
        primary = colors.accentTeal,
        onPrimary = colors.inkPrimary,
        secondary = colors.accentTealDeep,
        onSecondary = colors.surfaceElevated,
        tertiary = colors.accentCoral,
        background = colors.surfaceBase,
        onBackground = colors.inkPrimary,
        surface = colors.surfaceElevated,
        onSurface = colors.inkPrimary,
        surfaceVariant = colors.surfaceTinted,
        onSurfaceVariant = colors.inkSecondary,
        outline = colors.inkTertiary,
        outlineVariant = colors.divider,
    )
    // We don't fully map our typography into M3's because we want consumers to use
    // WalletTheme.typography.* directly; this minimal M3 typography just keeps any
    // unstyled M3 Text from looking jarring.
    val m3Typography = Typography(
        bodyLarge = typography.bodyLg,
        bodyMedium = typography.bodyMd,
        bodySmall = typography.caption,
        titleLarge = typography.title,
        labelLarge = typography.pillLabel,
    )

    CompositionLocalProvider(
        LocalWalletColors provides colors,
        LocalWalletTypography provides typography,
        LocalWalletShapes provides shapes,
        LocalWalletSpacing provides spacing,
    ) {
        MaterialTheme(
            colorScheme = m3Colors,
            typography = m3Typography,
            content = content,
        )
    }
}
