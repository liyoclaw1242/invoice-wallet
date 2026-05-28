package tw.invoicewallet.feature.settings.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tw.invoicewallet.core.designsystem.R
import tw.invoicewallet.core.designsystem.components.PillButton
import tw.invoicewallet.core.designsystem.components.QuietPill
import tw.invoicewallet.core.designsystem.theme.WalletTheme

/** A single onboarding page: an illustration up top, then the editorial pitch. */
data class OnboardingPage(val title: String, val body: String, @DrawableRes val illustration: Int)

/** The four-step privacy + utility introduction shown once on first launch. */
val onboardingPages: List<OnboardingPage> = listOf(
    OnboardingPage(
        title = "你的發票，鎖在你手上",
        body = "沒有帳號、沒有雲端同步、沒有遠端備份。所有資料用 SQLCipher 加密存在這支手機上，連我們也讀不到。",
        illustration = R.drawable.illust_onboarding_local,
    ),
    OnboardingPage(
        title = "對準就掃，掃完就存",
        body = "相機常駐，連續掃一疊不用每張按確認。歪斜、淡墨、熱感紙的發票都能辨識。",
        illustration = R.drawable.illust_onboarding_scan,
    ),
    OnboardingPage(
        title = "載具發票一鍵歸戶",
        body = "從財政部下載手機條碼載具 CSV，匯入後自動補齊既有發票；你已經編輯過的備註、標籤、對獎狀態都會留著。",
        illustration = R.drawable.illust_onboarding_csv,
    ),
    OnboardingPage(
        title = "AI 想幫你查？要先拿到你的鑰匙",
        body = "電腦上、手機上的 Claude 都可以查詢你的發票，但每一條通道都得你手動授權；隨時撤銷。",
        illustration = R.drawable.illust_onboarding_ai,
    ),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    WalletTheme {
        var pageIndex by remember { mutableIntStateOf(0) }
        val current = onboardingPages[pageIndex]
        val isLast = pageIndex == onboardingPages.lastIndex

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(WalletTheme.colors.surfaceBase)
                .testTag("onboarding-root"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = WalletTheme.spacing.lg)
                    .padding(top = 56.dp, bottom = WalletTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                DotsIndicator(pageCount = onboardingPages.size, currentIndex = pageIndex)

                PageContent(current)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PillButton(
                        text = if (isLast) "開始使用" else "下一步",
                        onClick = { if (isLast) onFinish() else pageIndex++ },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .testTag("onboarding-next"),
                    )
                    if (!isLast) {
                        QuietPill(
                            text = "略過",
                            onClick = onFinish,
                            modifier = Modifier.testTag("onboarding-skip"),
                        )
                    } else {
                        // Reserve the same vertical space so the button doesn't jump on the last page.
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding-page-${page.illustration}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WalletTheme.spacing.lg),
    ) {
        Image(
            painter = painterResource(page.illustration),
            contentDescription = null, // decorative; the heading carries the meaning
            modifier = Modifier
                .size(280.dp)
                .clip(WalletTheme.shapes.card),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WalletTheme.spacing.sm),
        ) {
            androidx.compose.material3.Text(
                text = page.title,
                style = WalletTheme.typography.displaySm.copy(fontWeight = FontWeight.SemiBold),
                color = WalletTheme.colors.inkPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = WalletTheme.spacing.sm),
            )
            androidx.compose.material3.Text(
                text = page.body,
                style = WalletTheme.typography.bodyLg,
                color = WalletTheme.colors.inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = WalletTheme.spacing.md),
            )
        }
    }
}

@Composable
private fun DotsIndicator(pageCount: Int, currentIndex: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { i ->
            val active = i == currentIndex
            Box(
                modifier = Modifier
                    .size(if (active) 10.dp else 6.dp)
                    .clip(WalletTheme.shapes.pill)
                    .background(
                        if (active) {
                            WalletTheme.colors.accentTealDeep
                        } else {
                            WalletTheme.colors.inkTertiary.copy(alpha = 0.5f)
                        },
                    ),
            )
        }
    }
}
