package tw.invoicewallet.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Editorial-style type scale.
 *
 * Display tiers use a **serif** (the Vocabulary look hangs on this) and body tiers use a
 * **sans**. System fallbacks for now — we'll swap in Source Serif Pro + Inter via the
 * downloadable-font provider in a follow-up commit; everything else (sizes, weights,
 * line-heights, letter-spacing) is the part that defines the rhythm and should not move.
 */
@Immutable
data class WalletTypography(
    val displayLg: TextStyle,
    val displayMd: TextStyle,
    val displaySm: TextStyle,
    val title: TextStyle,
    val titleSmall: TextStyle,
    val microCaps: TextStyle,
    val bodyLg: TextStyle,
    val bodyMd: TextStyle,
    val caption: TextStyle,
    val mono: TextStyle,
    val pillLabel: TextStyle,
)

private val Serif = FontFamily.Serif
private val Sans = FontFamily.Default
private val Mono = FontFamily.Monospace

val WalletDefaultTypography = WalletTypography(
    displayLg = TextStyle(fontFamily = Serif, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium),
    displayMd = TextStyle(fontFamily = Serif, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
    displaySm = TextStyle(fontFamily = Serif, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
    title = TextStyle(fontFamily = Sans, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    // Small-caps section labels ("PREMIUM" / "ABOUT YOU") — letter-spacing is what sells the look.
    microCaps = TextStyle(
        fontFamily = Sans,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.1.sp,
    ),
    bodyLg = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    bodyMd = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    caption = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    mono = TextStyle(fontFamily = Mono, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    pillLabel = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

internal val LocalWalletTypography = compositionLocalOf<WalletTypography> {
    error("WalletTypography not provided — wrap content in WalletTheme { … }.")
}
