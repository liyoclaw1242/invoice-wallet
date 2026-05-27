package tw.invoicewallet.feature.scan.merchant

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CachingMerchantDirectoryTest {

    @Test
    fun `caches a successful lookup so the source is queried only once`() = runTest {
        var calls = 0
        val source = object : MerchantNameSource {
            override suspend fun fetch(taxId: String): String? {
                calls++
                return "瑪可希維"
            }
        }
        val directory = CachingMerchantDirectory(source)

        directory.nameFor("90650686") shouldBe "瑪可希維"
        directory.nameFor("90650686") shouldBe "瑪可希維"

        calls shouldBe 1
    }

    @Test
    fun `returns null and stays usable when the source throws`() = runTest {
        val source = object : MerchantNameSource {
            override suspend fun fetch(taxId: String): String? = error("offline")
        }

        CachingMerchantDirectory(source).nameFor("90650686") shouldBe null
    }
}
