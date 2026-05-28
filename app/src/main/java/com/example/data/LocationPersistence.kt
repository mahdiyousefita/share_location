package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "location_history")
data class ShareHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    val method: String = "Link" // e.g., "SMS", "System Link", "Mock Circle"
)

@Dao
interface LocationHistoryDao {
    @Query("SELECT * FROM location_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ShareHistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: ShareHistoryEntry): Long

    @Query("DELETE FROM location_history WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM location_history")
    suspend fun clearAllHistory()
}

@Database(entities = [ShareHistoryEntry::class], version = 1, exportSchema = false)
abstract class LocationDatabase : RoomDatabase() {
    abstract fun historyDao(): LocationHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: LocationDatabase? = null

        fun getDatabase(context: Context): LocationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocationDatabase::class.java,
                    "location_share_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class LocationRepository(private val dao: LocationHistoryDao) {
    val historyLog: Flow<List<ShareHistoryEntry>> = dao.getAllHistory()

    suspend fun logShare(entry: ShareHistoryEntry): Long {
        return dao.insertEntry(entry)
    }

    suspend fun deleteShare(id: Long) {
        dao.deleteEntry(id)
    }

    suspend fun clearHistory() {
        dao.clearAllHistory()
    }
}
