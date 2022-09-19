package pirate.android.sdk.internal.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import pirate.android.sdk.db.entity.PirateCompactBlockEntity

//
// Database
//

/**
 * The "Cache DB", serving as a cache of compact blocks, waiting to be processed. This will contain
 * the entire blockchain, from the birthdate of the wallet, forward. The [PirateCompactBlockProcessor]
 * will copy blocks from this database, as they are scanned. In the future, those blocks can be
 * deleted because they are no longer needed. Currently, this efficiency has not been implemented.
 */
@Database(
    entities = [PirateCompactBlockEntity::class],
    version = 1,
    exportSchema = true
)
abstract class PirateCompactBlockDb : RoomDatabase() {
    abstract fun compactBlockDao(): CompactBlockDao
}

//
// Data Access Objects
//

/**
 * Data access object for compact blocks in the "Cache DB."
 */
@Dao
interface CompactBlockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: PirateCompactBlockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: List<PirateCompactBlockEntity>)

    @Query("DELETE FROM compactblocks WHERE height > :height")
    suspend fun rewindTo(height: Int)

    @Query("SELECT MAX(height) FROM compactblocks")
    suspend fun latestBlockHeight(): Int

    @Query("SELECT data FROM compactblocks WHERE height = :height")
    suspend fun findCompactBlock(height: Int): ByteArray?
}
