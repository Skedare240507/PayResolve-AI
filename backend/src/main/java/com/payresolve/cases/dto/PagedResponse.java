package com.payresolve.cases.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Generic container for paginated query responses.
 */
public record PagedResponse<T>(
    @JsonProperty("content") List<T> content,
    @JsonProperty("page") int page,
    @JsonProperty("size") int size,
    @JsonProperty("totalElements") long totalElements,
    @JsonProperty("totalPages") int totalPages
) {
    public static <T> PagedResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PagedResponse<>(content, page, size, totalElements, totalPages);
    }
}
