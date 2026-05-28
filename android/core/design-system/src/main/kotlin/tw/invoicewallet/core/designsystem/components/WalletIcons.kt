package tw.invoicewallet.core.designsystem.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Hand-rolled line icons used by the wallet's bottom nav. Inlined as vector paths so we
 * stay free of the 6 MB `material-icons-extended` dependency. Stroke 1.6 (vector-space
 * units, ≈ 1.6 dp at 24 dp render) keeps a consistent weight across the set.
 */

/** Receipt — a folded paper receipt with a zig-zag bottom and two content lines. */
val IconReceipt: ImageVector = lineIcon("receipt") {
    addStrokePath("M6 3 L18 3 L18 19 L16 17.5 L14 19 L12 17.5 L10 19 L8 17.5 L6 19 Z")
    addStrokePath("M8.5 8 L15.5 8")
    addStrokePath("M8.5 12 L13.5 12")
}

/** Ticket — a stub with a centre divider and two inward notches on the long edges. */
val IconTicket: ImageVector = lineIcon("ticket") {
    addStrokePath(
        "M4 8 L20 8 L20 11 A1.5 1.5 0 0 0 20 13 L20 16 L4 16 L4 13 A1.5 1.5 0 0 0 4 11 Z",
    )
    addStrokePath("M14 8 L14 16")
}

/** Cog — settings gear: 6 teeth + a centre circle, kept simple to stay legible at 24 dp. */
val IconCog: ImageVector = lineIcon("cog") {
    addStrokePath(
        "M12 2.5 L13 5 L15.5 4 L16.5 6.5 L19 6 L19 8.7 L21.5 10 L20.5 12.5 " +
            "L21.5 15 L19 16.3 L19 19 L16.5 18.5 L15.5 21 L13 20 L12 22.5 " +
            "L11 20 L8.5 21 L7.5 18.5 L5 19 L5 16.3 L2.5 15 L3.5 12.5 " +
            "L2.5 10 L5 8.7 L5 6 L7.5 6.5 L8.5 4 L11 5 Z",
    )
    addStrokePath(circlePath(cx = 12f, cy = 12.5f, r = 3f))
}

// ---- composable wrapper -----------------------------------------------------------

@Composable
fun WalletIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    Icon(imageVector = icon, contentDescription = null, modifier = modifier, tint = tint)
}

// ---- DSL helpers (file-private) ---------------------------------------------------

private const val VIEWPORT = 24f
private const val STROKE_WIDTH = 1.6f

private fun lineIcon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = VIEWPORT,
    viewportHeight = VIEWPORT,
).apply(build).build()

/** Stroke-only path with the wallet's standard weight + rounded ends. */
private fun ImageVector.Builder.addStrokePath(pathData: String) {
    addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

/** A circle as two SVG arcs — the vector builder API only takes path strings. */
private fun circlePath(cx: Float, cy: Float, r: Float): String =
    "M ${cx - r} $cy A $r $r 0 1 0 ${cx + r} $cy A $r $r 0 1 0 ${cx - r} $cy Z"
