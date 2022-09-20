package pirate.android.sdk.jni

import androidx.test.filters.SmallTest
import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.toSeed
import pirate.android.sdk.annotation.MaintainedTest
import pirate.android.sdk.annotation.TestPurpose
import pirate.android.sdk.internal.PirateTroubleshootingTwig
import pirate.android.sdk.internal.Twig
import pirate.android.sdk.tool.PirateDerivationTool
import pirate.android.sdk.type.PirateNetwork
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@MaintainedTest(TestPurpose.REGRESSION)
@RunWith(Parameterized::class)
@SmallTest
class TransparentTest(val expected: Expected, val network: PirateNetwork) {

    @Test
    fun deriveTransparentSecretKeyTest() = runBlocking {
        assertEquals(expected.tskCompressed, PirateDerivationTool.deriveTransparentSecretKey(SEED, network = network))
    }

    @Test
    fun deriveTransparentAddressTest() = runBlocking {
        assertEquals(expected.tAddr, PirateDerivationTool.deriveTransparentAddress(SEED, network = network))
    }

    @Test
    fun deriveTransparentAddressFromSecretKeyTest() = runBlocking {
        val pk = PirateDerivationTool.deriveTransparentSecretKey(SEED, network = network)
        assertEquals(expected.tAddr, PirateDerivationTool.deriveTransparentAddressFromPrivateKey(pk, network = network))
    }

    @Test
    fun derivePirateUnifiedViewingKeysFromSeedTest() = runBlocking {
        val uvks = PirateDerivationTool.derivePirateUnifiedViewingKeys(SEED, network = network)
        assertEquals(1, uvks.size)
        val uvk = uvks.first()
        assertEquals(expected.zAddr, PirateDerivationTool.deriveShieldedAddress(uvk.extfvk, network = network))
        assertEquals(expected.tAddr, PirateDerivationTool.deriveTransparentAddressFromPublicKey(uvk.extpub, network = network))
    }

    companion object {
        const val PHRASE = "deputy visa gentle among clean scout farm drive comfort patch skin salt ranch cool ramp warrior drink narrow normal lunch behind salt deal person"
        val MNEMONIC = MnemonicCode(PHRASE)
        val SEED = MNEMONIC.toSeed()

        object ExpectedMainnet : Expected {
            override val tAddr = "t1PKtYdJJHhc3Pxowmznkg7vdTwnhEsCvR4"
            override val zAddr = "zs1yc4sgtfwwzz6xfsy2xsradzr6m4aypgxhfw2vcn3hatrh5ryqsr08sgpemlg39vdh9kfupx20py"
            override val tskCompressed = "L4BvDC33yLjMRxipZvdiUmdYeRfZmR8viziwsVwe72zJdGbiJPv2"
            override val tpk = "03b1d7fb28d17c125b504d06b1530097e0a3c76ada184237e3bc0925041230a5af"
        }

        object ExpectedTestnet : Expected {
            override val tAddr = "tm9v3KTsjXK8XWSqiwFjic6Vda6eHY9Mjjq"
            override val zAddr = "ztestsapling1wn3tw9w5rs55x5yl586gtk72e8hcfdq8zsnjzcu8p7ghm8lrx54axc74mvm335q7lmy3g0sqje6"
            override val tskCompressed = "KzVugoXxR7AtTMdR5sdJtHxCNvMzQ4H196k7ATv4nnjoummsRC9G"
            override val tpk = "03b1d7fb28d17c125b504d06b1530097e0a3c76ada184237e3bc0925041230a5af"
        }

        @BeforeClass
        @JvmStatic
        fun startup() {
            Twig.plant(PirateTroubleshootingTwig(formatter = { "@TWIG $it" }))
        }

        @JvmStatic
        @Parameterized.Parameters
        fun data() = listOf(
            arrayOf(ExpectedTestnet, PirateNetwork.Testnet),
            arrayOf(ExpectedMainnet, PirateNetwork.Mainnet),
        )
    }

    interface Expected {
        val tAddr: String
        val zAddr: String
        val tskCompressed: String
        val tpk: String
    }
}
