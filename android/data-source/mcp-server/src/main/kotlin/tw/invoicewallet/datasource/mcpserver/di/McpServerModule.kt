package tw.invoicewallet.datasource.mcpserver.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.datetime.Clock
import tw.invoicewallet.core.database.repository.AuthGrantRepository
import tw.invoicewallet.datasource.authz.AuthorizationEngine
import tw.invoicewallet.datasource.mcpserver.EncryptedMcpTokenStore
import tw.invoicewallet.datasource.mcpserver.McpServerControls
import tw.invoicewallet.datasource.mcpserver.McpServerManager
import tw.invoicewallet.datasource.mcpserver.McpTokenStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object McpServerModule {

    @Provides
    @Singleton
    fun provideAuthorizationEngine(repository: AuthGrantRepository, clock: Clock): AuthorizationEngine =
        AuthorizationEngine(repository = repository, clock = clock)

    @Provides
    @Singleton
    fun provideMcpTokenStore(@ApplicationContext context: Context): McpTokenStore = EncryptedMcpTokenStore(context)

    @Provides
    fun provideMcpServerControls(manager: McpServerManager): McpServerControls = manager
}
