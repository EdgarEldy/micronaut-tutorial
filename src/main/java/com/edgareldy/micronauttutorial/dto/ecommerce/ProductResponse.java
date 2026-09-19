package com.edgareldy.micronauttutorial.dto.ecommerce;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;

/**
 * Public view of a product. Carries the category id only, never its name: this DTO is cached, and a category rename would leave a copied name stale.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record ProductResponse(Long id, Long categoryId, String productName, BigDecimal unitPrice) {
}
