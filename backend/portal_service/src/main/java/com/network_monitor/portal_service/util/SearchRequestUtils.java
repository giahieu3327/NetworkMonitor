
package com.network_monitor.portal_service.util;

import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class SearchRequestUtils {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_DIRECTION = "DESC";

    private SearchRequestUtils() {
    }

    public static int normalizePage(SearchRequest request) {
        if (request == null || request.getPage() < 0) {
            return DEFAULT_PAGE;
        }

        return request.getPage();
    }

    public static int normalizeSize(SearchRequest request) {
        if (request == null || request.getSize() <= 0) {
            return DEFAULT_SIZE;
        }

        return Math.min(request.getSize(), MAX_SIZE);
    }

    public static String normalizeKeyword(SearchRequest request) {
        if (request == null || request.getKeyword() == null) {
            return null;
        }

        String keyword = request.getKeyword().trim();
        return keyword.isEmpty() ? null : keyword;
    }

    public static String normalizeSortBy(
            SearchRequest request,
            String defaultSortBy) {

        if (request == null || request.getSortBy() == null) {
            return defaultSortBy;
        }

        String sortBy = request.getSortBy().trim();

        return sortBy.isEmpty() ? defaultSortBy : sortBy;
    }

    public static String normalizeDirection(SearchRequest request) {
        if (request == null || request.getDirection() == null) {
            return DEFAULT_DIRECTION;
        }

        String direction = request.getDirection().trim();

        if ("ASC".equalsIgnoreCase(direction)) {
            return "ASC";
        }

        if ("DESC".equalsIgnoreCase(direction)) {
            return "DESC";
        }

        return DEFAULT_DIRECTION;
    }

    public static Pageable toPageable(
            int page,
            int size,
            String sortBy,
            String direction) {

        Sort.Direction sortDirection =
                "ASC".equalsIgnoreCase(direction)
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        return PageRequest.of(
                page,
                size,
                Sort.by(sortDirection, sortBy)
        );
    }
}
