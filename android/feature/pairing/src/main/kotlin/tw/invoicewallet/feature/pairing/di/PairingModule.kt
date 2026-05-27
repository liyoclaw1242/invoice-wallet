package tw.invoicewallet.feature.pairing.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import tw.invoicewallet.feature.pairing.PairingClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PairingModule {

    // Builds its own HttpClient so it doesn't collide with other SingletonComponent clients.
    @Provides
    @Singleton
    fun providePairingClient(): PairingClient = PairingClient(HttpClient(OkHttp))
}
