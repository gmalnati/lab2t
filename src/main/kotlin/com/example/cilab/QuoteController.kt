package com.example.cilab

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

data class QuoteRequest(val subtotal: BigDecimal)

data class QuoteResponse(val subtotal: BigDecimal, val total: BigDecimal)

@RestController
class QuoteController(private val pricingService: PricingService) {

    @PostMapping("/quote")
    fun quote(@RequestBody request: QuoteRequest): QuoteResponse {
        val total = pricingService.applyDiscount(request.subtotal)
        return QuoteResponse(subtotal = request.subtotal, total = total)
    }
}
