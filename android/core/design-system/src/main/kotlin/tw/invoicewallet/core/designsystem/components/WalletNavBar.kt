package tw.invoicewallet.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import tw.invoicewallet.core.designsystem.theme.WalletTheme

/** One entry in the wallet's bottom nav. */
@Immutable
data class WalletNavItem(val route: String, val label: String, val icon: ImageVector, val testTag: String = route)

/**
 * Bottom navigation bar — sage-teal accent on the selected tab plus a tiny dot beneath
 * it, ink-tertiary on the rest. Sits on [WalletTheme.colors.surfaceElevated] with a
 * hairline divider on top so it reads as a quiet shelf rather than a heavy bar.
 *
 * Honours `navigationBars` window insets so the bar floats above the system gesture
 * area on edge-to-edge layouts.
 */
@Composable
fun WalletNavBar(
    items: List<WalletNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WalletTheme.colors.surfaceElevated),
    ) {
        HorizontalDivider(color = WalletTheme.colors.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEach { item ->
                NavTab(item = item, selected = item.route == selectedRoute, onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun NavTab(item: WalletNavItem, selected: Boolean, onSelect: (String) -> Unit) {
    val tint = if (selected) WalletTheme.colors.accentTealDeep else WalletTheme.colors.inkTertiary
    Column(
        modifier = Modifier
            .clip(WalletTheme.shapes.pill)
            .clickable { onSelect(item.route) }
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag(item.testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        WalletIcon(icon = item.icon, modifier = Modifier.size(22.dp), tint = tint)
        Text(item.label, style = WalletTheme.typography.caption, color = tint)
        // 3-dp dot underline for the active tab — tiny, sage-teal, no other movement.
        Box(
            modifier = Modifier
                .size(if (selected) 4.dp else 0.dp)
                .clip(CircleShape)
                .background(if (selected) WalletTheme.colors.accentTealDeep else WalletTheme.colors.surfaceElevated),
        )
    }
}
