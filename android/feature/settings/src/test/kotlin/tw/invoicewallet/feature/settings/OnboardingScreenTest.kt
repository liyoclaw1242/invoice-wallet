package tw.invoicewallet.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tw.invoicewallet.feature.settings.onboarding.OnboardingScreen
import tw.invoicewallet.feature.settings.onboarding.onboardingPages

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Pixel-class qualifiers so the bottom CTA stays inside the viewport — Robolectric's
// default 320×480 clips it, making performClick silently miss.
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun opens_on_the_local_first_page() {
        composeRule.setContent { OnboardingScreen(onFinish = {}) }

        composeRule.onNodeWithText(onboardingPages.first().title).assertIsDisplayed()
    }

    @Test
    fun finishes_after_stepping_through_every_page() {
        var finished = false
        composeRule.setContent { OnboardingScreen(onFinish = { finished = true }) }

        // First three pages show 「下一步」; the last shows 「開始使用」.
        repeat(onboardingPages.size - 1) {
            composeRule.onNodeWithText("下一步").performClick()
        }
        composeRule.onNodeWithText("開始使用").performClick()

        finished shouldBe true
    }

    @Test
    fun skip_button_finishes_immediately_from_any_intermediate_page() {
        var finished = false
        composeRule.setContent { OnboardingScreen(onFinish = { finished = true }) }
        composeRule.onNodeWithText("下一步").performClick() // → page 2 of 4

        composeRule.onNodeWithText("略過").performClick()

        finished shouldBe true
    }

    @Test
    fun skip_is_not_offered_on_the_final_page() {
        composeRule.setContent { OnboardingScreen(onFinish = {}) }

        // Step to the last page; the final button is 「開始使用」, no 「略過」 beside it.
        repeat(onboardingPages.size - 1) {
            composeRule.onNodeWithText("下一步").performClick()
        }
        composeRule.onNodeWithText("開始使用").assertIsDisplayed()
        composeRule.onNodeWithText("略過").assertDoesNotExist()
    }
}
