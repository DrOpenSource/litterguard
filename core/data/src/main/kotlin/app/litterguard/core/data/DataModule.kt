package app.litterguard.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.litterguard.core.data.db.IncidentDao
import app.litterguard.core.data.db.IncidentDatabase
import app.litterguard.core.domain.ports.EvidenceStore
import app.litterguard.core.domain.ports.IncidentRepository
import app.litterguard.core.domain.ports.SiteConfigRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataBindings {
    @Binds
    fun incidentRepository(implementation: RoomIncidentRepository): IncidentRepository

    @Binds
    fun siteConfigRepository(implementation: DataStoreSiteConfigRepository): SiteConfigRepository

    @Binds
    fun evidenceStore(implementation: FileEvidenceStore): EvidenceStore
}

@Module
@InstallIn(SingletonComponent::class)
internal object DataProviders {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): IncidentDatabase =
        Room.databaseBuilder(context, IncidentDatabase::class.java, IncidentDatabase.FILE_NAME).build()

    @Provides
    fun incidentDao(database: IncidentDatabase): IncidentDao = database.incidents()

    @Provides
    @Singleton
    fun siteDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("site") }
}
