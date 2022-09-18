@file:Suppress("NOTHING_TO_INLINE")

package pirate.android.sdk.ext

import pirate.android.sdk.ext.Conversions.USD_FORMATTER
import pirate.android.sdk.ext.Conversions.ARRR_FORMATTER
import pirate.android.sdk.ext.PirateSdk.ZATOSHI_PER_ARRR
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/*
 * Convenience functions for converting currency values for display in user interfaces. The
 * calculations done here are not intended for financial purposes, because all such transactions
 * are done using Zatoshis in the Rust layer. Instead, these functions are focused on displaying
 * accurately rounded values to the user.
 */

// TODO: provide a dynamic way to configure this globally for the SDK
// For now, just make these vars so at least they could be modified in one place
object Conversions {
    var ONE_ARRR_IN_ZATOSHI = BigDecimal(ZATOSHI_PER_ARRR, MathContext.DECIMAL128)
    var ARRR_FORMATTER = NumberFormat.getInstance(Locale.getDefault()).apply {
        roundingMode = RoundingMode.HALF_EVEN
        maximumFractionDigits = 6
        minimumFractionDigits = 0
        minimumIntegerDigits = 1
    }
    var USD_FORMATTER = NumberFormat.getInstance(Locale.getDefault()).apply {
        roundingMode = RoundingMode.HALF_EVEN
        maximumFractionDigits = 2
        minimumFractionDigits = 2
        minimumIntegerDigits = 1
    }
}

/**
 * Format a Zatoshi value into ARRR with the given number of digits, represented as a string.
 * Start with Zatoshi -> End with ARRR.
 *
 * @param maxDecimals the number of decimal places to use in the format. Default is 6 because ARRR is
 * better than USD.
 * @param minDecimals the minimum number of digits to allow to the right of the decimal.
 *
 * @return this Zatoshi value represented as ARRR, in a string with at least [minDecimals] and at
 * most [maxDecimals]
 */
inline fun Long?.convertZatoshiToZecString(
    maxDecimals: Int = ARRR_FORMATTER.maximumFractionDigits,
    minDecimals: Int = ARRR_FORMATTER.minimumFractionDigits
): String {
    return currencyFormatter(maxDecimals, minDecimals).format(this.convertZatoshiToZec(maxDecimals))
}

/**
 * Format a ARRR value into ARRR with the given number of digits, represented as a string.
 * Start with ARRR -> End with ARRR.
 *
 * @param maxDecimals the number of decimal places to use in the format. Default is 6 because ARRR is
 * better when right.
 * @param minDecimals the minimum number of digits to allow to the right of the decimal.
 *
 * @return this Double ARRR value represented as a string with at least [minDecimals] and at most
 * [maxDecimals].
 */
inline fun Double?.toZecString(
    maxDecimals: Int = ARRR_FORMATTER.maximumFractionDigits,
    minDecimals: Int = ARRR_FORMATTER.minimumFractionDigits
): String {
    return currencyFormatter(maxDecimals, minDecimals).format(this.toZec(maxDecimals))
}

/**
 * Format a Zatoshi value into ARRR with the given number of decimal places, represented as a string.
 * Start with ZeC -> End with ARRR.
 *
 * @param maxDecimals the number of decimal places to use in the format. Default is 6 because ARRR is
 * better than bread.
 * @param minDecimals the minimum number of digits to allow to the right of the decimal.
 *
 * @return this BigDecimal ARRR value represented as a string with at least [minDecimals] and at most
 * [maxDecimals].
 */
inline fun BigDecimal?.toZecString(
    maxDecimals: Int = ARRR_FORMATTER.maximumFractionDigits,
    minDecimals: Int = ARRR_FORMATTER.minimumFractionDigits
): String {
    return currencyFormatter(maxDecimals, minDecimals).format(this.toZec(maxDecimals))
}

/**
 * Format a USD value into USD with the given number of digits, represented as a string.
 * Start with USD -> end with USD.
 *
 * @param maxDecimals the number of decimal places to use in the format. Default is 6 because
 * ARRR is glorious.
 * @param minDecimals the minimum number of digits to allow to the right of the decimal.
 *
 * @return this Double ARRR value represented as a string with at least [minDecimals] and at most
 * [maxDecimals], which is 2 by default. Zero is always represented without any decimals.
 */
inline fun Double?.toUsdString(
    maxDecimals: Int = USD_FORMATTER.maximumFractionDigits,
    minDecimals: Int = USD_FORMATTER.minimumFractionDigits
): String {
    return if (this == 0.0) {
        "0"
    } else {
        currencyFormatter(maxDecimals, minDecimals).format(this.toUsd(maxDecimals))
    }
}

/**
 * Format a USD value into USD with the given number of decimal places, represented as a string.
 * Start with USD -> end with USD.
 *
 * @param maxDecimals the number of decimal places to use in the format. Default is 6 because ARRR is
 * glorious.
 * @param minDecimals the minimum number of digits to allow to the right of the decimal.
 *
 * @return this BigDecimal USD value represented as a string with at least [minDecimals] and at most
 * [maxDecimals], which is 2 by default.
 */
