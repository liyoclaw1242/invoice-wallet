package tw.invoicewallet.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Radius scale. The pill is the dominant shape language — buttons, chips, toasts.
 * Square-icon is for the small tinted icon container in settings rows.
 */
@Immutable
data class WalletShapes(val pill: Shape, val card: Shape, val squareIcon: Shape, val sheet: Shape)

val WalletDefaultShapes = WalletShapes(
    pill = RoundedCornerShape(percent = 50),
    card = RoundedCornerShape(20.dp),
    squareIcon = RoundedCornerShape(10.dp),
    sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
)

internal val LocalWalletShapes = compositionLocalOf<WalletShapes> {
    error("WalletShapes not provided — wrap content in WalletTheme { … }.")
}
