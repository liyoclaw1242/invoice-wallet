package tw.invoicewallet.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabasePassphraseProviderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun returns_a_32_byte_passphrase_that_is_stable_across_instances() {
        val first = DatabasePassphraseProvider(context).getOrCreatePassphrase()
        val second = DatabasePassphraseProvider(context).getOrCreatePassphrase()

        first.toList() shouldHaveSize 32
        second.toList() shouldBe first.toList()
    }
}
