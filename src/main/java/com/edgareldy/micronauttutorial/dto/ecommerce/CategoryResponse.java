package com.edgareldy.micronauttutorial.dto.ecommerce;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Public view of a category.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record CategoryResponse(Long id, String categoryName) {
}
