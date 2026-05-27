package tw.invoicewallet.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
@Config(sdk = [34])
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shows_the_privacy_first_message() {
        composeRule.setContent {
            MaterialTheme { OnboardingScreen(onFinish = {}) }
        }
        composeRule.onNodeWithText(onboardingPages.first().title).assertIsDisplayed()
    }

    @Test
    fun finishes_after_stepping_through_every_page() {
        var finished = false
        composeRule.setContent {
            MaterialTheme { OnboardingScreen(onFinish = { finished = true }) }
        }
        // Advance through each page; the click on the last page finishes onboarding.
        repeat(onboardingPages.size) {
            composeRule.onNodeWithTag("onboarding-next").performClick()
        }
        finished shouldBe true
    }
}
