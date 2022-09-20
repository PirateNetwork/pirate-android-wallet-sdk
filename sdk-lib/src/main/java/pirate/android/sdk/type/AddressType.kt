package pirate.android.sdk.type

/**
 * Validation helper class, representing the types of addresses, either Shielded, Transparent or
 * Invalid. Used in conjuction with [pirate.android.sdk.Synchronizer.validateAddress].
 */
sealed class PirateAddressType {
    /**
     * Marker interface for valid [PirateAddressType] instances.
     */
    interface Valid

    /**
     * An instance of [PirateAddressType] corresponding to a valid z-addr.
     */
    object Shielded : Valid, PirateAddressType()

    /**
     * An instance of [PirateAddressType] corresponding to a valid t-addr.
     */
    object Transparent : Valid, PirateAddressType()

    /**
     * An instance of [PirateAddressType] corresponding to an invalid address.
     *
     * @param reason a description of why the address was invalid.
     */
    class PirateInvalid(val reason: String = "Invalid") : PirateAddressType()

    /**
     * A convenience method that returns true when an instance of this class is invalid.
     */
    val isNotValid get() = this !is Valid
}
