package com.edgareldy.micronauttutorial.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/**
 * Standard envelope for every API response, successes and errors alike.
 * Errors are built with {@link #error(String)}: success is false and data is null.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// @Serdeable makes the annotation processor generate the (de)serializer at compile time, so Micronaut
// Serde needs no reflection to write this record as JSON.
@Serdeable
public record ApiResponse<T>(
        boolean success,
        String message,
        @JsonInclude(JsonInclude.Include.ALWAYS) T data,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }
}
