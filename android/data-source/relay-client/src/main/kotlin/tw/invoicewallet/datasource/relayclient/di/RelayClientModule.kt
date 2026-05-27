package tw.invoicewallet.datasource.relayclient.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import tw.invoicewallet.datasource.relayclient.EncryptedRelayDeviceStore
import tw.invoicewallet.datasource.relayclient.RelayDeviceStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RelayClientModule {

    @Provides
    @Singleton
    fun provideRelayDeviceStore(@ApplicationContext context: Context): RelayDeviceStore =
        EncryptedRelayDeviceStore(context)
}
