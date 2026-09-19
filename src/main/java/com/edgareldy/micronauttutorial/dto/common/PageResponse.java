package com.edgareldy.micronauttutorial.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.data.model.Page;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.function.Function;

/**
 * Paginated list payload: the page content plus the paging metadata.
 * Built with {@link #of(List, int, int, long)} or {@link #from(Page, Function)}.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record PageResponse<T>(
        @JsonInclude(JsonInclude.Include.ALWAYS) List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * Builds a page response and derives totalPages.
     *
     * @throws IllegalArgumentException if size is lower than 1, page is negative or total is negative
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1 but was " + size);
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0 but was " + page);
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must be >= 0 but was " + total);
        }
        int totalPages = (int) ((total + size - 1) / size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }

    /**
     * Converts a Micronaut Data page of entities into a page of DTOs.
     */
    public static <E, T> PageResponse<T> from(Page<E> source, Function<E, T> mapper) {
        List<T> content = source.getContent().stream().map(mapper).toList();
        return of(content, source.getPageNumber(), source.getSize(), source.getTotalSize());
    }
}
