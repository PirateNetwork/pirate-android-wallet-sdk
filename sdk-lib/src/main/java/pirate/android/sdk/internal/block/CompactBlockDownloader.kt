package pirate.android.sdk.internal.block

import pirate.android.sdk.exception.LightWalletException
import pirate.android.sdk.internal.Twig
import pirate.android.sdk.internal.ext.retryUpTo
import pirate.android.sdk.internal.model.JniBlockMeta
import pirate.android.sdk.internal.model.ext.from
import pirate.android.sdk.internal.repository.CompactBlockRepository
import pirate.android.sdk.model.BlockHeight
import pirate.lightwallet.client.LightWalletClient
import pirate.lightwallet.client.model.BlockHeightUnsafe
import pirate.lightwallet.client.model.CompactBlockUnsafe
import pirate.lightwallet.client.model.LightWalletEndpointInfoUnsafe
import pirate.lightwallet.client.model.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * Serves as a source of compact blocks received from the light wallet server. Once started, it will use the given
 * lightWallet client to request all the appropriate blocks and compact block store to persist them. By delegating to
 * these dependencies, the downloader remains agnostic to the particular implementation of how to retrieve and store
 * data; although, by default the SDK uses gRPC and SQL.
 *
 * @property lightWalletClient the client used for requesting compact blocks
 * @property compactBlockStore responsible for persisting the compact blocks that are received
 */
open class CompactBlockDownloader private constructor(val compactBlockRepository: CompactBlockRepository) {

    lateinit var lightWalletClient: LightWalletClient
        private set

    constructor(
        lightWalletClient: LightWalletClient,
        compactBlockRepository: CompactBlockRepository
    ) : this(compactBlockRepository) {
        this.lightWalletClient = lightWalletClient
    }

    /**
     * Requests the given range of blocks from the lightWalletClient and then persists them to the
     * compactBlockStore.
     *
     * @param heightRange the inclusive range of heights to request. For example 10..20 would
     * request 11 blocks (including block 10 and block 20).
     * @throws LightWalletException.DownloadBlockException if any error while downloading the blocks occurs
     * @return List of JniBlockMeta objects, which describe the original CompactBlock objects, which were just
     * downloaded and persisted on the device disk
     */
    @Throws(LightWalletException.DownloadBlockException::class)
    suspend fun downloadBlockRange(heightRange: ClosedRange<BlockHeight>): List<JniBlockMeta> {
        val filteredFlow = lightWalletClient.getBlockRange(
            BlockHeightUnsafe.from(heightRange.start)..BlockHeightUnsafe.from(heightRange.endInclusive)
        ).onEach { response ->
            when (response) {
                is Response.Success -> {
                    Twig.verbose { "Downloading block at height: ${response.result.height} succeeded." }
                }
                is Response.Failure -> {
                    Twig.warn { "Downloading blocks in range: $heightRange failed with: ${response.description}." }
                    throw LightWalletException.DownloadBlockException(
                        response.code,
                        response.description,
                        response.toThrowable()
                    )
                }
            }
        }
            .filterIsInstance<Response.Success<CompactBlockUnsafe>>()
            .map { response ->
                response.result
            }
            .onCompletion {
                if (it != null) {
                    Twig.warn { "Blocks in range $heightRange failed to download with: $it" }
                } else {
                    Twig.verbose { "All blocks in range $heightRange downloaded successfully" }
                }
            }

        return compactBlockRepository.write(filteredFlow)
    }

    /**
     * Rewind the storage to the given height, usually to handle reorgs. Deletes all blocks above
     * the given height.
     *
     * @param height the height to which the data will rewind.
     */
    suspend fun rewindToHeight(height: BlockHeight) =
        // TODO [#685]: Cancel anything in flight
        // TODO [#685]: https://github.com/zcash/zcash-android-wallet-sdk/issues/685
        compactBlockRepository.rewindTo(height)

    /**
     * Return the latest block height known by the lightWalletClient.
     *
     * @return the latest block height.
     */
    suspend fun getLatestBlockHeight() = lightWalletClient.getLatestBlockHeight()

    /**
     * Return the latest block height that has been persisted into the [CompactBlockRepository].
     *
     * @return the latest block height that has been persisted.
     */
    suspend fun getLastDownloadedHeight() =
        compactBlockRepository.getLatestHeight()

    suspend fun getServerInfo(): LightWalletEndpointInfoUnsafe? = withContext(IO) {
        retryUpTo(GET_SERVER_INFO_RETRIES) {
            when (val response = lightWalletClient.getServerInfo()) {
                is Response.Success -> return@withContext response.result
                else -> {
                    lightWalletClient.reconnect()
                    Twig.warn { "WARNING: reconnecting to server in response to failure (retry #${it + 1})" }
                }
            }
        }

        null
    }

    /**
     * Stop this downloader and cleanup any resources being used.
     */
    suspend fun stop() {
        withContext(Dispatchers.IO) {
            lightWalletClient.shutdown()
        }
    }

    /**
     * Fetch the details of a known transaction.
     *
     * @return the full transaction info.
     */
    suspend fun fetchTransaction(txId: ByteArray) = lightWalletClient.fetchTransaction(txId)

    companion object {
        private const val GET_SERVER_INFO_RETRIES = 6
    }
}