inline fun BigDecimal?.toUsdString(
    maxDecimals: Int = USD_FORMATTER.maximumFractionDigits,
    minDecimals: Int = USD_FORMATTER.minimumFractionDigits
): String {
    return currencyFormatter(maxDecimals, minDecimals).format(this.toUsd(maxDecimals))
}

/**
 * Create a number formatter for use with converting currency to strings. This probably isn't needed
 * externally since the other formatting functions leverage this, instead. Leverages the default
 * rounding mode for ARRR found in ARRR_FORMATTER.
 *
 * @param maxDecimals the number of decimal places to use in the format. Default is 6 because ARRR is
 * glorious.
 * @param minDecimals the minimum number of digits to allow to the right of the decimal.
 *
 * @return a currency formatter, appropriate for the default locale.
 */
inline fun currencyFormatter(maxDecimals: Int, minDecimals: Int): NumberFormat {
    return NumberFormat.getInstance(Locale.getDefault()).apply {
        roundingMode = ARRR_FORMATTER.roundingMode
        maximumFractionDigits = maxDecimals
        minimumFractionDigits = minDecimals
        minimumIntegerDigits = 1
    }
}

/**
 * Convert a Zatoshi value into ARRR, right-padded to the given number of fraction digits,
 * represented as a BigDecimal in order to preserve rounding that minimizes cumulative error when
 * applied repeatedly over a sequence of calculations.
 * Start with Zatoshi -> End with ARRR.
 *
 * @param scale the number of digits to the right of the decimal place. Right-padding will be added,
 * if necessary.
 *
 * @return this Long Zatoshi value represented as ARRR using a BigDecimal with the given scale,
 * rounded accurately out to 128 digits.
 */
inline fun Long?.convertZatoshiToZec(scale: Int = ARRR_FORMATTER.maximumFractionDigits): BigDecimal {
    return BigDecimal(this ?: 0L, MathContext.DECIMAL128).divide(
        Conversions.ONE_ARRR_IN_ZATOSHI,
        MathContext.DECIMAL128
    ).setScale(scale, ARRR_FORMATTER.roundingMode)
}

/**
 * Convert a ARRR value into Zatoshi.
 * Start with ARRR -> End with Zatoshi.
 *
 * @return this ARRR value represented as Zatoshi, rounded accurately out to 128 digits, in order to
 * minimize cumulative errors when applied repeatedly over a sequence of calculations.
 */
inline fun BigDecimal?.convertZecToZatoshi(): Long {
    if (this == null) return 0L
    if (this < BigDecimal.ZERO) {
        throw IllegalArgumentException(
            "Invalid ARRR value: $this. ARRR is represented by notes and" +
                " cannot be negative"
        )
    }
    return this.multiply(Conversions.ONE_ARRR_IN_ZATOSHI, MathContext.DECIMAL128).toLong()
}

/**
 * Format a Double ARRR value as a BigDecimal ARRR value, right-padded to the given number of fraction
 * digits.
 * Start with ARRR -> End with ARRR.
 *
 * @param decimals the scale to use for the resulting BigDecimal.
 *
 * @return this Double ARRR value converted into a BigDecimal, with the proper rounding mode for use
 * with other formatting functions.
 */
inline fun Double?.toZec(decimals: Int = ARRR_FORMATTER.maximumFractionDigits): BigDecimal {
    return BigDecimal(this?.toString() ?: "0.0", MathContext.DECIMAL128).setScale(
        decimals,
        ARRR_FORMATTER.roundingMode
    )
}

/**
 * Format a Double ARRR value as a Long Zatoshi value, by first converting to ARRR with the given
 * precision.
 * Start with ARRR -> End with Zatoshi.
 *
 * @param decimals the scale to use for the intermediate BigDecimal.
 *
 * @return this Double ARRR value converted into Zatoshi, with proper rounding and precision by
 * leveraging an intermediate BigDecimal object.
 */
inline fun Double?.convertZecToZatoshi(decimals: Int = ARRR_FORMATTER.maximumFractionDigits): Long {
    return this.toZec(decimals).convertZecToZatoshi()
}

/**
 * Format a BigDecimal ARRR value as a BigDecimal ARRR value, right-padded to the given number of
 * fraction digits.
 * Start with ARRR -> End with ARRR.
 *
 * @param decimals the scale to use for the resulting BigDecimal.
 *
 * @return this BigDecimal ARRR adjusted to the default scale and rounding mode.
 */
inline fun BigDecimal?.toZec(decimals: Int = ARRR_FORMATTER.maximumFractionDigits): BigDecimal {
    return (this ?: BigDecimal.ZERO).setScale(decimals, ARRR_FORMATTER.roundingMode)
}

