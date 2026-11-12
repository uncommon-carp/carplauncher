package app.olauncher.focus

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "launch_event", indices = [Index("timestamp"), Index("appPackage", "user")])
data class LaunchEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appPackage: String,
    val user: String,
    val timestamp: Long,
)

@Dao
interface LaunchEventDao {
    @Insert
    suspend fun insert(event: LaunchEvent)

    @Query("SELECT * FROM launch_event WHERE timestamp >= :since")
    suspend fun since(since: Long): List<LaunchEvent>

    @Query("SELECT COUNT(*) FROM launch_event WHERE appPackage = :appPackage AND user = :user AND timestamp >= :since")
    suspend fun countSince(appPackage: String, user: String, since: Long): Int

    @Query("DELETE FROM launch_event WHERE timestamp < :before")
    suspend fun deleteBefore(before: Long)
}

@Database(entities = [LaunchEvent::class], version = 1, exportSchema = false)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun launchEvents(): LaunchEventDao

    companion object {
        @Volatile
        private var instance: FocusDatabase? = null

        fun get(context: Context): FocusDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, FocusDatabase::class.java, "focus.db")
                .build()
                .also { instance = it }
        }
    }
}

/** Local log of launches made through the launcher. Nothing leaves the device. */
class LaunchRepository(context: Context) {
    private val dao = FocusDatabase.get(context).launchEvents()

    suspend fun record(appPackage: String, user: String, now: Long = System.currentTimeMillis()) {
        dao.insert(LaunchEvent(appPackage = appPackage, user = user, timestamp = now))
        dao.deleteBefore(now - LAUNCH_RETENTION_DAYS * DAY_MILLIS)
    }

    suspend fun statsSince(since: Long): Map<String, LaunchStats> =
        aggregateLaunches(dao.since(since).map { LaunchRecord(appKey(it.appPackage, it.user), it.timestamp) }, since)

    suspend fun countSince(appPackage: String, user: String, since: Long): Int =
        dao.countSince(appPackage, user, since)
}
