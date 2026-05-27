package tw.invoicewallet.feature.scan.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
}
