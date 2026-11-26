package app.litterguard.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.litterguard.core.domain.config.Sensitivity
import app.litterguard.core.domain.ports.SiteConfigRepository
import app.litterguard.core.domain.site.SiteConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DataStoreSiteConfigRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : SiteConfigRepository {
        override val config: Flow<SiteConfig> =
            store.data
                .catch { error ->
                    if (error !is IOException) throw error
                    Timber.w(error, "Could not read settings; using defaults")
                    emit(emptyPreferences())
                }.map { it.toSiteConfig() }

        override suspend fun update(transform: (SiteConfig) -> SiteConfig) {
            store.edit { preferences -> preferences.write(transform(preferences.toSiteConfig())) }
        }

        private fun Preferences.toSiteConfig() =
            SiteConfig(
                siteName = this[SITE_NAME] ?: SiteConfig.DEFAULT_SITE_NAME,
                zone = this[ZONE]?.let(ZoneCodec::decode),
                sensitivity =
                    Sensitivity.entries.firstOrNull { it.name == this[SENSITIVITY] } ?: Sensitivity.NORMAL,
                alertChatId = this[CHAT_ID],
                alertChatTitle = this[CHAT_TITLE],
            )

        private fun MutablePreferences.write(config: SiteConfig) {
            this[SITE_NAME] = config.siteName
            this[SENSITIVITY] = config.sensitivity.name
            writeOrRemove(ZONE, config.zone?.let(ZoneCodec::encode))
            writeOrRemove(CHAT_ID, config.alertChatId)
            writeOrRemove(CHAT_TITLE, config.alertChatTitle)
        }

        private fun MutablePreferences.writeOrRemove(
            key: Preferences.Key<String>,
            value: String?,
        ) {
            if (value == null) remove(key) else this[key] = value
        }

        private companion object {
            val SITE_NAME = stringPreferencesKey("site_name")
            val ZONE = stringPreferencesKey("zone")
            val SENSITIVITY = stringPreferencesKey("sensitivity")
            val CHAT_ID = stringPreferencesKey("alert_chat_id")
            val CHAT_TITLE = stringPreferencesKey("alert_chat_title")
        }
    }
