package com.example.cilab

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The one piece of "business logic" this lab is built around.
 *
 * Applies a tiered discount to an order subtotal:
 *   - subtotal >= 200  -> 20% off
 *   - subtotal >= 100  -> 10% off
 *   - otherwise        -> no discount
 *
 * The tier boundaries (100 and 200) are exactly where bugs love to hide,
 * which is what makes them good targets for unit tests.
 */
@Service
class PricingService {

    fun applyDiscount(subtotal: BigDecimal): BigDecimal {
        require(subtotal >= BigDecimal.ZERO) { "subtotal must not be negative" }

        val rate = when {
            subtotal >= BigDecimal(200) -> BigDecimal("0.20")
            subtotal >= BigDecimal(100) -> BigDecimal("0.10")
            else -> BigDecimal.ZERO
        }

        val total = subtotal.subtract(subtotal.multiply(rate))
        return total.setScale(2, RoundingMode.HALF_UP)
    }
}
