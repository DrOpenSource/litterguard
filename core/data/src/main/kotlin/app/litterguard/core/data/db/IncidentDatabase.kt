package app.litterguard.core.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** One row per event (PRD §8). Times are epoch milliseconds; enums are stored by name. */
@Entity(tableName = "incident", indices = [Index("confirmedAt")])
internal data class IncidentEntity(
    @PrimaryKey val id: String,
    val siteName: String,
    val kind: String,
    /** Comma-separated `ObjectCategory` names. */
    val actorCategories: String,
    val actorEnteredAt: Long?,
    val actorLeftAt: Long?,
    val confirmedAt: Long,
    val boxLeft: Float?,
    val boxTop: Float?,
    val boxRight: Float?,
    val boxBottom: Float?,
    val deliveryState: String,
    val reviewLabel: String,
    val detectorDescription: String,
    val mergedCount: Int,
)

@Entity(
    tableName = "evidence_file",
    foreignKeys = [
        ForeignKey(
            entity = IncidentEntity::class,
            parentColumns = ["id"],
            childColumns = ["incidentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("incidentId")],
)
internal data class EvidenceFileEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val incidentId: String,
    val kind: String,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val capturedAt: Long,
)

internal data class IncidentWithEvidence(
    @Embedded val incident: IncidentEntity,
    @Relation(parentColumn = "id", entityColumn = "incidentId")
    val evidence: List<EvidenceFileEntity>,
)

@Dao
internal interface IncidentDao {
    @Transaction
    @Query("SELECT * FROM incident ORDER BY confirmedAt DESC")
    fun observeAll(): Flow<List<IncidentWithEvidence>>

    @Transaction
    @Query("SELECT * FROM incident WHERE id = :id")
    suspend fun find(id: String): IncidentWithEvidence?

    @Insert
    suspend fun insert(incident: IncidentEntity)

    @Insert
    suspend fun insertEvidence(files: List<EvidenceFileEntity>)

    @Transaction
    suspend fun insertWithEvidence(
        incident: IncidentEntity,
        files: List<EvidenceFileEntity>,
    ) {
        insert(incident)
        insertEvidence(files)
    }

    @Query("UPDATE incident SET mergedCount = mergedCount + 1 WHERE id = :id")
    suspend fun incrementMergedCount(id: String)

    @Query("UPDATE incident SET deliveryState = :state WHERE id = :id")
    suspend fun setDeliveryState(
        id: String,
        state: String,
    )

    @Query("UPDATE incident SET reviewLabel = :label WHERE id = :id")
    suspend fun setReviewLabel(
        id: String,
        label: String,
    )
}

@Database(entities = [IncidentEntity::class, EvidenceFileEntity::class], version = 1, exportSchema = true)
internal abstract class IncidentDatabase : RoomDatabase() {
    abstract fun incidents(): IncidentDao

    companion object {
        const val FILE_NAME = "litterguard.db"
    }
}
