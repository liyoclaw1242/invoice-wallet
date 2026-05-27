package tw.invoicewallet.core.testing

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtensionTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `Main dispatcher is replaced so Main-confined work runs under the test dispatcher`() =
        runTest(mainDispatcher.testDispatcher) {
            var ran = false
            // Would throw "Module with the Main dispatcher had failed to initialize"
            // if Dispatchers.Main had not been replaced by the extension.
            withContext(Dispatchers.Main) { ran = true }
            ran shouldBe true
        }
}
