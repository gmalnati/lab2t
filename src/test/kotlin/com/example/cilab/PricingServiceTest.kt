package com.example.cilab

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PricingServiceTest {

    private val pricingService = PricingService()

    @Test
    fun `applies 10 percent discount exactly at the 100 boundary`() {
        // Arrange
        val subtotal = BigDecimal("100.00")

        // Act
        val total = pricingService.applyDiscount(subtotal)

        // Assert
        assertEquals(BigDecimal("90.00"), total)
    }
}
