package tw.invoicewallet.feature.lottery.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tw.invoicewallet.feature.lottery.LotteryApiClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LotteryModule {

    @Provides
    @Singleton
    fun provideLotteryApiClient(): LotteryApiClient = LotteryApiClient()
}
