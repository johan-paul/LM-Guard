package com.lmguard.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A stable pagination shape. Spring's {@code Page} serialises with an unstable internal
 * structure, so it is never returned directly across the API boundary.
 */
@Schema(name = "PageResponse", description = "A page of results")
public record PageResponse<T>(

        @Schema(description = "Items on this page")
        List<T> items,

        @Schema(description = "Zero-based page index", example = "0")
        int page,

        @Schema(description = "Requested page size", example = "20")
        int size,

        @Schema(description = "Total matching items across all pages", example = "137")
        long totalItems,

        @Schema(description = "Total number of pages", example = "7")
        int totalPages,

        @Schema(description = "True when this is the last page", example = "false")
        boolean last
) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return from(page, Function.identity());
    }
}
