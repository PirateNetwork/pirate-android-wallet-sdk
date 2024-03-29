package pirate.android.sdk.block

import androidx.annotation.VisibleForTesting
import pirate.android.sdk.BuildConfig
import pirate.android.sdk.annotation.OpenForTesting
import pirate.android.sdk.exception.CompactBlockProcessorException
import pirate.android.sdk.exception.CompactBlockProcessorException.EnhanceTransactionError.EnhanceTxDecryptError
import pirate.android.sdk.exception.CompactBlockProcessorException.EnhanceTransactionError.EnhanceTxDownloadError
import pirate.android.sdk.exception.CompactBlockProcessorException.MismatchedNetwork
import pirate.android.sdk.exception.InitializeException
import pirate.android.sdk.exception.LightWalletException
import pirate.android.sdk.exception.RustLayerException
import pirate.android.sdk.ext.PirateSdk
import pirate.android.sdk.ext.PirateSdk.MAX_BACKOFF_INTERVAL
import pirate.android.sdk.ext.PirateSdk.POLL_INTERVAL
import pirate.android.sdk.internal.Backend
import pirate.android.sdk.internal.Twig
import pirate.android.sdk.internal.block.CompactBlockDownloader
import pirate.android.sdk.internal.createAccountAndGetSpendingKey
import pirate.android.sdk.internal.ext.isNullOrEmpty
import pirate.android.sdk.internal.ext.length
import pirate.android.sdk.internal.ext.retryUpTo
import pirate.android.sdk.internal.ext.retryWithBackoff
import pirate.android.sdk.internal.ext.toHexReversed
import pirate.android.sdk.internal.getBalance
import pirate.android.sdk.internal.getBranchIdForHeight
import pirate.android.sdk.internal.getCurrentAddress
import pirate.android.sdk.internal.getDownloadedUtxoBalance
import pirate.android.sdk.internal.getNearestRewindHeight
import pirate.android.sdk.internal.getVerifiedBalance
import pirate.android.sdk.internal.listTransparentReceivers
import pirate.android.sdk.internal.model.BlockBatch
import pirate.android.sdk.internal.model.DbTransactionOverview
import pirate.android.sdk.internal.model.JniBlockMeta
import pirate.android.sdk.internal.model.ext.from
import pirate.android.sdk.internal.model.ext.toBlockHeight
import pirate.android.sdk.internal.network
import pirate.android.sdk.internal.repository.DerivedDataRepository
import pirate.android.sdk.internal.rewindToHeight
import pirate.android.sdk.internal.validateCombinedChainOrErrorBlockHeight
import pirate.android.sdk.model.Account
import pirate.android.sdk.model.BlockHeight
import pirate.android.sdk.model.PercentDecimal
import pirate.android.sdk.model.UnifiedSpendingKey
import pirate.android.sdk.model.WalletBalance
import pirate.android.sdk.model.PirateNetwork
import pirate.lightwallet.client.ext.BenchmarkingExt
import pirate.lightwallet.client.fixture.BenchmarkingBlockRangeFixture
import pirate.lightwallet.client.model.BlockHeightUnsafe
import pirate.lightwallet.client.model.GetAddressUtxosReplyUnsafe
import pirate.lightwallet.client.model.LightWalletEndpointInfoUnsafe
import pirate.lightwallet.client.model.Response
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Responsible for processing the compact blocks that are received from the lightwallet server. This class encapsulates
 * all the business logic required to validate and scan the blockchain and is therefore tightly coupled with
 * librustzcash.
 *
 * @property downloader the component responsible for downloading compact blocks and persisting them
 * locally for processing.
 * @property repository the repository holding transaction information.
 * @property backend the librustzcash functionality available and exposed to the SDK.
 * @param minimumHeight the lowest height that we could care about. This is mostly used during
 * reorgs as a backstop to make sure we do not rewind beyond sapling activation. It also is factored
 * in when considering initial range to download. In most cases, this should be the birthday height
 * of the current wallet--the height before which we do not need to scan for transactions.
 */
