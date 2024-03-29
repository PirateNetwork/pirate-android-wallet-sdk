package pirate.android.sdk.internal

import pirate.android.sdk.internal.model.Checkpoint
import pirate.android.sdk.internal.model.JniBlockMeta
import pirate.android.sdk.model.Account
import pirate.android.sdk.model.BlockHeight
import pirate.android.sdk.model.UnifiedFullViewingKey
import pirate.android.sdk.model.WalletBalance
import pirate.android.sdk.model.Arrrtoshi

// This class is currently unused, although the goal is to swap out usages of BackendExt for this throughout the SDK.
@Suppress("TooManyFunctions")
internal class TypesafeBackendImpl(private val backend: Backend) : TypesafeBackend {
    override suspend fun initAccountsTable(vararg keys: UnifiedFullViewingKey) =
        backend.initAccountsTable(*keys)

    override suspend fun initAccountsTable(
        seed: ByteArray,
        numberOfAccounts: Int
    ): List<UnifiedFullViewingKey> = backend.initAccountsTableTypesafe(seed, numberOfAccounts)

    override suspend fun initBlocksTable(checkpoint: Checkpoint) = backend.initBlocksTable(checkpoint)

    override suspend fun getCurrentAddress(account: Account): String = getCurrentAddress(account)

    override suspend fun listTransparentReceivers(account: Account): List<String> =
        backend.listTransparentReceivers(account)

    override suspend fun getBalance(account: Account): Arrrtoshi = backend.getBalance(account)

    override fun getBranchIdForHeight(height: BlockHeight): Long = backend.getBranchIdForHeight(height.value)

    override suspend fun getVerifiedBalance(account: Account): Arrrtoshi = backend.getVerifiedBalance(account)

    override suspend fun getNearestRewindHeight(height: BlockHeight): BlockHeight =
        backend.getNearestRewindHeight(height)

    override suspend fun rewindToHeight(height: BlockHeight) = backend.rewindToHeight(height)

    override suspend fun getLatestBlockHeight(): BlockHeight? = backend.getLatestBlockHeight()

    override suspend fun findBlockMetadata(height: BlockHeight): JniBlockMeta? = backend.findBlockMetadata(height)

    override suspend fun rewindBlockMetadataToHeight(height: BlockHeight) = backend.rewindBlockMetadataToHeight(height)

    /**
     * @param limit The limit provides an efficient way how to restrict the portion of blocks, which will be validated.
     * @return Null if successful. If an error occurs, the height will be the height where the error was detected.
     */
    override suspend fun validateCombinedChainOrErrorBlockHeight(limit: Long?): BlockHeight? =
        backend.validateCombinedChainOrErrorBlockHeight(limit)

    override suspend fun getDownloadedUtxoBalance(address: String): WalletBalance =
        backend.getDownloadedUtxoBalance(address)
}
