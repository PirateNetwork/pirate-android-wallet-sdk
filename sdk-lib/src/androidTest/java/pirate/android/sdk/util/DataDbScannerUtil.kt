package pirate.android.sdk.util

import androidx.test.platform.app.InstrumentationRegistry
import pirate.android.sdk.CloseablePirateSynchronizer
import pirate.android.sdk.PirateSdkSynchronizer
import pirate.android.sdk.PirateSynchronizer
import pirate.android.sdk.internal.PirateTroubleshootingTwig
import pirate.android.sdk.internal.Twig
import pirate.android.sdk.model.BlockHeight
import pirate.android.sdk.model.LightWalletEndpoint
import pirate.android.sdk.model.PiratehNetwork
import pirate.android.sdk.model.defaultForNetwork
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * A tool for validating an existing database and testing reorgs.
 */
@ExperimentalCoroutinesApi
class DataDbScannerUtil {
    private val context = InstrumentationRegistry.getInstrumentation().context

    private val host = "lightwalletd.pirate.black"
    private val port = 443
    private val alias = "ScannerUtil"

//    private val mnemonics = SimpleMnemonics()
//    private val caceDbPath = PirateInitializer.cacheDbPath(context, alias)

//    private val downloader = PirateCompactBlockDownloader(
//        PirateLightWalletGrpcService(context, host, port),
//        PirateCompactBlockDbStore(context, caceDbPath)
//    )

//    private val processor = PirateCompactBlockProcessor(downloader)

//    private val rustBackend = PirateRustBackend.init(context, cacheDbName, dataDbName)

    private val birthdayHeight = 600_000L
    private lateinit var synchronizer: CloseablePirateSynchronizer

    @Before
    fun setup() {
        Twig.plant(PirateTroubleshootingTwig())
//        cacheBlocks()
    }

    private fun cacheBlocks() = runBlocking {
//        twig("downloading compact blocks...")
//        val latestBlockHeight = downloader.getLatestBlockHeight()
//        val lastDownloaded = downloader.getLastDownloadedHeight()
//        val blockRange = (Math.max(birthday, lastDownloaded))..latestBlockHeight
//        downloadNewBlocks(blockRange)
//        val error = validateNewBlocks(blockRange)
//        twig("validation completed with result $error")
//        assertEquals(-1, error)
    }

    private fun deleteDb(dbName: String) {
        context.getDatabasePath(dbName).absoluteFile.delete()
    }

    @Test
    @Ignore("This test is broken")
    fun scanExistingDb() {
        synchronizer = PirateSynchronizer.newBlocking(
            context,
            PirateNetwork.Mainnet,
            lightWalletEndpoint = LightWalletEndpoint
                .defaultForNetwork(PiratehNetwork.Mainnet),
            seed = byteArrayOf(),
            birthday = BlockHeight.new(
                PirateNetwork.Mainnet,
                birthdayHeight
            )
        )

        println("sync!")
        val scope = (synchronizer as PirateSdkSynchronizer).coroutineScope

        scope.launch {
            synchronizer.status.collect { status ->
                //            when (status) {
                println("received status of $status")
                //            }
            }
        }
        println("going to sleep!")
        Thread.sleep(125000)
        println("I'm back and I'm out!")
        runBlocking { synchronizer.close() }
    }
//
//    @Test
//    fun printBalances() = runBlocking {
//        readLines()
//            .map { seedPhrase ->
//                twig("checking balance for: $seedPhrase")
//                mnemonics.toSeed(seedPhrase.toCharArray())
//            }.collect { seed ->
//                initializer.import(seed, birthday, clearDataDb = true, clearCacheDb = false)
//                    /*
//                what I need to do right now
//                - for each seed
//                - I can reuse the cache of blocks... so just like get the cache once
//                - I need to scan into a new database
//                    - I don't really need a new rustbackend
//                    - I definitely don't need a new grpc connection
//                - can I just use a processor and point it to a different DB?
//                + so yeah, I think I need to use the processor directly right here and just swap out its pieces
//                    - perhaps create a new initializer and use that to configure the processor?
//                    - or maybe just set the data destination for the processor
//                    - I might need to consider how state is impacting this design
//                        - can we be more stateless and thereby improve the flexibility of this code?!!!
//                      */
//                synchronizer?.stop()
//                synchronizer = PirateSynchronizer(context, initializer)
//
// //            deleteDb(dataDbPath)
// //            initWallet(seed)
// //            twig("scanning blocks for seed <$seed>")
// ////            rustBackend.scanBlocks()
// //            twig("done scanning blocks for seed $seed")
// ////            val total = rustBackend.getBalance(0)
// //            twig("found total: $total")
// ////            val available = rustBackend.getVerifiedBalance(0)
// //            twig("found available: $available")
// //            twig("xrxrx2\t$seed\t$total\t$available")
// //            println("xrxrx2\t$seed\t$total\t$available")
//        }
//
//        Thread.sleep(5000)
//        assertEquals("foo", "bar")
//    }
}
