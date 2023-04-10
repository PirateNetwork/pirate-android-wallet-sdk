package cash.z.ecc.android.sdk.jni

import cash.z.ecc.android.sdk.internal.SaplingParamTool
import cash.z.ecc.android.sdk.internal.SdkDispatchers
import cash.z.ecc.android.sdk.internal.Twig
import cash.z.ecc.android.sdk.internal.ext.deleteRecursivelySuspend
import cash.z.ecc.android.sdk.internal.ext.deleteSuspend
import cash.z.ecc.android.sdk.internal.model.JniBlockMeta
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.ZcashNetwork
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Serves as the JNI boundary between the Kotlin and Rust layers. Functions in this class should
 * not be called directly by code outside of the SDK. Instead, one of the higher-level components
 * should be used such as Wallet.kt or CompactBlockProcessor.kt.
 */
@Suppress("TooManyFunctions")
internal class RustBackend private constructor(
    override val network: ZcashNetwork,
    val birthdayHeight: BlockHeight,
    val dataDbFile: File,
    val fsBlockDbRoot: File,
    override val saplingParamDir: File
) : Backend {

    /**
     * This function deletes the data database file and the cache directory (compact blocks files) if set by input
     * parameters.
     *
     * @param clearCache to request the cache directory and its content deletion
     * @param clearDataDb to request the data database file deletion
     *
     * @return false in case of any required and failed deletion, true otherwise.
     */
    suspend fun clear(clearCache: Boolean = true, clearDataDb: Boolean = true): Boolean {
        var cacheClearResult = true
        var dataClearResult = true
        if (clearCache) {
            Twig.debug { "Deleting the cache files..." }
            fsBlockDbRoot.deleteRecursivelySuspend().also { result ->
                Twig.debug { "Deletion of the cache files ${if (result) "succeeded" else "failed"}!" }
                cacheClearResult = result
            }
        }
        if (clearDataDb) {
            Twig.debug { "Deleting the data database..." }
            dataDbFile.deleteSuspend().also { result ->
                Twig.debug { "Deletion of the data database ${if (result) "succeeded" else "failed"}!" }
                dataClearResult = result
            }
        }
        return cacheClearResult && dataClearResult
    }

    //
    // Wrapper Functions
    //

    override suspend fun initBlockMetaDb() = withContext(SdkDispatchers.DATABASE_IO) {
        initBlockMetaDb(
            fsBlockDbRoot.absolutePath,
        )
    }

    override suspend fun initDataDb(seed: ByteArray?) = withContext(SdkDispatchers.DATABASE_IO) {
        initDataDb(
            dataDbFile.absolutePath,
            seed,
            networkId = network.id
        )
    }

    override suspend fun createAccount(seed: ByteArray): UnifiedSpendingKeyJni {
        return withContext(SdkDispatchers.DATABASE_IO) {
            createAccount(
                dataDbFile.absolutePath,
                seed,
                networkId = network.id
            )
        }
    }

    /**
     * @param keys A list of UFVKs to initialize the accounts table with
     */
    override suspend fun initAccountsTable(vararg keys: String): Boolean {
        return withContext(SdkDispatchers.DATABASE_IO) {
            initAccountsTableWithKeys(
                dataDbFile.absolutePath,
                keys,
                networkId = network.id
            )
        }
    }

    override suspend fun initBlocksTable(
        checkpointHeight: Long,
        checkpointHash: String,
        checkpointTime: Long,
        checkpointSaplingTree: String,
    ): Boolean {
        return withContext(SdkDispatchers.DATABASE_IO) {
            initBlocksTable(
                dataDbFile.absolutePath,
                checkpointHeight,
                checkpointHash,
                checkpointTime,
                checkpointSaplingTree,
                networkId = network.id
            )
        }
    }

    override suspend fun getCurrentAddress(account: Int) =
        withContext(SdkDispatchers.DATABASE_IO) {
            getCurrentAddress(
                dataDbFile.absolutePath,
                account,
                networkId = network.id
            )
        }

    override fun getTransparentReceiver(ua: String) = getTransparentReceiverForUnifiedAddress(ua)

    override fun getSaplingReceiver(ua: String) = getSaplingReceiverForUnifiedAddress(ua)

    override suspend fun listTransparentReceivers(account: Int): List<String> {
        return withContext(SdkDispatchers.DATABASE_IO) {
            listTransparentReceivers(
                dbDataPath = dataDbFile.absolutePath,
                account = account,
                networkId = network.id
            ).asList()
        }
    }

    override suspend fun getBalance(account: Int): Long {
        val longValue = withContext(SdkDispatchers.DATABASE_IO) {
            getBalance(
                dataDbFile.absolutePath,
                account,
                networkId = network.id
            )
        }

        return longValue
    }

    override suspend fun getVerifiedBalance(account: Int): Long {
        val longValue = withContext(SdkDispatchers.DATABASE_IO) {
            getVerifiedBalance(
                dbDataPath = dataDbFile.absolutePath,
                account = account,
                networkId = network.id
            )
        }

        return longValue
    }

    override suspend fun getReceivedMemoAsUtf8(idNote: Long) =
        withContext(SdkDispatchers.DATABASE_IO) {
            getReceivedMemoAsUtf8(
                dataDbFile.absolutePath,
                idNote,
                networkId = network.id
            )
        }

    override suspend fun getSentMemoAsUtf8(idNote: Long) =
        withContext(SdkDispatchers.DATABASE_IO) {
            getSentMemoAsUtf8(
                dataDbFile.absolutePath,
                idNote,
                networkId = network.id
            )
        }

    override suspend fun writeBlockMetadata(blockMetadata: List<JniBlockMeta>) =
        withContext(SdkDispatchers.DATABASE_IO) {
            writeBlockMetadata(
                fsBlockDbRoot.absolutePath,
                blockMetadata.toTypedArray()
            )
        }

    override suspend fun getLatestHeight() =
        withContext(SdkDispatchers.DATABASE_IO) {
            val height = getLatestHeight(fsBlockDbRoot.absolutePath)

            if (-1L == height) {
                null
            } else {
                height
            }
        }

    override suspend fun findBlockMetadata(height: Long) =
        withContext(SdkDispatchers.DATABASE_IO) {
            findBlockMetadata(
                fsBlockDbRoot.absolutePath,
                height
            )
        }

    override suspend fun rewindBlockMetadataToHeight(height: Long) =
        withContext(SdkDispatchers.DATABASE_IO) {
            rewindBlockMetadataToHeight(
                fsBlockDbRoot.absolutePath,
                height
            )
        }

    override suspend fun validateCombinedChainOrErrorHeight() =
        withContext(SdkDispatchers.DATABASE_IO) {
            val validationResult = validateCombinedChain(
                fsBlockDbRoot.absolutePath,
                dataDbFile.absolutePath,
                networkId = network.id
            )

            if (-1L == validationResult) {
                null
            } else {
                validationResult
            }
        }

    override suspend fun getVerifiedTransparentBalance(address: String): Long =
        withContext(SdkDispatchers.DATABASE_IO) {
            getVerifiedTransparentBalance(
                dataDbFile.absolutePath,
                address,
                networkId = network.id
            )
        }

    override suspend fun getTotalTransparentBalance(address: String): Long =
        withContext(SdkDispatchers.DATABASE_IO) {
            getTotalTransparentBalance(
                dataDbFile.absolutePath,
                address,
                networkId = network.id
            )
        }

    override suspend fun getNearestRewindHeight(height: Long): Long =
        withContext(SdkDispatchers.DATABASE_IO) {
            getNearestRewindHeight(
                dataDbFile.absolutePath,
                height,
                networkId = network.id
            )
        }

    /**
     * Deletes data for all blocks above the given height. Boils down to:
     *
     * DELETE FROM blocks WHERE height > ?
     */
    override suspend fun rewindToHeight(height: Long) =
        withContext(SdkDispatchers.DATABASE_IO) {
            rewindToHeight(
                dataDbFile.absolutePath,
                height,
                networkId = network.id
            )
        }

    override suspend fun scanBlocks(limit: Int): Boolean {
        return withContext(SdkDispatchers.DATABASE_IO) {
            scanBlocks(
                fsBlockDbRoot.absolutePath,
                dataDbFile.absolutePath,
                limit,
                networkId = network.id
            )
        }
    }

    override suspend fun decryptAndStoreTransaction(tx: ByteArray) =
        withContext(SdkDispatchers.DATABASE_IO) {
            decryptAndStoreTransaction(
                dataDbFile.absolutePath,
                tx,
                networkId = network.id
            )
        }

    override suspend fun createToAddress(
        account: Int,
        unifiedSpendingKey: ByteArray,
        to: String,
        value: Long,
        memo: ByteArray?
    ): Long = withContext(SdkDispatchers.DATABASE_IO) {
        createToAddress(
            dataDbFile.absolutePath,
            unifiedSpendingKey,
            to,
            value,
            memo ?: ByteArray(0),
            File(saplingParamDir, SaplingParamTool.SPEND_PARAM_FILE_NAME).absolutePath,
            File(saplingParamDir, SaplingParamTool.OUTPUT_PARAM_FILE_NAME).absolutePath,
            networkId = network.id,
            useZip317Fees = IS_USE_ZIP_317_FEES
        )
    }

    override suspend fun shieldToAddress(
        account: Int,
        unifiedSpendingKey: ByteArray,
        memo: ByteArray?
    ): Long {
        Twig.debug { "TMP: shieldToAddress with db path: $dataDbFile, ${memo?.size}" }
        return withContext(SdkDispatchers.DATABASE_IO) {
            shieldToAddress(
                dataDbFile.absolutePath,
                unifiedSpendingKey,
                memo ?: ByteArray(0),
                File(saplingParamDir, SaplingParamTool.SPEND_PARAM_FILE_NAME).absolutePath,
                File(saplingParamDir, SaplingParamTool.OUTPUT_PARAM_FILE_NAME).absolutePath,
                networkId = network.id,
                useZip317Fees = IS_USE_ZIP_317_FEES
            )
        }
    }

    override suspend fun putUtxo(
        tAddress: String,
        txId: ByteArray,
        index: Int,
        script: ByteArray,
        value: Long,
        height: BlockHeight
    ): Boolean = withContext(SdkDispatchers.DATABASE_IO) {
        putUtxo(
            dataDbFile.absolutePath,
            tAddress,
            txId,
            index,
            script,
            value,
            height.value,
            networkId = network.id
        )
    }

    override fun isValidShieldedAddr(addr: String) =
        isValidShieldedAddress(addr, networkId = network.id)

    override fun isValidTransparentAddr(addr: String) =
        isValidTransparentAddress(addr, networkId = network.id)

    override fun isValidUnifiedAddr(addr: String) =
        isValidUnifiedAddress(addr, networkId = network.id)

    override fun getBranchIdForHeight(height: Long): Long =
        branchIdForHeight(height, networkId = network.id)

//    /**
//     * This is a proof-of-concept for doing Local RPC, where we are effectively using the JNI
//     * boundary as a grpc server. It is slightly inefficient in terms of both space and time but
//     * given that it is all done locally, on the heap, it seems to be a worthwhile tradeoff because
//     * it reduces the complexity and expands the capacity for the two layers to communicate.
//     *
//     * We're able to keep the "unsafe" byteArray functions private and wrap them in typeSafe
//     * equivalents and, eventually, surface any parse errors (for now, errors are only logged).
//     */
//     override fun parseTransactionDataList(
//         tdl: LocalRpcTypes.TransactionDataList
//     ): LocalRpcTypes.TransparentTransactionList {
//         return try {
//             // serialize the list, send it over to rust and get back a serialized set of results that we parse out
//             // and return
//             return LocalRpcTypes.TransparentTransactionList.parseFrom(parseTransactionDataList(tdl.toByteArray()))
//         } catch (t: Throwable) {
//             twig("ERROR: failed to parse transaction data list due to: $t caused by: ${t.cause}")
//             LocalRpcTypes.TransparentTransactionList.newBuilder().build()
//         }
//     }

    /**
     * Exposes all of the librustzcash functions along with helpers for loading the static library.
     */
    companion object {
        internal val rustLibraryLoader = NativeLibraryLoader("zcashwalletsdk")
        private const val IS_USE_ZIP_317_FEES = false

        suspend fun loadLibrary() {
            rustLibraryLoader.load {
                initOnLoad()
            }
        }

        /**
         * Loads the library and initializes path variables. Although it is best to only call this
         * function once, it is idempotent.
         */
        suspend fun init(
            fsBlockDbRoot: File,
            dataDbFile: File,
            saplingParamsDir: File,
            zcashNetwork: ZcashNetwork,
            birthdayHeight: BlockHeight
        ): RustBackend {
            loadLibrary()

            return RustBackend(
                zcashNetwork,
                birthdayHeight,
                dataDbFile = dataDbFile,
                fsBlockDbRoot = fsBlockDbRoot,
                saplingParamDir = saplingParamsDir
            )
        }

        //
        // External Functions
        //

        @JvmStatic
        private external fun initOnLoad()

        @JvmStatic
        private external fun initBlockMetaDb(fsBlockDbRoot: String): Int

        @JvmStatic
        private external fun initDataDb(dbDataPath: String, seed: ByteArray?, networkId: Int): Int

        @JvmStatic
        private external fun initAccountsTableWithKeys(
            dbDataPath: String,
            ufvks: Array<out String>,
            networkId: Int
        ): Boolean

        @JvmStatic
        @Suppress("LongParameterList")
        private external fun initBlocksTable(
            dbDataPath: String,
            height: Long,
            hash: String,
            time: Long,
            saplingTree: String,
            networkId: Int
        ): Boolean

        @JvmStatic
        private external fun createAccount(dbDataPath: String, seed: ByteArray, networkId: Int): UnifiedSpendingKeyJni

        @JvmStatic
        private external fun getCurrentAddress(
            dbDataPath: String,
            account: Int,
            networkId: Int
        ): String

        @JvmStatic
        private external fun getTransparentReceiverForUnifiedAddress(ua: String): String?

        @JvmStatic
        private external fun getSaplingReceiverForUnifiedAddress(ua: String): String?

        @JvmStatic
        private external fun listTransparentReceivers(dbDataPath: String, account: Int, networkId: Int): Array<String>

        internal fun validateUnifiedSpendingKey(bytes: ByteArray) =
            isValidSpendingKey(bytes)

        @JvmStatic
        private external fun isValidSpendingKey(bytes: ByteArray): Boolean

        @JvmStatic
        private external fun isValidShieldedAddress(addr: String, networkId: Int): Boolean

        @JvmStatic
        private external fun isValidTransparentAddress(addr: String, networkId: Int): Boolean

        @JvmStatic
        private external fun isValidUnifiedAddress(addr: String, networkId: Int): Boolean

        @JvmStatic
        private external fun getBalance(dbDataPath: String, account: Int, networkId: Int): Long

        @JvmStatic
        private external fun getVerifiedBalance(
            dbDataPath: String,
            account: Int,
            networkId: Int
        ): Long

        @JvmStatic
        private external fun getReceivedMemoAsUtf8(
            dbDataPath: String,
            idNote: Long,
            networkId: Int
        ): String?

        @JvmStatic
        private external fun getSentMemoAsUtf8(
            dbDataPath: String,
            dNote: Long,
            networkId: Int
        ): String?

        @JvmStatic
        private external fun writeBlockMetadata(
            dbCachePath: String,
            blockMeta: Array<JniBlockMeta>
        ): Boolean

        @JvmStatic
        private external fun getLatestHeight(dbCachePath: String): Long

        @JvmStatic
        private external fun findBlockMetadata(
            dbCachePath: String,
            height: Long
        ): JniBlockMeta?

        @JvmStatic
        private external fun rewindBlockMetadataToHeight(
            dbCachePath: String,
            height: Long
        )

        @JvmStatic
        private external fun validateCombinedChain(
            dbCachePath: String,
            dbDataPath: String,
            networkId: Int
        ): Long

        @JvmStatic
        private external fun getNearestRewindHeight(
            dbDataPath: String,
            height: Long,
            networkId: Int
        ): Long

        @JvmStatic
        private external fun rewindToHeight(
            dbDataPath: String,
            height: Long,
            networkId: Int
        ): Boolean

        @JvmStatic
        private external fun scanBlocks(
            dbCachePath: String,
            dbDataPath: String,
            limit: Int,
            networkId: Int
        ): Boolean

        @JvmStatic
        private external fun decryptAndStoreTransaction(
            dbDataPath: String,
            tx: ByteArray,
            networkId: Int
        )

        @JvmStatic
        @Suppress("LongParameterList")
        private external fun createToAddress(
            dbDataPath: String,
            usk: ByteArray,
            to: String,
            value: Long,
            memo: ByteArray,
            spendParamsPath: String,
            outputParamsPath: String,
            networkId: Int,
            useZip317Fees: Boolean
        ): Long

        @JvmStatic
        @Suppress("LongParameterList")
        private external fun shieldToAddress(
            dbDataPath: String,
            usk: ByteArray,
            memo: ByteArray,
            spendParamsPath: String,
            outputParamsPath: String,
            networkId: Int,
            useZip317Fees: Boolean
        ): Long

        @JvmStatic
        private external fun branchIdForHeight(height: Long, networkId: Int): Long

        @JvmStatic
        @Suppress("LongParameterList")
        private external fun putUtxo(
            dbDataPath: String,
            tAddress: String,
            txId: ByteArray,
            index: Int,
            script: ByteArray,
            value: Long,
            height: Long,
            networkId: Int
        ): Boolean

        @JvmStatic
        private external fun getVerifiedTransparentBalance(
            pathDataDb: String,
            taddr: String,
            networkId: Int
        ): Long

        @JvmStatic
        private external fun getTotalTransparentBalance(
            pathDataDb: String,
            taddr: String,
            networkId: Int
        ): Long
    }
}