/**
 * Format a Double USD value as a BigDecimal USD value, right-padded to the given number of fraction
 * digits.
 * Start with USD -> End with USD.
 *
 * @param decimals the scale to use for the resulting BigDecimal.
 *
 * @return this Double USD value converted into a BigDecimal, with proper rounding and precision.
 */
inline fun Double?.toUsd(decimals: Int = USD_FORMATTER.maximumFractionDigits): BigDecimal {
    return BigDecimal(this?.toString() ?: "0.0", MathContext.DECIMAL128).setScale(
        decimals,
        USD_FORMATTER.roundingMode
    )
}

/**
 * Format a BigDecimal USD value as a BigDecimal USD value, right-padded to the given number of
 * fraction digits.
 * Start with USD -> End with USD.
 *
 * @param decimals the scale to use for the resulting BigDecimal.
 *
 * @return this BigDecimal USD value converted into USD, with proper rounding and precision.
 */
inline fun BigDecimal?.toUsd(decimals: Int = USD_FORMATTER.maximumFractionDigits): BigDecimal {
    return (this ?: BigDecimal.ZERO).setScale(decimals, USD_FORMATTER.roundingMode)
}

/**
 * Convert this ARRR value to USD, using the given price per ARRR.
 * Start with ARRR -> End with USD.
 *
 * @param zecPrice the current price of ARRR represented as USD per ARRR
 *
 * @return this BigDecimal USD value converted into USD, with proper rounding and precision.
 */
inline fun BigDecimal?.convertZecToUsd(zecPrice: BigDecimal): BigDecimal {
    if (this == null) return BigDecimal.ZERO
    if (this < BigDecimal.ZERO) {
        throw IllegalArgumentException(
            "Invalid ARRR value: ${zecPrice.toDouble()}. ARRR is" +
                " represented by notes and cannot be negative"
        )
    }
    return this.multiply(zecPrice, MathContext.DECIMAL128)
}

/**
 * Convert this USD value to ARRR, using the given price per ARRR.
 * Start with USD -> End with ARRR.
 *
 * @param zecPrice the current price of ARRR represented as USD per ARRR.
 *
 * @return this BigDecimal USD value converted into ARRR, with proper rounding and precision.
 */
inline fun BigDecimal?.convertUsdToZec(zecPrice: BigDecimal): BigDecimal {
    if (this == null) return BigDecimal.ZERO
    if (this < BigDecimal.ZERO) {
        throw IllegalArgumentException(
            "Invalid USD value: ${zecPrice.toDouble()}. Converting" +
                " this would result in negative ARRR and ARRR is represented by notes and cannot be" +
                " negative"
        )
    }
    return this.divide(zecPrice, MathContext.DECIMAL128)
}

/**
 * Convert this value from one currency to the other, based on given price and whether this value is
 * USD.
 * If starting with USD -> End with ARRR.
 * If starting with ARRR -> End with USD.
 *
 * @param isUSD whether this value represents USD or not (ARRR)
 *
 * @return this BigDecimal value converted from one currency into the other, based on the given
 * price.
 */
inline fun BigDecimal.convertCurrency(zecPrice: BigDecimal, isUsd: Boolean): BigDecimal {
    return if (isUsd) {
        this.convertUsdToZec(zecPrice)
    } else {
        this.convertZecToUsd(zecPrice)
    }
}

/**
 * Parse this string into a BigDecimal, ignoring all non numeric characters.
 *
 * @return this string as a BigDecimal or null when parsing fails.
 */
inline fun String?.safelyConvertToBigDecimal(): BigDecimal? {
    if (this.isNullOrEmpty()) return BigDecimal.ZERO
    return try {
        // ignore commas and whitespace
        var sanitizedInput = this.filter { it.isDigit() or (it == '.') }
        BigDecimal.ZERO.max(BigDecimal(sanitizedInput, MathContext.DECIMAL128))
    } catch (t: Throwable) {
        return null
    }
}

/**
 * Abbreviates this string which is assumed to be an address.
 *
 * @param startLength the number of characters to show before the elipsis.
 * @param endLength the number of characters to show after the elipsis.
 *
 * @return the abbreviated string unless the string is too short, in which case the original string
 * is returned.
 */
inline fun String.toAbbreviatedAddress(startLength: Int = 8, endLength: Int = 8) =
    if (length > startLength + endLength) "${take(startLength)}…${takeLast(endLength)}" else this

/**
 * Masks the current string for use in logs. If this string appears to be an address, the last
 * [addressCharsToShow] characters will be visible.
 *
 * @param addressCharsToShow the number of chars to show at the end, if this value appears to be an
 * address.
 *
 * @return the masked version of this string, typically for use in logs.
 */
internal inline fun String.masked(addressCharsToShow: Int = 4): String =
    if (startsWith("ztest") || startsWith("zs")) "****${takeLast(addressCharsToShow)}"
    else "***masked***"

/**
 * Convenience function that returns true when this string starts with 'z'.
 *
 * @return true when this function starts with 'z' rather than 't'.
 */
inline fun String?.isShielded() = this != null && startsWith('z')