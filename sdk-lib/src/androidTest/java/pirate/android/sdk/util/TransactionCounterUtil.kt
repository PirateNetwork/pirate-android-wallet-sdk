package pirate.android.sdk.util

import androidx.test.platform.app.InstrumentationRegistry
import pirate.android.sdk.internal.Twig
import pirate.android.sdk.internal.model.ext.from
import pirate.android.sdk.model.BlockHeight
import pirate.android.sdk.model.Mainnet
import pirate.android.sdk.model.PirateNetwork
import pirate.lightwallet.client.LightWalletClient
import pirate.lightwallet.client.model.BlockHeightUnsafe
import pirate.lightwallet.client.model.LightWalletEndpoint
import pirate.lightwallet.client.new
import org.junit.Ignore
import org.junit.Test

class TransactionCounterUtil {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val lightWalletClient = LightWalletClient.new(context, LightWalletEndpoint.Mainnet)

    @Test
    @Ignore("This test is broken")
    fun testBlockSize() {
        val sizes = mutableMapOf<Int, Int>()

        lightWalletClient.getBlockRange(
            BlockHeightUnsafe.from(
                BlockHeight.new(
                    PirateNetwork.Mainnet,
                    900_000
                )
            )..BlockHeightUnsafe.from(
                BlockHeight.new(
                    PirateNetwork.Mainnet,
                    910_000
                )
            )
        )
        /*
        Fixme: work with flow instead of sequence
        assert(response is Response.Success)

        Fixme: serializedSize is not available anymore
        (response as Response.Success).result.forEach { compactBlock ->
            twig("h: ${compactBlock.header.size}")
            val s = compactBlock.serializedSize
            sizes[s] = (sizes[s] ?: 0) + 1
        }
         */

        Twig.debug { "sizes: ${sizes.toSortedMap()}" }
    }

    @Test
    @Ignore("This test is broken")
    fun testCountTransactions() {
        val txCounts = mutableMapOf<Int, Int>()
        val outputCounts = mutableMapOf<Int, Int>()
        var totalOutputs = 0
        var totalTxs = 0

        lightWalletClient.getBlockRange(
            BlockHeightUnsafe.from(
                BlockHeight.new(
                    PirateNetwork.Mainnet,
                    900_000
                )
            )..BlockHeightUnsafe.from(
                BlockHeight.new(
                    PirateNetwork.Mainnet,
                    950_000
                )
            )
        )
        /*
        Fixme: work with flow instead of sequence
        assert(response is Response.Success) { "Server communication failed." }

        Fixme: vtx is not available anymore. Once we'll decide to enable this test again, we could compare a known
             outputs count for the selected blocks range with the calculated outputs count from synced the blocks
        (response as Response.Success).result.forEach { compactBlock ->
            compactBlock.vtx.map { it.outputs.size }.forEach { oCount ->
                outputCounts[oCount] = (outputCounts[oCount] ?: 0) + oCount.coerceAtLeast(1)
                totalOutputs += oCount
            }
            compactBlock.vtx.size.let { count ->
                txCounts[count] = (txCounts[count] ?: 0) + count.coerceAtLeast(1)
                totalTxs += count
            }
        }
         */
        Twig.debug { "txs: $txCounts" }
        Twig.debug { "outputs: $outputCounts" }
        Twig.debug { "total: $totalTxs  $totalOutputs" }
    }
}
