package com.edgareldy.micronauttutorial.dto.ecommerce;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of product creation and update.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record ProductRequest(
        @NotNull(message = "must not be null") Long categoryId,
        @NotBlank(message = "must not be blank") @Size(max = 150, message = "must be at most 150 characters") String productName,
        @NotNull(message = "must not be null") @Positive(message = "must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "must have at most 10 integer and 2 fraction digits") BigDecimal unitPrice
) {
}
