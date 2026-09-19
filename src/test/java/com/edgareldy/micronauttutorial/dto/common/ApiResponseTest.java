package com.edgareldy.micronauttutorial.dto.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests of the ApiResponse envelope shape (no application context needed).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
class ApiResponseTest {

    @Test
    void successCarriesDataMessageAndTimestamp() {
        Instant before = Instant.now();
        ApiResponse<String> response = ApiResponse.success("payload", "done");

        assertTrue(response.success());
        assertEquals("done", response.message());
        assertEquals("payload", response.data());
        assertNotNull(response.timestamp());
        assertFalse(response.timestamp().isBefore(before));
    }

    @Test
    void errorHasSuccessFalseAndNullData() {
        ApiResponse<Void> response = ApiResponse.error("boom");

        assertFalse(response.success());
        assertEquals("boom", response.message());
        assertNull(response.data());
        assertNotNull(response.timestamp());
    }
}
