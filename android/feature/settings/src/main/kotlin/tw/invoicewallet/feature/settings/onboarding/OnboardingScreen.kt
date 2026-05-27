package tw.invoicewallet.feature.settings.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A single onboarding page. */
data class OnboardingPage(val title: String, val body: String)

/** The privacy-first introduction shown once, on first launch (ARCHITECTURE: 資料屬於使用者). */
val onboardingPages: List<OnboardingPage> = listOf(
    OnboardingPage(
        title = "你的發票，只屬於你",
        body = "這是一個本機優先的發票錢包。沒有帳號、沒有雲端同步，資料不會離開這支手機。",
    ),
    OnboardingPage(
        title = "本機加密保存",
        body = "所有發票都存在裝置上、以 SQLCipher 加密；金鑰鎖在 Android Keystore，預設不上傳任何伺服器。",
    ),
    OnboardingPage(
        title = "對外連線透明",
        body = "只有在你查詢店名或對獎時，App 才會連到公開資料（財政部 / 商業登記）。你的購買明細永遠不外流。",
    ),
    OnboardingPage(
        title = "開始使用",
        body = "掃描你的第一張發票，建立屬於自己的發票錢包吧。",
    ),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    var page by remember { mutableIntStateOf(0) }
    val current = onboardingPages[page]
    val isLast = page == onboardingPages.lastIndex

    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = current.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = current.body,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = "${page + 1} / ${onboardingPages.size}",
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { if (isLast) onFinish() else page++ },
                modifier = Modifier.fillMaxWidth().testTag("onboarding-next"),
            ) {
                Text(if (isLast) "開始使用" else "下一步")
            }
        }
    }
}
