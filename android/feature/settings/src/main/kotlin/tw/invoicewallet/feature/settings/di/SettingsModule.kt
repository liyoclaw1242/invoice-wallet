package tw.invoicewallet.feature.settings.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import tw.invoicewallet.feature.settings.CarrierCodeStore
import tw.invoicewallet.feature.settings.DataStoreSettingsRepository
import tw.invoicewallet.feature.settings.EncryptedCarrierCodeStore
import tw.invoicewallet.feature.settings.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("invoice-wallet-settings") }

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<Preferences>): SettingsRepository =
        DataStoreSettingsRepository(dataStore)

    @Provides
    @Singleton
    fun provideCarrierCodeStore(@ApplicationContext context: Context): CarrierCodeStore =
        EncryptedCarrierCodeStore(context)
}
