package org.example.helptreeservice.util;

import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Single place for request pagination validation. Controllers must never pass
 * an unbounded size to a repository.
 */
public final class PageRequests {

    private PageRequests() {}

    public static Pageable of(Integer page, Integer size, PaginationConfig config, Sort sort) {
        return of(page, size, config.getDefaultPageSize(), config.getMaxPageSize(), sort);
    }

    public static Pageable of(Integer page,
                              Integer size,
                              int defaultSize,
                              int maxSize,
                              Sort sort) {
        int resolvedSize = size != null ? size : defaultSize;
        if (resolvedSize < 1) {
            throw new BadRequestException("Параметр size должен быть положительным");
        }
        resolvedSize = Math.min(resolvedSize, maxSize);

        int resolvedPage = page != null ? page : 0;
        if (resolvedPage < 0) {
            throw new BadRequestException("Параметр page не может быть отрицательным");
        }

        return PageRequest.of(resolvedPage, resolvedSize, sort);
    }
}
