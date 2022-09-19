package pirate.android.sdk.internal.block

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import pirate.android.sdk.db.entity.PirateCompactBlockEntity
import pirate.android.sdk.internal.SdkDispatchers
import pirate.android.sdk.internal.SdkExecutors
import pirate.android.sdk.internal.db.PirateCompactBlockDb
import cash.z.wallet.sdk.rpc.CompactFormats
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * An implementation of CompactBlockStore that persists information to a database in the given
 * path. This represents the "cache db" or local cache of compact blocks waiting to be scanned.
 */
class PirateCompactBlockDbStore private constructor(
    private val cacheDb: PirateCompactBlockDb
) : CompactBlockStore {

    private val cacheDao = cacheDb.compactBlockDao()

    override suspend fun getLatestHeight(): Int = max(0, cacheDao.latestBlockHeight())

    override suspend fun findCompactBlock(height: Int): CompactFormats.CompactBlock? =
        cacheDao.findCompactBlock(height)?.let { CompactFormats.CompactBlock.parseFrom(it) }

    override suspend fun write(result: List<CompactFormats.CompactBlock>) =
        cacheDao.insert(result.map { PirateCompactBlockEntity(it.height.toInt(), it.toByteArray()) })

    override suspend fun rewindTo(height: Int) =
        cacheDao.rewindTo(height)

    override suspend fun close() {
        withContext(SdkDispatchers.DATABASE_IO) {
            cacheDb.close()
        }
    }

    companion object {
        /**
         * @param appContext the application context. This is used for creating the database.
         * @property dbPath the absolute path to the database.
         */
        fun new(appContext: Context, dbPath: String): PirateCompactBlockDbStore {
            val cacheDb = createCompactBlockCacheDb(appContext.applicationContext, dbPath)

            return PirateCompactBlockDbStore(cacheDb)
        }

        private fun createCompactBlockCacheDb(
            appContext: Context,
            dbPath: String
        ): PirateCompactBlockDb {
            return Room.databaseBuilder(appContext, PirateCompactBlockDb::class.java, dbPath)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                // this is a simple cache of blocks. destroying the db should be benign
                .fallbackToDestructiveMigration()
                .setQueryExecutor(SdkExecutors.DATABASE_IO)
                .setTransactionExecutor(SdkExecutors.DATABASE_IO)
                .build()
        }
    }
}
