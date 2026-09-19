package com.edgareldy.micronauttutorial.dto.ecommerce;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body of order creation. The total is never supplied, it is computed by the service.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record OrderRequest(
        @NotNull(message = "must not be null") Long customerId,
        @NotNull(message = "must not be null") Long productId,
        @NotNull(message = "must not be null") @Min(value = 1, message = "must be at least 1")
        @Max(value = 100000, message = "must be at most 100000") Integer quantity
) {
}
