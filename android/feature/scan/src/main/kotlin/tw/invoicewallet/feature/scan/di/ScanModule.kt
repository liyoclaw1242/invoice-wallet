package tw.invoicewallet.feature.scan.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import tw.invoicewallet.feature.scan.merchant.CachingMerchantDirectory
import tw.invoicewallet.feature.scan.merchant.GovMerchantNameSource
import tw.invoicewallet.feature.scan.merchant.MerchantDirectory
import tw.invoicewallet.feature.scan.merchant.MerchantNameSource
import tw.invoicewallet.feature.scan.recognition.InvoiceRecognizer
import tw.invoicewallet.feature.scan.recognition.MlKitInvoiceRecognizer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScanModule {

    @Provides
    @Singleton
    fun provideInvoiceRecognizer(@ApplicationContext context: Context): InvoiceRecognizer =
        MlKitInvoiceRecognizer(context)

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 6_000
            connectTimeoutMillis = 6_000
        }
    }

    @Provides
    @Singleton
    fun provideMerchantNameSource(client: HttpClient): MerchantNameSource = GovMerchantNameSource(client)

    @Provides
    @Singleton
    fun provideMerchantDirectory(source: MerchantNameSource): MerchantDirectory = CachingMerchantDirectory(source)
}
