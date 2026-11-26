package app.litterguard.core.alerts

import app.litterguard.core.alerts.delivery.AlertCaption
import app.litterguard.core.alerts.delivery.IncidentDelivery
import app.litterguard.core.alerts.delivery.WorkManagerAlertSink
import app.litterguard.core.alerts.telegram.TelegramClient
import app.litterguard.core.domain.ports.AlertChannelSetup
import app.litterguard.core.domain.ports.AlertSink
import app.litterguard.core.domain.ports.Deterrent
import app.litterguard.core.domain.ports.EvidenceStore
import app.litterguard.core.domain.ports.IncidentRepository
import app.litterguard.core.domain.ports.SiteConfigRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface AlertsBindings {
    @Binds
    fun deterrent(implementation: SirenPlayer): Deterrent

    @Binds
    fun alertSink(implementation: WorkManagerAlertSink): AlertSink

    @Binds
    fun botCredentials(implementation: SecretStore): BotCredentials
}

@Module
@InstallIn(SingletonComponent::class)
internal object AlertsProviders {
    private const val CONNECT_SECONDS = 10L
    private const val READ_SECONDS = 30L
    private const val WRITE_SECONDS = 60L

    // Deliberately no logging interceptor: request URLs contain the bot token.
    @Provides
    @Singleton
    fun telegramClient(): TelegramClient =
        TelegramClient(
            http =
                OkHttpClient
                    .Builder()
                    .connectTimeout(CONNECT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(READ_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(WRITE_SECONDS, TimeUnit.SECONDS)
                    .build(),
            baseUrl = "https://api.telegram.org".toHttpUrl(),
            dispatcher = Dispatchers.IO,
        )

    @Provides
    @Singleton
    fun alertChannelSetup(
        client: TelegramClient,
        credentials: BotCredentials,
        site: SiteConfigRepository,
    ): AlertChannelSetup = TelegramAlertChannel(client, credentials, site, Dispatchers.IO)

    @Provides
    fun incidentDelivery(
        client: TelegramClient,
        credentials: BotCredentials,
        site: SiteConfigRepository,
        incidents: IncidentRepository,
        evidence: EvidenceStore,
    ): IncidentDelivery =
        IncidentDelivery(
            client = client,
            credentials = credentials,
            site = site,
            incidents = incidents,
            evidence = evidence,
            captions = AlertCaption { ZoneId.systemDefault() },
            io = Dispatchers.IO,
        )
}
