package com.enterpriseai.ticket.dto;

import java.util.List;

/**
 * Cursor-based pagination wrapper.
 *
 * @param <T> The item type in this page.
 */
public record PageResponse<T>(
        List<T> items,
        int pageSize,
        long totalElements,
        int totalPages,
        int currentPage,
        boolean hasNext,
        boolean hasPrevious
) {}

