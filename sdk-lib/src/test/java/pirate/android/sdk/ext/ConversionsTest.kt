package pirate.android.sdk.ext

import pirate.android.sdk.model.Arrrtoshi
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.MathContext
import kotlin.test.assertEquals

class ConversionsTest {
    
    private val arrrPrice = BigDecimal("1.0") // Test price: 1 USD per ARRR

    @Test
    fun `default right padding is 6`() {
        assertEquals(1.13.toArrr(6), Arrrtoshi(113000000L).convertArrrtoshiToArrr())
        assertEquals(1.13.toArrr(6), 1.13.toArrr())
    }

    @Test
    fun `toZec uses banker's rounding`() {
        assertEquals("1.004", 1.0035.toArrrString(3))
        assertEquals("1.004", 1.0045.toArrrString(3))
    }

    @Test
    fun `toArrrString defaults to 6 digits`() {
        assertEquals("1.123457", Arrrtoshi(112345678L).convertArrrtoshiToArrrString())
    }

    @Test
    fun `toArrrString uses banker's rounding`() {
        assertEquals("1.123456", Arrrtoshi(112345650L).convertArrrtoshiToArrrString())
    }

    @Test
    fun `toArrrString honors minimum digits`() {
        assertEquals("1.1000", 1.1.toArrrString(6, 4))
    }

    @Test
    fun `toArrrString drops trailing zeros`() {
        assertEquals("1.1", 1.10000000.toArrrString(6, 0))
    }

    @Test
    fun `toArrrString limits trailing zeros`() {
        assertEquals("1.10", 1.10000000.toArrrString(6, 2))
    }

    @Test
    fun `toArrrString hides decimal when min is zero`() {
        assertEquals("1", 1.0.toArrrString(6, 0))
    }

    @Test
    fun `toArrrString defaults are reasonable`() {
        // basically check for no extra zeros and banker's rounding
        assertEquals("1", 1.0000000.toArrrString())
        assertEquals("0", 0.0000000.toArrrString())
        assertEquals("1.01", 1.0100000.toArrrString())
        assertEquals("1.000004", 1.0000035.toArrrString())
        assertEquals("1.000004", 1.0000045.toArrrString())
        assertEquals("1.000006", 1.0000055.toArrrString())
    }

    @Test
    fun `toUsdString defaults are reasonable`() {
        // basically check for no extra zeros and banker's rounding
        assertEquals("1.00", 1.0000000.toUsdString())
        assertEquals("0", 0.0000000.toUsdString())
        assertEquals("1.01", 1.0100000.toUsdString())
        assertEquals("0.01", .0100000.toUsdString())
        assertEquals("1.02", 1.025.toUsdString())
    }

    @Test
    fun `toArrrString zatoshi converts`() {
        assertEquals("1.123456", Arrrtoshi(112345650L).convertArrrtoshiToArrrString(6, 0))
    }

    @Test
    fun `toArrrString big decimal formats`() {
        assertEquals("1.123", BigDecimal(1.123456789).toArrrString(3, 0))
    }

    @Test
    fun `toArrr reduces precision with rounding up`() {
        val amount = "20.37905555555555555555555555555555".safelyConvertToBigDecimal()
        val expected = "20.379056".safelyConvertToBigDecimal()
        assertEquals(expected, amount.toArrr(6))
    }

    @Test
    fun `toArrrString() + convertUsdToArrr()`() {
        assertEquals("113", "$113".safelyConvertToBigDecimal().convertUsdToArrr(arrrPrice).toArrrString())
    }
}
