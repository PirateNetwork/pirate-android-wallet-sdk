package pirate.android.sdk.demoapp.fixture

import pirate.android.sdk.demoapp.model.WalletAddress
import pirate.android.sdk.demoapp.model.WalletAddresses

object WalletAddressesFixture {

    suspend fun new(
        unified: String = WalletAddressFixture.UNIFIED_ADDRESS_STRING,
        sapling: String = WalletAddressFixture.SAPLING_ADDRESS_STRING,
        transparent: String = WalletAddressFixture.TRANSPARENT_ADDRESS_STRING
    ) = WalletAddresses(
        WalletAddress.Unified.new(unified),
        WalletAddress.Sapling.new(sapling),
        WalletAddress.Transparent.new(transparent)
    )
}