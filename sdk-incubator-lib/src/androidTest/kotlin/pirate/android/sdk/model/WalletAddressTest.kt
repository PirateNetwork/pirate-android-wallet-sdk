package pirate.android.sdk.model

import pirate.android.sdk.fixture.WalletAddressFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletAddressTest {
    @Test
    @ExperimentalCoroutinesApi
    fun unified_equals_different_instance() = runTest {
        val one = WalletAddressFixture.unified()
        val two = WalletAddressFixture.unified()

        assertEquals(one, two)
    }
}
