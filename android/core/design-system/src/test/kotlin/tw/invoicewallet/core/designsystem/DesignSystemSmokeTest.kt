package tw.invoicewallet.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tw.invoicewallet.core.designsystem.components.CompactPill
import tw.invoicewallet.core.designsystem.components.CompactPillTone
import tw.invoicewallet.core.designsystem.components.PillButton
import tw.invoicewallet.core.designsystem.components.PillChip
import tw.invoicewallet.core.designsystem.components.SectionLabel
import tw.invoicewallet.core.designsystem.components.ToastPill
import tw.invoicewallet.core.designsystem.theme.WalletTheme

/**
 * Smoke checks that the theme + atoms render without crashing, and that user
 * interactions reach the callback. We don't assert visual properties (colours, shapes)
 * here — those are token concerns, verified by review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DesignSystemSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun all_atoms_render_inside_WalletTheme() {
        composeRule.setContent {
            WalletTheme {
                Column {
                    SectionLabel("Premium", modifier = androidx.compose.ui.Modifier.testTag("section-label"))
                    CompactPill("Day 1", tone = CompactPillTone.Coral)
                    PillChip(label = "Society", selected = false, onClick = {})
                    PillChip(label = "Slang", selected = true, onClick = {})
                    ToastPill("Saved!")
                    PillButton("Continue", onClick = {})
                    Text("body line", style = WalletTheme.typography.bodyLg)
                }
            }
        }

        composeRule.onNodeWithTag("section-label").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
        composeRule.onNodeWithText("Saved!").assertIsDisplayed()
    }

    @Test
    fun pillButton_fires_onClick() {
        var clicks = 0
        composeRule.setContent {
            WalletTheme { PillButton("Tap", onClick = { clicks++ }) }
        }

        composeRule.onNodeWithText("Tap").performClick()

        assert(clicks == 1) { "expected exactly one click, got $clicks" }
    }

    @Test
    fun pillChip_fires_onClick_in_both_states() {
        var clicks = 0
        composeRule.setContent {
            WalletTheme {
                Column {
                    PillChip(label = "Idle", selected = false, onClick = { clicks++ })
                    PillChip(label = "Selected", selected = true, onClick = { clicks++ })
                }
            }
        }

        composeRule.onNodeWithText("Idle").performClick()
        composeRule.onNodeWithText("Selected").performClick()

        assert(clicks == 2) { "expected 2 clicks across both states, got $clicks" }
    }
}
