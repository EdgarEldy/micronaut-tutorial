package com.edgareldy.micronauttutorial.dto.ecommerce;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;

/**
 * Public view of an order.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record OrderResponse(Long id, Long customerId, Long productId, int quantity, BigDecimal total) {
}
