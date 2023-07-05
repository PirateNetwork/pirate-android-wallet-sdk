package pirate.android.sdk.type

/**
 * Model object for holding a wallet birthday.
 *
 * @param height the height at the time the wallet was born.
 * @param hash the hash of the block at the height.
 * @param time the block time at the height. Represented as seconds since the Unix epoch.
 * @param tree the sapling tree corresponding to the height.
 */
data class PirateWalletBirthday(
    val height: Int = -1,
    val hash: String = "",
    val time: Long = -1,
    // Note: this field does NOT match the name of the JSON, so will break with field-based JSON parsing
    val tree: String = ""
) {
    companion object
}

/**
 * A grouping of keys that correspond to a single wallet account but do not have spend authority.
 *
 * @param extfvk the extended full viewing key which provides the ability to see inbound and
 * outbound shielded transactions. It can also be used to derive a z-addr.
 * @param extpub the extended public key which provides the ability to see transparent
 * transactions. It can also be used to derive a t-addr.
 */
data class PirateUnifiedViewingKey(
    val extfvk: String = "",
    val extpub: String = ""
)

data class PirateUnifiedAddressAccount(
    val accountId: Int = -1,
    override val rawShieldedAddress: String = "",
    override val rawTransparentAddress: String = ""
) : UnifiedAddress

interface UnifiedAddress {
    val rawShieldedAddress: String
    val rawTransparentAddress: String
}

enum class PirateNetwork(
    val id: Int,
    val networkName: String,
    val saplingActivationHeight: Int,
    val defaultHost: String,
    val defaultPort: Int
) {
    Testnet(0, "testnet", 280_000, "testlightd.pirate.black", 443),
    Mainnet(1, "mainnet", 152_855, "lightd1.pirate.black", 443);

    companion object {
        fun from(id: Int) = values().first { it.id == id }
    }
}
