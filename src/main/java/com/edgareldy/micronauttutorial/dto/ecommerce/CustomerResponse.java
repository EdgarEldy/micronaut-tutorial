package com.edgareldy.micronauttutorial.dto.ecommerce;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Public view of a customer; optional fields are always serialized, as null when absent.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record CustomerResponse(
        Long id,
        String firstName,
        String lastName,
        @JsonInclude(JsonInclude.Include.ALWAYS) String telephone,
        @JsonInclude(JsonInclude.Include.ALWAYS) String email,
        @JsonInclude(JsonInclude.Include.ALWAYS) String address
) {
}
