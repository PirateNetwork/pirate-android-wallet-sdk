package pirate.android.sdk.internal.repository

import pirate.android.sdk.internal.model.JniBlockMeta
import pirate.android.sdk.model.BlockHeight
import pirate.lightwallet.client.model.CompactBlockUnsafe
import kotlinx.coroutines.flow.Flow

/**
 * Interface for storing compact blocks.
 */
interface CompactBlockRepository {
    /**
     * Gets the highest block that is currently stored.
     *
     * @return the latest block height
     */
    suspend fun getLatestHeight(): BlockHeight?

    /**
     * Fetch the compact block for the given height, if it exists.
     *
     * @param height of the block we are looking for
     * @return the compact block summary object or null when it did not exist
     */
    suspend fun findCompactBlock(height: BlockHeight): JniBlockMeta?

    /**
     * This function is supposed to be used once the whole blocks sync process done. It removes all the temporary
     * block files from the device disk.
     *
     * @return true when all block files are deleted or do not exist, false only if the deletion fails
     */
    suspend fun deleteAllCompactBlockFiles(): Boolean

    /**
     * This function is supposed to be used continuously while sync process is in progress. It removes all the temporary
     * block files from the given list of blocks.
     *
     * @param blocks list of compact block summary objects of blocks to delete
     * @return true when all block files from the list are deleted or do not exist, false only if the deletion fails
     */
    suspend fun deleteCompactBlockFiles(blocks: List<JniBlockMeta>): Boolean

    /**
     * Write the given flow of blocks to this store, which may be anything from an in-memory cache to a DB.
     *
     * @param blocks Flow of compact blocks to persist
     * @return list of compact block summary objects of blocks that were written
     */
    suspend fun write(blocks: Flow<CompactBlockUnsafe>): List<JniBlockMeta>

    /**
     * Remove every block above the given height.
     *
     * @param height the target height to which to rewind.
     */
    suspend fun rewindTo(height: BlockHeight)
}
