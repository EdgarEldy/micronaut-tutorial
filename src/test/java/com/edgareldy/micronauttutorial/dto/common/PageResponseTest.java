package com.edgareldy.micronauttutorial.dto.common;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests of PageResponse: page arithmetic, argument rejection and Micronaut Data Page conversion.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
class PageResponseTest {

    @Test
    void totalZeroGivesZeroPages() {
        PageResponse<String> page = PageResponse.of(List.of(), 0, 10, 0);
        assertEquals(0, page.totalPages());
        assertEquals(0, page.totalElements());
    }

    @Test
    void exactMultipleDoesNotAddAnExtraPage() {
        assertEquals(2, PageResponse.of(List.of("a"), 0, 10, 20).totalPages());
    }

    @Test
    void remainderAddsOnePage() {
        assertEquals(3, PageResponse.of(List.of("a"), 0, 10, 21).totalPages());
        assertEquals(1, PageResponse.of(List.of("a"), 0, 10, 1).totalPages());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> PageResponse.of(List.of(), 0, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> PageResponse.of(List.of(), 0, -1, 5));
        assertThrows(IllegalArgumentException.class, () -> PageResponse.of(List.of(), -1, 10, 5));
        assertThrows(IllegalArgumentException.class, () -> PageResponse.of(List.of(), 0, 10, -1));
    }

    @Test
    void fromMapsContentAndCopiesPagingMetadata() {
        // Page.of builds a Micronaut Data page by hand, so no repository is needed here.
        Page<Integer> source = Page.of(List.of(1, 2, 3), Pageable.from(1, 3), 7L);

        PageResponse<String> page = PageResponse.from(source, i -> "n" + i);

        assertEquals(List.of("n1", "n2", "n3"), page.content());
        assertEquals(1, page.page());
        assertEquals(3, page.size());
        assertEquals(7, page.totalElements());
        assertEquals(3, page.totalPages());
    }
}