@OpenForTesting
@Suppress("TooManyFunctions", "LargeClass")
class CompactBlockProcessor internal constructor(
    val downloader: CompactBlockDownloader,
    private val repository: DerivedDataRepository,
    private val backend: Backend,
    minimumHeight: BlockHeight
) {
    /**
     * Callback for any non-trivial errors that occur while processing compact blocks.
     *
     * @return true when processing should continue. Return false when the error is unrecoverable
     * and all processing should halt and stop retrying.
     */
    var onProcessorErrorListener: ((Throwable) -> Boolean)? = null

    /**
     * Callback for reorgs. This callback is invoked when validation fails with the height at which
     * an error was found and the lower bound to which the data will rewind, at most.
     */
    var onChainErrorListener: ((errorHeight: BlockHeight, rewindHeight: BlockHeight) -> Any)? = null

    /**
     * Callback for setup errors that occur prior to processing compact blocks. Can be used to
     * override any errors from [verifySetup]. When this listener is missing then all setup errors
     * will result in the processor not starting. This is particularly useful for wallets to receive
     * a callback right before the SDK will reject a lightwalletd server because it appears not to
     * match.
     *
     * @return true when the setup error should be ignored and processing should be allowed to
     * start. Otherwise, processing will not begin.
     */
    var onSetupErrorListener: ((Throwable) -> Boolean)? = null

    private val consecutiveChainErrors = AtomicInteger(0)

    /**
     * The zcash network that is being processed. Either Testnet or Mainnet.
     */
    val network = backend.network

    private val lowerBoundHeight: BlockHeight = BlockHeight(
        max(
            network.saplingActivationHeight.value,
            minimumHeight.value - MAX_REORG_SIZE
        )
    )

    private val _state: MutableStateFlow<State> = MutableStateFlow(State.Initialized)
    private val _progress = MutableStateFlow(PercentDecimal.ZERO_PERCENT)
    private val _processorInfo = MutableStateFlow(ProcessorInfo(null, null, null, null))
    private val _networkHeight = MutableStateFlow<BlockHeight?>(null)
    private val processingMutex = Mutex()

    /**
     * Flow of birthday heights. The birthday is essentially the first block that the wallet cares
     * about. Any prior block can be ignored. This is not a fixed value because the height is
     * influenced by the first transaction, which isn't always known. So we start with an estimation
     * and improve it as the wallet progresses. Once the first transaction occurs, this value is
     * effectively fixed.
     */
    private val _birthdayHeight = MutableStateFlow(lowerBoundHeight)

    /**
     * The flow of state values so that a wallet can monitor the state of this class without needing
     * to poll.
     */
    val state = _state.asStateFlow()

    /**
     * The flow of progress values so that a wallet can monitor how much downloading remains
     * without needing to poll.
     */
    val progress = _progress.asStateFlow()

    /**
     * The flow of detailed processorInfo like the range of blocks that shall be downloaded and
     * scanned. This gives the wallet a lot of insight into the work of this processor.
     */
    val processorInfo = _processorInfo.asStateFlow()

    /**
     * The flow of network height. This value is updated at the same time that [processorInfo] is
     * updated but this allows consumers to have the information pushed instead of polling.
     */
    val networkHeight = _networkHeight.asStateFlow()

    /**
     * The first block this wallet cares about anything prior can be ignored. If a wallet has no
     * transactions, this value will later update to 100 blocks before the first transaction,
     * rounded down to the nearest 100. So in some cases, this is a dynamic value.
     */
    val birthdayHeight = _birthdayHeight.value

    /**
     * Download compact blocks, verify and scan them until [stop] is called.
     */
    @Suppress("LongMethod")
    suspend fun start() {
        verifySetup()

        updateBirthdayHeight()

        // Clear any undeleted left over block files from previous sync attempts
        deleteAllBlockFiles(
            downloader = downloader,
            lastKnownHeight = getLastScannedHeight(repository)
        )

        Twig.debug { "setup verified. processor starting" }

        // using do/while makes it easier to execute exactly one loop which helps with testing this processor quickly
        // (because you can start and then immediately set isStopped=true to always get precisely one loop)
        do {
            retryWithBackoff(::onProcessorError, maxDelayMillis = MAX_BACKOFF_INTERVAL) {
                val result = processingMutex.withLockLogged("processNewBlocks") {
                    processNewBlocks()
                }
                // immediately process again after failures in order to download new blocks right away
                when (result) {
                    BlockProcessingResult.Reconnecting -> {
                        val napTime = calculatePollInterval(true)
                        Twig.debug {
                            "Unable to process new blocks because we are disconnected! Attempting to " +
                                "reconnect in ${napTime}ms"
                        }
                        delay(napTime)
                    }

                    BlockProcessingResult.NoBlocksToProcess -> {
                        val noWorkDone = _processorInfo.value.lastSyncRange?.isEmpty() ?: true
                        val summary = if (noWorkDone) {
                            "Nothing to process: no new blocks to sync"
                        } else {
                            "Done processing blocks"
                        }
                        consecutiveChainErrors.set(0)
                        val napTime = calculatePollInterval()
                        Twig.debug {
                            "$summary Sleeping for ${napTime}ms " +
                                "(latest height: ${_processorInfo.value.networkBlockHeight})."
                        }
                        delay(napTime)
                    }

                    is BlockProcessingResult.FailedEnhance -> {
                        Twig.error {
                            "Failed while enhancing transaction details at height: ${result.error.height} +" +
                                "with: ${result.error}"
                        }
                        checkErrorResult(result.error.height)
                    }

                    is BlockProcessingResult.FailedDeleteBlocks -> {
                        Twig.error {
                            "Failed to delete temporary blocks files from the device disk. It will be retried on the" +
                                " next time, while downloading new blocks."
                        }
                        checkErrorResult(result.failedAtHeight)
                    }

                    is BlockProcessingResult.FailedDownloadBlocks -> {
                        Twig.error { "Failed while downloading blocks at height: ${result.failedAtHeight}" }
                        checkErrorResult(result.failedAtHeight)
                    }

                    is BlockProcessingResult.FailedValidateBlocks -> {
                        Twig.error { "Failed while validating blocks at height: ${result.failedAtHeight}" }
                        checkErrorResult(result.failedAtHeight)
                    }

                    is BlockProcessingResult.FailedScanBlocks -> {
                        Twig.error { "Failed while scanning blocks at height: ${result.failedAtHeight}" }
                        checkErrorResult(result.failedAtHeight)
                    }

                    is BlockProcessingResult.Success -> {
                        // Do nothing.
                    }

                    is BlockProcessingResult.DownloadSuccess -> {
                        // Do nothing. Syncing of blocks is in progress.
                    }

                    BlockProcessingResult.UpdateBirthday -> {
                        // Do nothing. The birthday was just updated.
                    }
                }
            }
        } while (_state.value !is State.Stopped)
        Twig.debug { "processor complete" }
        stop()
    }

    suspend fun checkErrorResult(failedHeight: BlockHeight) {
        if (consecutiveChainErrors.get() >= RETRIES) {
            val errorMessage = "ERROR: unable to resolve reorg at height $failedHeight after " +
                "${consecutiveChainErrors.get()} correction attempts!"
            fail(CompactBlockProcessorException.FailedReorgRepair(errorMessage))
        } else {
            handleChainError(failedHeight)
        }
        consecutiveChainErrors.getAndIncrement()
    }

    /**
     * Sets the state to [State.Stopped], which causes the processor loop to exit.
     */
    suspend fun stop() {
        runCatching {
            setState(State.Stopped)
            downloader.stop()
        }
    }

    /**
     * Stop processing and throw an error.
     */
    private suspend fun fail(error: Throwable) {
        stop()
        Twig.debug { "${error.message}" }
        throw error
    }

    private suspend fun processNewBlocks(): BlockProcessingResult {
        Twig.debug { "Beginning to process new blocks (with lower bound: $lowerBoundHeight)..." }

        return if (!updateRanges()) {
            Twig.debug { "Disconnection detected! Attempting to reconnect!" }
            setState(State.Disconnected)
            downloader.lightWalletClient.reconnect()
            BlockProcessingResult.Reconnecting
        } else if (_processorInfo.value.lastSyncRange.isNullOrEmpty()) {
            setState(State.Synced(_processorInfo.value.lastSyncRange))
            BlockProcessingResult.NoBlocksToProcess
        } else {
            val syncRange = if (BenchmarkingExt.isBenchmarking()) {
                // We inject a benchmark test blocks range at this point to process only a restricted range of
                // blocks for a more reliable benchmark results.
                val benchmarkBlockRange = BenchmarkingBlockRangeFixture.new().let {
                    // Convert range of Longs to range of BlockHeights
                    BlockHeight.new(PirateNetwork.Mainnet, it.start)..(
                        BlockHeight.new(PirateNetwork.Mainnet, it.endInclusive)
                        )
                }
                benchmarkBlockRange
            } else {
                _processorInfo.value.lastSyncRange!!
            }

            syncBlocksAndEnhanceTransactions(
                syncRange = syncRange,
                withDownload = true,
                enhanceStartHeight = _processorInfo.value.firstUnenhancedHeight
            )
        }
    }

    @Suppress("ReturnCount")
    private suspend fun syncBlocksAndEnhanceTransactions(
        syncRange: ClosedRange<BlockHeight>,
        withDownload: Boolean,
        enhanceStartHeight: BlockHeight?
    ): BlockProcessingResult {
        _state.value = State.Syncing

        // Syncing last blocks and enhancing transactions
        var syncResult: BlockProcessingResult = BlockProcessingResult.Success
        runSyncingAndEnhancing(
            backend = backend,
            downloader = downloader,
            repository = repository,
            network = network,
            syncRange = syncRange,
            withDownload = withDownload,
            enhanceStartHeight = enhanceStartHeight
        ).collect { syncProgress ->
            _progress.value = syncProgress.percentage
            updateProgress(lastSyncedHeight = syncProgress.lastSyncedHeight)

            if (syncProgress.result == BlockProcessingResult.UpdateBirthday) {
                updateBirthdayHeight()
            } else if (syncProgress.result != BlockProcessingResult.Success) {
                syncResult = syncProgress.result
                return@collect
            }
        }

        if (syncResult != BlockProcessingResult.Success) {
            // Remove persisted but not validated and scanned blocks in case of any failure
            val lastScannedHeight = getLastScannedHeight(repository)
            downloader.rewindToHeight(lastScannedHeight)
            deleteAllBlockFiles(
                downloader = downloader,
                lastKnownHeight = lastScannedHeight
            )

            return syncResult
        }

        return BlockProcessingResult.Success
    }

    sealed class BlockProcessingResult {
        object NoBlocksToProcess : BlockProcessingResult()
        object Success : BlockProcessingResult()
        data class DownloadSuccess(val downloadedBlocks: List<JniBlockMeta>?) : BlockProcessingResult()
        object UpdateBirthday : BlockProcessingResult()
        object Reconnecting : BlockProcessingResult()
        data class FailedDownloadBlocks(val failedAtHeight: BlockHeight) : BlockProcessingResult()
        data class FailedScanBlocks(val failedAtHeight: BlockHeight) : BlockProcessingResult()
        data class FailedValidateBlocks(val failedAtHeight: BlockHeight) : BlockProcessingResult()
        data class FailedDeleteBlocks(val failedAtHeight: BlockHeight) : BlockProcessingResult()
        data class FailedEnhance(val error: CompactBlockProcessorException.EnhanceTransactionError) :
            BlockProcessingResult()
    }

    /**
     * Gets the latest range info and then uses that initialInfo to update (and transmit)
     * the scan/download ranges that require processing.
     *
     * @return true when the update succeeds.
     */
    private suspend fun updateRanges(): Boolean {
        // This fetches the latest height each time this method is called, which can be very inefficient
        // when downloading all of the blocks from the server
        val networkBlockHeight = run {
            val networkBlockHeightUnsafe =
                when (val response = downloader.getLatestBlockHeight()) {
                    is Response.Success -> response.result
                    else -> null
                }

            runCatching { networkBlockHeightUnsafe?.toBlockHeight(network) }.getOrNull()
        } ?: return false

        // If we find out that we previously downloaded, but not validated and scanned persisted blocks, we need
        // to rewind the blocks above the last scanned height first.
        val lastScannedHeight = getLastScannedHeight(repository)
        val lastDownloadedHeight = getLastDownloadedHeight(downloader).let {
            BlockHeight.new(
                network,
                max(
                    it?.value ?: 0,
                    lowerBoundHeight.value
                )
            )
        }
        val lastSyncedHeight = if (lastDownloadedHeight.value - lastScannedHeight.value > 0) {
            Twig.verbose {
                "Clearing blocks of last persisted batch within the last scanned height " +
                    "$lastScannedHeight and last download height $lastDownloadedHeight, as all these blocks " +
                    "possibly haven't been validated and scanned in the previous blocks sync attempt."
            }
            downloader.rewindToHeight(lastScannedHeight)
            lastScannedHeight
        } else {
            lastDownloadedHeight
        }

        // Get the first un-enhanced transaction from the repository
        val firstUnenhancedHeight = getFirstUnenhancedHeight(repository)

        updateProgress(
            networkBlockHeight = networkBlockHeight,
            lastSyncedHeight = lastSyncedHeight,
            lastSyncRange = lastSyncedHeight + 1..networkBlockHeight,
            firstUnenhancedHeight = firstUnenhancedHeight
        )

        return true
    }

    /**
     * Confirm that the wallet data is properly setup for use.
     */
    // Need to refactor this to be less ugly and more testable
    @Suppress("NestedBlockDepth")
    private suspend fun verifySetup() {
        // verify that the data is initialized
        val error = if (!repository.isInitialized()) {
            CompactBlockProcessorException.Uninitialized
        } else if (repository.getAccountCount() == 0) {
            CompactBlockProcessorException.NoAccount
        } else {
            // verify that the server is correct

            // How do we handle network connection issues?

            downloader.getServerInfo()?.let { info ->
                val serverBlockHeight =
                    runCatching { info.blockHeightUnsafe.toBlockHeight(network) }.getOrNull()

                if (null == serverBlockHeight) {
                    // Note: we could better signal network connection issue
                    CompactBlockProcessorException.BadBlockHeight(info.blockHeightUnsafe)
                } else {
                    val clientBranch = "%x".format(
                        Locale.ROOT,
                        backend.getBranchIdForHeight(serverBlockHeight)
                    )
                    val network = backend.network.networkName

                    if (!clientBranch.equals(info.consensusBranchId, true)) {
                        MismatchedNetwork(
                            clientNetwork = network,
                            serverNetwork = info.chainName
                        )
                    } else if (!info.matchingNetwork(network)) {
                        MismatchedNetwork(
                            clientNetwork = network,
                            serverNetwork = info.chainName
                        )
                    } else {
                        null
                    }
                }
            }
        }

        if (error != null) {
            Twig.debug { "Validating setup prior to scanning . . . ISSUE FOUND! - ${error.javaClass.simpleName}" }
            // give listener a chance to override
            if (onSetupErrorListener?.invoke(error) != true) {
                throw error
            } else {
                Twig.debug {
                    "Warning: An ${error::class.java.simpleName} was encountered while verifying setup but " +
                        "it was ignored by the onSetupErrorHandler. Ignoring message: ${error.message}"
                }
            }
        }
    }

    private suspend fun updateBirthdayHeight() {
        val betterBirthday = calculateBirthdayHeight()
        if (betterBirthday > birthdayHeight) {
            Twig.debug { "Better birthday found! Birthday height updated from $birthdayHeight to $betterBirthday" }
            _birthdayHeight.value = betterBirthday
        }
    }

    var failedUtxoFetches = 0

    @Suppress("MagicNumber", "LongMethod")
    internal suspend fun refreshUtxos(account: Account, startHeight: BlockHeight): Int {
        Twig.debug { "Checking for UTXOs above height $startHeight" }
        var count = 0
        // TODO [#683]: cleanup the way that we prevent this from running excessively
        //       For now, try for about 3 blocks per app launch. If the service fails it is
        //       probably disabled on ligthtwalletd, so then stop trying until the next app launch.
        // TODO [#683]: https://github.com/zcash/zcash-android-wallet-sdk/issues/683
        if (failedUtxoFetches < 9) { // there are 3 attempts per block
            @Suppress("TooGenericExceptionCaught")
            try {
                retryUpTo(UTXO_FETCH_RETRIES) {
                    val tAddresses = backend.listTransparentReceivers(account)

                    downloader.lightWalletClient.fetchUtxos(
                        tAddresses,
                        BlockHeightUnsafe.from(startHeight)
                    ).onEach { response ->
                        when (response) {
                            is Response.Success -> {
                                Twig.verbose { "Downloading UTXO at height: ${response.result.height} succeeded." }
                            }

                            is Response.Failure -> {
                                Twig.warn {
                                    "Downloading UTXO from height:" +
                                        " $startHeight failed with: ${response.description}."
                                }
                                throw LightWalletException.FetchUtxosException(
                                    response.code,
                                    response.description,
                                    response.toThrowable()
                                )
                            }
                        }
                    }
                        .filterIsInstance<Response.Success<GetAddressUtxosReplyUnsafe>>()
                        .map { response ->
                            response.result
                        }
                        .onCompletion {
                            if (it != null) {
                                Twig.debug { "UTXOs from height $startHeight failed to download with: $it" }
                            } else {
                                Twig.debug { "All UTXOs from height $startHeight fetched successfully" }
                            }
                        }.collect { utxo ->
                            Twig.verbose { "Fetched UTXO at height: ${utxo.height}" }
                            val processResult = processUtxoResult(utxo)
                            if (processResult) {
                                count++
                            }
                        }
                }
            } catch (e: Throwable) {
                failedUtxoFetches++
                Twig.debug {
                    "Warning: Fetching UTXOs is repeatedly failing! We will only try about " +
                        "${(9 - failedUtxoFetches + 2) / 3} more times then give up for this session. " +
                        "Exception message: ${e.message}, caused by: ${e.cause}."
                }
            }
        } else {
            Twig.debug {
                "Warning: gave up on fetching UTXOs for this session. It seems to unavailable on " +
                    "lightwalletd."
            }
        }

        return count
    }

    /**
     * @return True in case of the UTXO processed successfully, false otherwise
     */
    internal suspend fun processUtxoResult(utxo: GetAddressUtxosReplyUnsafe): Boolean {
        // Note (str4d): We no longer clear UTXOs here, as rustBackend.putUtxo now uses an upsert instead of an insert.
        //  This means that now-spent UTXOs would previously have been deleted, but now are left in the database (like
        //  shielded notes). Due to the fact that the lightwalletd query only returns _current_ UTXOs, we don't learn
        //  about recently-spent UTXOs here, so the transparent balance does not get updated here. Instead, when a
        //  received shielded note is "enhanced" by downloading the full transaction, we mark any UTXOs spent in that
        //  transaction as spent in the database. This relies on two current properties: UTXOs are only ever spent in
        //  shielding transactions, and at least one shielded note from each shielding transaction is always enhanced.
        //  However, for greater reliability, we may want to alter the Data Access API to support "inferring spentness"
        //  from what is _not_ returned as a UTXO, or alternatively fetch TXOs from lightwalletd instead of just UTXOs.
        Twig.debug { "Found UTXO at height ${utxo.height.toInt()} with ${utxo.valueZat} zatoshi" }
        @Suppress("TooGenericExceptionCaught")
        return try {
            backend.putUtxo(
                utxo.address,
                utxo.txid,
                utxo.index,
                utxo.script,
                utxo.valueZat,
                utxo.height
            )
            true
        } catch (t: Throwable) {
            Twig.debug {
                "Warning: Ignoring transaction at height ${utxo.height} @ index ${utxo.index} because " +
                    "it already exists. Exception message: ${t.message}, caused by: ${t.cause}."
            }
            // TODO [#683]: more accurately track the utxos that were skipped (in theory, this could fail for other
            //  reasons)
            // TODO [#683]: https://github.com/zcash/zcash-android-wallet-sdk/issues/683
            false
        }
    }

    companion object {
        /**
         * Default attempts at retrying.
         */
        internal const val RETRIES = 5

        /**
         * Transaction fetching default attempts at retrying.
         */
        internal const val TRANSACTION_FETCH_RETRIES = 1

        /**
         * UTXOs fetching default attempts at retrying.
         */
        internal const val UTXO_FETCH_RETRIES = 3

        /**
         * The theoretical maximum number of blocks in a reorg, due to other bottlenecks in the protocol design.
         */
        internal const val MAX_REORG_SIZE = 100

        /**
         * Default size of batches of blocks to request from the compact block service. Then it's also used as a default
         * size of batches of blocks to validate and scan via librustzcash. For scanning action applies this - the
         * smaller this number the more granular information can be provided about scan state. Unfortunately, it may
         * also lead to a lot of overhead during scanning.
         */
        internal const val SYNC_BATCH_SIZE = 10

        /**
         * Default size of batch of blocks for running the transaction enhancing.
         */
        internal const val ENHANCE_BATCH_SIZE = 1000

        /**
         * Default number of blocks to rewind when a chain reorg is detected. This should be large enough to recover
         * from the reorg but smaller than the theoretical max reorg size of 100.
         */
        internal const val REWIND_DISTANCE = 10

        /**
         * Requests, processes and persists all blocks from the given range.
         *
         * @param rustBackend the Rust backend component
         * @param downloader the compact block downloader component
         * @param repository the derived data repository component
         * @param network the network in which the sync mechanism operates
         * @param syncRange the range of blocks to download
         * @param withDownload the flag indicating whether the blocks should also be downloaded and processed, or
         * processed existing blocks
         * @param enhanceStartHeight the height in which the enhancing should start, or null in case of no previous
         * transaction enhancing done yet

         * @return Flow of BatchSyncProgress sync and enhancement results
         */
        @VisibleForTesting
        @Suppress("LongParameterList", "LongMethod")
        internal suspend fun runSyncingAndEnhancing(
            backend: Backend,
            downloader: CompactBlockDownloader,
            repository: DerivedDataRepository,
            network: PirateNetwork,
            syncRange: ClosedRange<BlockHeight>,
            withDownload: Boolean,
            enhanceStartHeight: BlockHeight?,
        ): Flow<BatchSyncProgress> = flow {
            if (syncRange.isEmpty()) {
                Twig.debug { "No blocks to sync" }
                emit(
                    BatchSyncProgress(
                        percentage = PercentDecimal.ONE_HUNDRED_PERCENT,
                        lastSyncedHeight = getLastScannedHeight(repository),
                        result = BlockProcessingResult.Success
                    )
                )
            } else {
                Twig.debug { "Syncing blocks in range $syncRange" }

                val batches = getBatchedBlockList(syncRange, network)

                // Check for the last enhanced height and eventually set is as the beginning of the next enhancing range
                var enhancingRange = if (enhanceStartHeight != null) {
                    BlockHeight(min(syncRange.start.value, enhanceStartHeight.value))..syncRange.start
                } else {
                    syncRange.start..syncRange.start
                }

                batches.asFlow().map {
                    Twig.debug { "Syncing process starts for batch: $it" }

                    // Run downloading stage
                    SyncStageResult(
                        batch = it,
                        stageResult = if (withDownload) {
                            downloadBatchOfBlocks(
                                downloader = downloader,
                                batch = it
                            )
                        } else {
                            BlockProcessingResult.DownloadSuccess(null)
                        }
                    )
                }.buffer(1).map { downloadStageResult ->
                    Twig.debug { "Download stage done with result: $downloadStageResult" }

                    if (downloadStageResult.stageResult !is BlockProcessingResult.DownloadSuccess) {
                        // In case of any failure, we just propagate the result
                        downloadStageResult
                    } else {
                        // Enrich batch model with fetched blocks. It's useful for later blocks deletion
                        downloadStageResult.batch.blocks = downloadStageResult.stageResult.downloadedBlocks

                        // Run validation stage
                        SyncStageResult(
                            downloadStageResult.batch,
                            validateBatchOfBlocks(
                                backend = backend,
                                batch = downloadStageResult.batch
                            )
                        )
                    }
                }.map { validateResult ->
                    Twig.debug { "Validation stage done with result: $validateResult" }

                    if (validateResult.stageResult != BlockProcessingResult.Success) {
                        validateResult
                    } else {
                        // Run scanning stage
                        SyncStageResult(
                            validateResult.batch,
                            scanBatchOfBlocks(
                                backend = backend,
                                batch = validateResult.batch
                            )
                        )
                    }
                }.map { scanResult ->
                    Twig.debug { "Scan stage done with result: $scanResult" }

                    if (scanResult.stageResult != BlockProcessingResult.Success) {
                        scanResult
                    } else {
                        // Run deletion stage
                        SyncStageResult(
                            scanResult.batch,
                            deleteFilesOfBatchOfBlocks(
                                downloader = downloader,
                                batch = scanResult.batch
                            )
                        )
                    }
                }.onEach { continuousResult ->
                    Twig.debug { "Deletion stage done with result: $continuousResult" }

                    emit(
                        BatchSyncProgress(
                            percentage = PercentDecimal(continuousResult.batch.order / batches.size.toFloat()),
                            lastSyncedHeight = getLastScannedHeight(repository),
                            result = continuousResult.stageResult
                        )
                    )

                    // Increment and compare the range for triggering the enhancing
                    enhancingRange = enhancingRange.start..continuousResult.batch.range.endInclusive

                    // Enhance is run in case of the range is on or over its limit, or in case of any failure
                    // state comes from the previous stages, or if the end of the sync range is reached
                    if (enhancingRange.length() >= ENHANCE_BATCH_SIZE ||
                        continuousResult.stageResult != BlockProcessingResult.Success ||
                        continuousResult.batch.order == batches.size.toLong()
                    ) {
                        // Copy the range for use and reset for the next iteration
                        val currentEnhancingRange = enhancingRange
                        enhancingRange = enhancingRange.endInclusive..enhancingRange.endInclusive
                        enhanceTransactionDetails(
                            range = currentEnhancingRange,
                            repository = repository,
                            rustBackend = backend,
                            downloader = downloader
                        ).collect { enhancingResult ->
                            Twig.debug { "Enhancing result: $enhancingResult" }
                            // TODO [#1047]: CompactBlockProcessor: Consider a separate sub-stage result handling
                            // TODO [#1047]: https://github.com/zcash/zcash-android-wallet-sdk/issues/1047
                            when (enhancingResult) {
                                is BlockProcessingResult.UpdateBirthday -> {
                                    Twig.debug { "Birthday height update reporting" }
                                }
                                is BlockProcessingResult.FailedEnhance -> {
                                    Twig.error { "Enhancing failed for: $enhancingRange with $enhancingResult" }
                                }
                                else -> {
                                    // Transactions enhanced correctly
                                }
                            }
                            emit(
                                BatchSyncProgress(
                                    percentage = PercentDecimal(continuousResult.batch.order / batches.size.toFloat()),
                                    lastSyncedHeight = getLastScannedHeight(repository),
                                    result = enhancingResult
                                )
                            )
                        }
                    }

                    Twig.debug { "All sync stages done for the batch: ${continuousResult.batch}" }
                }.takeWhile { batchProcessResult ->
                    batchProcessResult.stageResult == BlockProcessingResult.Success ||
                        batchProcessResult.stageResult == BlockProcessingResult.UpdateBirthday
                }.collect()
            }
        }

        private fun getBatchedBlockList(
            syncRange: ClosedRange<BlockHeight>,
            network: PirateNetwork
        ): List<BlockBatch> {
            val missingBlockCount = syncRange.endInclusive.value - syncRange.start.value + 1
            val batchCount = (
                missingBlockCount / SYNC_BATCH_SIZE +
                    (if (missingBlockCount.rem(SYNC_BATCH_SIZE) == 0L) 0 else 1)
                )

            Twig.debug {
                "Found $missingBlockCount missing blocks, syncing in $batchCount batches of $SYNC_BATCH_SIZE..."
            }

            var start = syncRange.start
            return buildList {
                for (index in 1..batchCount) {
                    val end = BlockHeight.new(
                        network,
                        min(
                            (syncRange.start.value + (index * SYNC_BATCH_SIZE)) - 1,
                            syncRange.endInclusive.value
                        )
                    ) // subtract 1 on the first value because the range is inclusive

                    add(BlockBatch(index, start..end))
                    start = end + 1
                }
            }
        }

        /**
         * Request and download all blocks in the given range and persist them locally for processing, later.
         *
         * @param batch the batch of blocks to download.
         */
        @VisibleForTesting
        @Throws(CompactBlockProcessorException.FailedDownload::class)
        @Suppress("MagicNumber")
        internal suspend fun downloadBatchOfBlocks(
            downloader: CompactBlockDownloader,
            batch: BlockBatch
        ): BlockProcessingResult {
            var downloadedBlocks = listOf<JniBlockMeta>()
            retryUpTo(RETRIES, { CompactBlockProcessorException.FailedDownload(it) }) { failedAttempts ->
                if (failedAttempts == 0) {
                    Twig.verbose { "Starting to download batch $batch" }
                } else {
                    Twig.warn { "Retrying to download batch $batch after $failedAttempts failure(s)..." }
                }

                downloadedBlocks = downloader.downloadBlockRange(batch.range)
            }
            Twig.verbose { "Successfully downloaded batch: $batch of $downloadedBlocks blocks" }

            return if (downloadedBlocks.isNotEmpty()) {
                BlockProcessingResult.DownloadSuccess(downloadedBlocks)
            } else {
                BlockProcessingResult.FailedDownloadBlocks(batch.range.start)
            }
        }

        @VisibleForTesting
        internal suspend fun validateBatchOfBlocks(batch: BlockBatch, backend: Backend): BlockProcessingResult {
            Twig.verbose { "Starting to validate batch $batch" }

            val result = backend.validateCombinedChainOrErrorBlockHeight(batch.range.length())

            return if (null == result) {
                Twig.verbose { "Successfully validated batch $batch" }
                BlockProcessingResult.Success
            } else {
                BlockProcessingResult.FailedValidateBlocks(result)
            }
        }

        @VisibleForTesting
        internal suspend fun scanBatchOfBlocks(batch: BlockBatch, backend: Backend): BlockProcessingResult {
            return runCatching {
                backend.scanBlocks(batch.range.length())
            }.onSuccess {
                Twig.verbose { "Successfully scanned batch $batch" }
            }.onFailure {
                Twig.error { "Failed while scanning batch $batch with $it" }
            }.fold(
                onSuccess = { BlockProcessingResult.Success },
                onFailure = { BlockProcessingResult.FailedScanBlocks(batch.range.start) }
            )
        }

        @VisibleForTesting
        internal suspend fun deleteAllBlockFiles(
            downloader: CompactBlockDownloader,
            lastKnownHeight: BlockHeight
        ): BlockProcessingResult {
            Twig.verbose { "Starting to delete all temporary block files" }
            return if (downloader.compactBlockRepository.deleteAllCompactBlockFiles()) {
                Twig.verbose { "Successfully deleted all temporary block files" }
                BlockProcessingResult.Success
            } else {
                BlockProcessingResult.FailedDeleteBlocks(lastKnownHeight)
            }
        }

        @VisibleForTesting
        internal suspend fun deleteFilesOfBatchOfBlocks(
            batch: BlockBatch,
            downloader: CompactBlockDownloader
        ): BlockProcessingResult {
            Twig.verbose { "Starting to delete temporary block files from batch: $batch" }

            return batch.blocks?.let { blocks ->
                val deleted = downloader.compactBlockRepository.deleteCompactBlockFiles(blocks)
                if (deleted) {
                    Twig.verbose { "Successfully deleted all temporary batched block files" }
                    BlockProcessingResult.Success
                } else {
                    BlockProcessingResult.FailedDeleteBlocks(batch.range.start)
                }
            } ?: BlockProcessingResult.Success
        }

        @VisibleForTesting
        internal suspend fun enhanceTransactionDetails(
            range: ClosedRange<BlockHeight>,
            repository: DerivedDataRepository,
            rustBackend: Backend,
            downloader: CompactBlockDownloader
        ): Flow<BlockProcessingResult> = flow {
            Twig.debug { "Enhancing transaction details for blocks $range" }

            val newTxs = repository.findNewTransactions(range)
            if (newTxs.isEmpty()) {
                Twig.debug { "No new transactions found in $range" }
            } else {
                Twig.debug { "Enhancing ${newTxs.size} transaction(s)!" }

                // If the first transaction has been added
                if (newTxs.size.toLong() == repository.getTransactionCount()) {
                    Twig.debug { "Encountered the first transaction. This changes the birthday height!" }
                    emit(BlockProcessingResult.UpdateBirthday)
                }

                newTxs.filter { it.minedHeight != null }.onEach { newTransaction ->
                    val trEnhanceResult = enhanceTransaction(newTransaction, rustBackend, downloader)
                    if (trEnhanceResult is BlockProcessingResult.FailedEnhance) {
                        Twig.error { "Encountered transaction enhancing error: ${trEnhanceResult.error}" }
                        emit(trEnhanceResult)
                        // We intentionally do not terminate the batch enhancing here, just reporting it
                    }
                }
            }

            Twig.debug { "Done enhancing transaction details" }
            emit(BlockProcessingResult.Success)
        }

        private suspend fun enhanceTransaction(
            transaction: DbTransactionOverview,
            backend: Backend,
            downloader: CompactBlockDownloader
        ): BlockProcessingResult {
            Twig.debug { "Starting enhancing transaction (id:${transaction.id}  block:${transaction.minedHeight})" }
            if (transaction.minedHeight == null) {
                return BlockProcessingResult.Success
            }

            return try {
                // Fetching transaction is done with retries to eliminate a bad network condition
                Twig.verbose { "Fetching transaction (id:${transaction.id}  block:${transaction.minedHeight})" }
                val transactionData = fetchTransaction(
                    id = transaction.id,
                    rawTransactionId = transaction.rawId.byteArray,
                    minedHeight = transaction.minedHeight,
                    downloader = downloader
                )

                // Decrypting and storing transaction is run just once, since we consider it more stable
                Twig.verbose {
                    "Decrypting and storing transaction " +
                        "(id:${transaction.id}  block:${transaction.minedHeight})"
                }
                decryptTransaction(
                    transactionData = transactionData,
                    minedHeight = transaction.minedHeight,
                    backend = backend
                )

                Twig.debug { "Done enhancing transaction (id:${transaction.id} block:${transaction.minedHeight})" }
                BlockProcessingResult.Success
            } catch (e: CompactBlockProcessorException.EnhanceTransactionError) {
                BlockProcessingResult.FailedEnhance(e)
            }
        }

        @Throws(EnhanceTxDownloadError::class)
        private suspend fun fetchTransaction(
            id: Long,
            rawTransactionId: ByteArray,
            minedHeight: BlockHeight,
            downloader: CompactBlockDownloader
        ): ByteArray {
            var transactionDataResult: ByteArray? = null
            retryUpTo(TRANSACTION_FETCH_RETRIES) { failedAttempts ->
                if (failedAttempts == 0) {
                    Twig.debug { "Starting to fetch transaction (id:$id, block:$minedHeight)" }
                } else {
                    Twig.warn {
                        "Retrying to fetch transaction (id:$id, block:$minedHeight) after $failedAttempts " +
                            "failure(s)..."
                    }
                }
                when (val response = downloader.fetchTransaction(rawTransactionId)) {
                    is Response.Success -> {
                        transactionDataResult = response.result.data
                    }
                    is Response.Failure -> {
                        throw EnhanceTxDownloadError(minedHeight, response.toThrowable())
                    }
                }
            }
            // Result is fetched or EnhanceTxDownloadError is thrown after all attempts failed at this point
            return transactionDataResult!!
        }

        @Throws(EnhanceTxDecryptError::class)
        private suspend fun decryptTransaction(
            transactionData: ByteArray,
            minedHeight: BlockHeight,
            backend: Backend,
        ) {
            runCatching {
                backend.decryptAndStoreTransaction(transactionData)
            }.onFailure {
                throw EnhanceTxDecryptError(minedHeight, it)
            }
        }

        /**
         * Get the height of the last block that was scanned by this processor.
         *
         * @return the last scanned height reported by the repository.
         */
        @VisibleForTesting
        internal suspend fun getLastScannedHeight(repository: DerivedDataRepository) =
            repository.lastScannedHeight()

        /**
         * Get the height of the first un-enhanced transaction detail from the repository.
         *
         * @return the oldest transaction which hasn't been enhanced yet, or null in case of all transaction enhanced
         * or repository is empty
         */
        @VisibleForTesting
        internal suspend fun getFirstUnenhancedHeight(repository: DerivedDataRepository) =
            repository.firstUnenhancedHeight()

        /**
         * Get the height of the last block that was downloaded by this processor.
         *
         * @return the last downloaded height reported by the downloader.
         */
        internal suspend fun getLastDownloadedHeight(downloader: CompactBlockDownloader) =
            downloader.getLastDownloadedHeight()

        // CompactBlockProcessor is the wrong place for this, but it's where all the other APIs that need
        //  access to the RustBackend live. This should be refactored.
        internal suspend fun createAccount(rustBackend: Backend, seed: ByteArray): UnifiedSpendingKey =
            rustBackend.createAccountAndGetSpendingKey(seed)

        /**
         * Get the current unified address for the given wallet account.
         *
         * @return the current unified address of this account.
         */
        internal suspend fun getCurrentAddress(rustBackend: Backend, account: Account) =
            rustBackend.getCurrentAddress(account)

        /**
         * Get the legacy Sapling address corresponding to the current unified address for the given wallet account.
         *
         * @return a Sapling address.
         */
        internal suspend fun getLegacySaplingAddress(rustBackend: Backend, account: Account) =
            rustBackend.getSaplingReceiver(
                rustBackend.getCurrentAddress(account)
            )
                ?: throw InitializeException.MissingAddressException("legacy Sapling")

        /**
         * Get the legacy transparent address corresponding to the current unified address for the given wallet account.
         *
         * @return a transparent address.
         */
        internal suspend fun getTransparentAddress(rustBackend: Backend, account: Account) =
            rustBackend.getTransparentReceiver(
                rustBackend.getCurrentAddress(account)
            )
                ?: throw InitializeException.MissingAddressException("legacy transparent")
    }

    /**
     * Emit an instance of processorInfo, corresponding to the provided data.
     *
     * @param networkBlockHeight the latest block available to lightwalletd that may or may not be
     * downloaded by this wallet yet.
     * @param lastSyncedHeight the height up to which the wallet last synced. This determines
     * where the next sync will begin.
     * @param lastSyncRange the inclusive range to sync. This represents what we most recently
     * wanted to sync. In most cases, it will be an invalid range because we'd like to sync blocks
     * that we don't yet have.
     * @param firstUnenhancedHeight the height at which the enhancing should start. Use null if you have no
     * preferences. The height will be calculated automatically for you to continue where it previously ended, or
     * it'll be set to the sync start height in case of the first sync attempt.
     */
    private fun updateProgress(
        networkBlockHeight: BlockHeight? = _processorInfo.value.networkBlockHeight,
        lastSyncedHeight: BlockHeight? = _processorInfo.value.lastSyncedHeight,
        lastSyncRange: ClosedRange<BlockHeight>? = _processorInfo.value.lastSyncRange,
        firstUnenhancedHeight: BlockHeight? = _processorInfo.value.firstUnenhancedHeight,
    ) {
        _networkHeight.value = networkBlockHeight
        _processorInfo.value = ProcessorInfo(
            networkBlockHeight = networkBlockHeight,
            lastSyncedHeight = lastSyncedHeight,
            lastSyncRange = lastSyncRange,
            firstUnenhancedHeight = firstUnenhancedHeight
        )
    }

    private suspend fun handleChainError(errorHeight: BlockHeight) {
        // TODO [#683]: consider an error object containing hash information
        // TODO [#683]: https://github.com/zcash/zcash-android-wallet-sdk/issues/683
        printValidationErrorInfo(errorHeight)
        determineLowerBound(errorHeight).let { lowerBound ->
            Twig.debug { "handling chain error at $errorHeight by rewinding to block $lowerBound" }
            onChainErrorListener?.invoke(errorHeight, lowerBound)
            rewindToNearestHeight(lowerBound, true)
        }
    }

    suspend fun getNearestRewindHeight(height: BlockHeight): BlockHeight {
        // TODO [#683]: add a concept of original checkpoint height to the processor. For now, derive it
        //  add one because we already have the checkpoint. Add one again because we delete ABOVE the block
        // TODO [#683]: https://github.com/zcash/zcash-android-wallet-sdk/issues/683
        val originalCheckpoint = lowerBoundHeight + MAX_REORG_SIZE + 2
        return if (height < originalCheckpoint) {
            originalCheckpoint
        } else {
            // tricky: subtract one because we delete ABOVE this block
            // This could create an invalid height if height was saplingActivationHeight
            val rewindHeight = BlockHeight(height.value - 1)
            backend.getNearestRewindHeight(rewindHeight)
        }
    }

    /**
     * Rewind back at least two weeks worth of blocks.
     */
    suspend fun quickRewind() {
        val height = max(_processorInfo.value.lastSyncedHeight, repository.lastScannedHeight())
        val blocksPer14Days = 14.days.inWholeMilliseconds / PirateSdk.BLOCK_INTERVAL_MILLIS.toInt()
        val twoWeeksBack = BlockHeight.new(
            network,
            (height.value - blocksPer14Days).coerceAtLeast(lowerBoundHeight.value)
        )
        rewindToNearestHeight(twoWeeksBack, false)
    }

    /**
     * @param alsoClearBlockCache when true, also clear the block cache which forces a redownload of
     * blocks. Otherwise, the cached blocks will be used in the rescan, which in most cases, is fine.
     */
    @Suppress("LongMethod")
    suspend fun rewindToNearestHeight(
        height: BlockHeight,
        alsoClearBlockCache: Boolean = false
    ) {
        processingMutex.withLockLogged("rewindToHeight") {
            val lastSyncedHeight = _processorInfo.value.lastSyncedHeight
            val lastLocalBlock = repository.lastScannedHeight()
            val targetHeight = getNearestRewindHeight(height)

            Twig.debug {
                "Rewinding from $lastSyncedHeight to requested height: $height using target height: " +
                    "$targetHeight with last local block: $lastLocalBlock"
            }

            if (null == lastSyncedHeight && targetHeight < lastLocalBlock) {
                Twig.debug { "Rewinding because targetHeight is less than lastLocalBlock." }
                runCatching {
                    backend.rewindToHeight(targetHeight)
                }.onFailure {
                    Twig.error { "Rewinding to the targetHeight $targetHeight failed with $it" }
                }
            } else if (null != lastSyncedHeight && targetHeight < lastSyncedHeight) {
                Twig.debug { "Rewinding because targetHeight is less than lastSyncedHeight." }
                runCatching {
                    backend.rewindToHeight(targetHeight)
                }.onFailure {
                    Twig.error { "Rewinding to the targetHeight $targetHeight failed with $it" }
                }
            } else {
                Twig.debug {
                    "Not rewinding dataDb because the last synced height is $lastSyncedHeight and the" +
                        " last local block is $lastLocalBlock both of which are less than the target height of " +
                        "$targetHeight"
                }
            }

            val currentNetworkBlockHeight = _processorInfo.value.networkBlockHeight

            if (alsoClearBlockCache) {
                Twig.debug {
                    "Also clearing block cache back to $targetHeight. These rewound blocks will download " +
                        "in the next scheduled scan"
                }
                downloader.rewindToHeight(targetHeight)
                // communicate that the wallet is no longer synced because it might remain this way for 20+ second
                // because we only download on 20s time boundaries so we can't trigger any immediate action
                setState(State.Syncing)
                if (null == currentNetworkBlockHeight) {
                    updateProgress(
                        lastSyncedHeight = targetHeight,
                        lastSyncRange = null
                    )
                } else {
                    updateProgress(
                        lastSyncedHeight = targetHeight,
                        lastSyncRange = (targetHeight + 1)..currentNetworkBlockHeight
                    )
                }
                _progress.value = PercentDecimal.ZERO_PERCENT
            } else {
                if (null == currentNetworkBlockHeight) {
                    updateProgress(
                        lastSyncedHeight = targetHeight,
                        lastSyncRange = null
                    )
                } else {
                    updateProgress(
                        lastSyncedHeight = targetHeight,
                        lastSyncRange = (targetHeight + 1)..currentNetworkBlockHeight
                    )
                }

                _progress.value = PercentDecimal.ZERO_PERCENT

                if (null != lastSyncedHeight) {
                    val range = (targetHeight + 1)..lastSyncedHeight
                    Twig.debug {
                        "We kept the cache blocks in place so we don't need to wait for the next " +
                            "scheduled download to rescan. Instead we will rescan and validate blocks " +
                            "${range.start}..${range.endInclusive}"
                    }

                    syncBlocksAndEnhanceTransactions(
                        syncRange = range,
                        withDownload = false,
                        enhanceStartHeight = null
                    )
                }
            }
        }
    }

    /** insightful function for debugging these critical errors */
    private suspend fun printValidationErrorInfo(errorHeight: BlockHeight, count: Int = 11) {
        // Note: blocks are public information so it's okay to print them but, still, let's not unless we're
        // debugging something
        if (!BuildConfig.DEBUG) {
            return
        }

        var errorInfo = fetchValidationErrorInfo(errorHeight)
        Twig.debug { "validation failed at block ${errorInfo.errorHeight} with hash: ${errorInfo.hash}" }

        errorInfo = fetchValidationErrorInfo(errorHeight + 1)
        Twig.debug { "the next block is ${errorInfo.errorHeight} with hash: ${errorInfo.hash}" }

        Twig.debug { "=================== BLOCKS [$errorHeight..${errorHeight.value + count - 1}]: START ========" }
        repeat(count) { i ->
            val height = errorHeight + i
            val block = downloader.compactBlockRepository.findCompactBlock(height)
            // sometimes the initial block was inserted via checkpoint and will not appear in the cache. We can get
            // the hash another way.
            val checkedHash = block?.hash ?: repository.findBlockHash(height)
            Twig.debug { "block: $height\thash=${checkedHash?.toHexReversed()}" }
        }
        Twig.debug { "=================== BLOCKS [$errorHeight..${errorHeight.value + count - 1}]: END ========" }
    }

    private suspend fun fetchValidationErrorInfo(errorHeight: BlockHeight): ValidationErrorInfo {
        val hash = repository.findBlockHash(errorHeight + 1)?.toHexReversed()

        return ValidationErrorInfo(errorHeight, hash)
    }

    /**
     * Called for every noteworthy error.
     *
     * @return true when processing should continue. Return false when the error is unrecoverable
     * and all processing should halt and stop retrying.
     */
    private fun onProcessorError(throwable: Throwable): Boolean {
        return onProcessorErrorListener?.invoke(throwable) ?: true
    }

    private fun determineLowerBound(errorHeight: BlockHeight): BlockHeight {
        val offset = min(MAX_REORG_SIZE, REWIND_DISTANCE * (consecutiveChainErrors.get() + 1))
        return BlockHeight(max(errorHeight.value - offset, lowerBoundHeight.value)).also {
            Twig.debug {
                "offset = min($MAX_REORG_SIZE, $REWIND_DISTANCE * (${consecutiveChainErrors.get() + 1})) = " +
                    "$offset"
            }
            Twig.debug { "lowerBound = max($errorHeight - $offset, $lowerBoundHeight) = $it" }
        }
    }

    /**
     * Poll on time boundaries. Per Issue #95, we want to avoid exposing computation time to a
     * network observer. Instead, we poll at regular time intervals that are large enough for all
     * computation to complete so no intervals are skipped. See 95 for more details.
     *
     * @param fastIntervalDesired currently not used but sometimes we want to poll quickly, such as
     * when we unexpectedly lose server connection or are waiting for an event to happen on the
     * chain. We can pass this desire along now and later figure out how to handle it, privately.
     *
     * @return the duration in milliseconds to the next poll attempt
     */
    @Suppress("UNUSED_PARAMETER")
    private fun calculatePollInterval(fastIntervalDesired: Boolean = false): Duration {
        val interval = POLL_INTERVAL
        val now = System.currentTimeMillis()
        val deltaToNextInterval = interval - (now + interval).rem(interval)
        return deltaToNextInterval.toDuration(DurationUnit.MILLISECONDS)
    }

    suspend fun calculateBirthdayHeight(): BlockHeight {
        return repository.getOldestTransaction()?.minedHeight?.value?.let {
            // To be safe adjust for reorgs (and generally a little cushion is good for privacy), so we round down to
            // the nearest 100 and then subtract 100 to ensure that the result is always at least 100 blocks away
            var oldestTransactionHeightValue = it
            oldestTransactionHeightValue -= oldestTransactionHeightValue.rem(MAX_REORG_SIZE) - MAX_REORG_SIZE.toLong()
            if (oldestTransactionHeightValue < lowerBoundHeight.value) {
                lowerBoundHeight
            } else {
                BlockHeight.new(network, oldestTransactionHeightValue)
            }
        } ?: lowerBoundHeight
    }

    /**
     * Calculates the latest balance info.
     *
     * @param account the account to check for balance info.
     *
     * @return an instance of WalletBalance containing information about available and total funds.
     */
    suspend fun getBalanceInfo(account: Account): WalletBalance {
        @Suppress("TooGenericExceptionCaught")
        return try {
            val balanceTotal = backend.getBalance(account)
            Twig.debug { "found total balance: $balanceTotal" }
            val balanceAvailable = backend.getVerifiedBalance(account)
            Twig.debug { "found available balance: $balanceAvailable" }
            WalletBalance(balanceTotal, balanceAvailable)
        } catch (t: Throwable) {
            Twig.debug { "failed to get balance due to $t" }
            throw RustLayerException.BalanceException(t)
        }
    }

    suspend fun getUtxoCacheBalance(address: String): WalletBalance =
        backend.getDownloadedUtxoBalance(address)

    /**
     * Transmits the given state for this processor.
     */
    private suspend fun setState(newState: State) {
        _state.value = newState
    }

    /**
     * Sealed class representing the various states of this processor.
     */
    sealed class State {
        /**
         * Marker interface for [State] instances that represent when the wallet is connected.
         */
        interface IConnected

        /**
         * Marker interface for [State] instances that represent when the wallet is syncing.
         */
        interface ISyncing

        /**
         * [State] for common syncing stage. It starts with downloading new blocks, then validating these blocks
         * and scanning them at the end.
         *
         * **Downloading** is when the wallet is actively downloading compact blocks because the latest
         * block height available from the server is greater than what we have locally. We move out
         * of this state once our local height matches the server.
         *
         * **Validating** is when the blocks that have been downloaded are actively being validated to
         * ensure that there are no gaps and that every block is chain-sequential to the previous
         * block, which determines whether a reorg has happened on our watch.
         *
         * **Scanning** is when the blocks that have been downloaded are actively being decrypted.
         *
         * **Deleting** is when the temporary block files being removed from the persistence.
         *
         * **Enhancing** is when transaction details are being retrieved. This typically means the wallet has
         * downloaded and scanned blocks and is now processing any transactions that were discovered. Once a
         * transaction is discovered, followup network requests are needed in order to retrieve memos or outbound
         * transaction information, like the recipient address. The existing information we have about transactions
         * is enhanced by the new information.
         */
        object Syncing : IConnected, ISyncing, State()

        /**
         * [State] for when we are done with syncing the blocks, for now, i.e. all necessary stages done (download,
         * validate, and scan).
         */
        class Synced(val syncedRange: ClosedRange<BlockHeight>?) : IConnected, ISyncing, State()

        /**
         * [State] for when we have no connection to lightwalletd.
         */
        object Disconnected : State()

        /**
         * [State] for when [stop] has been called. For simplicity, processors should not be
         * restarted but they are not prevented from this behavior.
         */
        object Stopped : State()

        /**
         * [State] the initial state of the processor, once it is constructed.
         */
        object Initialized : State()
    }

    /**
     * Progress model class for sharing the whole batch sync progress out of the sync process.
     */
    internal data class BatchSyncProgress(
        val percentage: PercentDecimal,
        val lastSyncedHeight: BlockHeight?,
        val result: BlockProcessingResult
    )

    /**
     * Progress model class for sharing particular sync stage result internally in the sync process.
     */
    private data class SyncStageResult(
        val batch: BlockBatch,
        val stageResult: BlockProcessingResult
    )

    /**
     * Data class for holding detailed information about the processor.
     *
     * @param networkBlockHeight the latest block available to lightwalletd that may or may not be
     * downloaded by this wallet yet.
     * @param lastSyncedHeight the height up to which the wallet last synced. This determines
     * where the next sync will begin.
     * @param lastSyncRange inclusive range to sync. Meaning, if the range is 10..10,
     * then we will download exactly block 10. If the range is 11..10, then we want to download
     * block 11 but can't.
     * @param firstUnenhancedHeight the height in which the enhancing should start, or null in case of no previous
     * transaction enhancing done yet
     */
    data class ProcessorInfo(
        val networkBlockHeight: BlockHeight?,
        val lastSyncedHeight: BlockHeight?,
        val lastSyncRange: ClosedRange<BlockHeight>?,
        val firstUnenhancedHeight: BlockHeight?
    ) {
        /**
         * Determines whether this instance is actively syncing compact blocks.
         *
         * @return true when there are more than zero blocks remaining to sync.
         */
        val isSyncing: Boolean
            get() =
                lastSyncedHeight != null &&
                    lastSyncRange != null &&
                    !lastSyncRange.isEmpty() &&
                    lastSyncedHeight < lastSyncRange.endInclusive

        /**
         * The amount of sync progress from 0 to 100.
         */
        @Suppress("MagicNumber")
        val syncProgress
            get() = when {
                lastSyncedHeight == null -> 0
                lastSyncRange == null -> 100
                lastSyncedHeight >= lastSyncRange.endInclusive -> 100
                else -> {
                    // when lastSyncedHeight == lastSyncedRange.first, we have synced one block, thus the offsets
                    val blocksSynced =
                        (lastSyncedHeight.value - lastSyncRange.start.value + 1).coerceAtLeast(0)
                    // we sync the range inclusively so 100..100 is one block to sync, thus the offset
                    val numberOfBlocks =
                        lastSyncRange.endInclusive.value - lastSyncRange.start.value + 1
                    // take the percentage then convert and round
                    ((blocksSynced.toFloat() / numberOfBlocks) * 100.0f).coerceAtMost(100.0f).roundToInt()
                }
            }
    }

    data class ValidationErrorInfo(
        val errorHeight: BlockHeight,
        val hash: String?
    )

    //
    // Helper Extensions
    //

    /**
     * Log the mutex in great detail just in case we need it for troubleshooting deadlock.
     */
    private suspend inline fun <T> Mutex.withLockLogged(name: String, block: () -> T): T {
        Twig.debug { "$name MUTEX: acquiring lock..." }
        this.withLock {
            Twig.debug { "$name MUTEX: ...lock acquired!" }
            return block().also {
                Twig.debug { "$name MUTEX: releasing lock" }
            }
        }
    }
}

private fun LightWalletEndpointInfoUnsafe.matchingNetwork(network: String): Boolean {
    fun String.toId() = lowercase(Locale.ROOT).run {
        when {
            contains("main") -> "mainnet"
            contains("test") -> "testnet"
            else -> this
        }
    }
    return chainName.toId() == network.toId()
}

private fun max(a: BlockHeight?, b: BlockHeight) = if (null == a) {
    b
} else if (a.value > b.value) {
    a
} else {
    b
}
