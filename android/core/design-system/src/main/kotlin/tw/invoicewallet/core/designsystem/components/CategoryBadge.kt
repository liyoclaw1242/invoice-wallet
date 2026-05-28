package tw.invoicewallet.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tw.invoicewallet.core.designsystem.theme.WalletTheme

/**
 * A circular tinted backdrop holding the [slug]'s `ic_category_*` illustration. Sized as
 * a "stamp" — meant to sit prominently above an invoice's hero text. Returns nothing
 * (composes empty) when the slug doesn't resolve to a known category, so callers can
 * sprinkle it freely without null-checking themselves.
 */
@Composable
fun CategoryBadge(slug: String?, modifier: Modifier = Modifier, size: Dp = 84.dp) {
    val iconRes = slug?.let { CategoryGuesser.iconRes(it) } ?: return
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(WalletTheme.colors.surfaceTinted),
        contentAlignment = Alignment.Center,
    ) {
        // Illustration sits at ~70 % of the backdrop so it doesn't crowd the edge.
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(size * 0.78f),
        )
    }
}
