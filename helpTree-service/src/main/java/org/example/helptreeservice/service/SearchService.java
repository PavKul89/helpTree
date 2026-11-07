package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.search.SearchResultDto;
import org.example.helptreeservice.enums.SearchType;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.repository.SearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * Unified search over posts, public users and reviews.
 *
 * <p>Visibility rules are enforced in the SQL projection: soft-deleted rows,
 * cancelled posts, emails/phones/coordinates and moderation flags are never
 * part of a hit. There is no separate publication table, so {@code POST} is the
 * publication category. Reviews have no city/category, therefore those filters
 * intentionally narrow the result set to posts/users.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private static final String NEWEST = "newest";
    private static final String OLDEST = "oldest";

    private final SearchRepository searchRepository;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public PageResponse<SearchResultDto> search(String query,
                                                SearchType type,
                                                String city,
                                                String category,
                                                String sort,
                                                Pageable pageable) {
        String pattern = likePattern(query);
        if (pattern == null) {
            throw new BadRequestException("Параметр q обязателен");
        }

        String normalizedSort = sort == null ? NEWEST : sort.trim().toLowerCase(Locale.ROOT);
        if (!NEWEST.equals(normalizedSort) && !OLDEST.equals(normalizedSort)) {
            throw new BadRequestException("Параметр sort поддерживает только newest или oldest");
        }

        SearchType resolvedType = type != null ? type : SearchType.ALL;
        String cityPattern = likePatternOrEmpty(city);
        String categoryPattern = likePatternOrEmpty(category);
        // A real user id filters blocked users/reviews in SQL. Anonymous callers
        // use -1 (no row can match a positive id), keeping the parameter type
        // stable for the native query.
        AuthorizationService.UserContext viewer = authorizationService.getCurrentUser();
        Long viewerId = viewer != null ? viewer.getUserId() : -1L;

        Page<Object[]> rows = OLDEST.equals(normalizedSort)
                ? searchRepository.searchOldest(pattern, cityPattern, categoryPattern,
                        resolvedType.name(), viewerId, pageable)
                : searchRepository.searchNewest(pattern, cityPattern, categoryPattern,
                        resolvedType.name(), viewerId, pageable);

        log.debug("Unified search: type={}, sort={}, total={}", resolvedType, normalizedSort,
                rows.getTotalElements());
        return PageResponse.from(rows.map(this::toDto));
    }

    private SearchResultDto toDto(Object[] row) {
        String type = (String) row[0];
        Long id = asLong(row[1]);
        Long ownerId = asLong(row[8]);

        return SearchResultDto.builder()
                .type(type)
                .id(id)
                .title((String) row[2])
                .snippet(truncate((String) row[3], 240))
                .category((String) row[4])
                .city((String) row[5])
                .rating(row[6] != null ? ((Number) row[6]).doubleValue() : null)
                .createdAt(asDateTime(row[7]))
                .ownerId(ownerId)
                .link(link(type, id, ownerId))
                .build();
    }

    private String link(String type, Long id, Long ownerId) {
        if ("POST".equals(type)) {
            return "/posts/" + id;
        }
        if ("REVIEW".equals(type)) {
            return ownerId != null ? "/profile/" + ownerId : "/users";
        }
        return "/profile/" + id;
    }

    private String likePattern(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        // Wildcards are stripped rather than escaped: search must stay a literal
        // substring match, and this keeps every native query free of ESCAPE
        // clauses.
        String literal = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("%", "")
                .replace("_", "");
        return literal.isEmpty() ? null : "%" + literal + "%";
    }

    private String likePatternOrEmpty(String value) {
        String pattern = likePattern(value);
        return pattern != null ? pattern : "";
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private LocalDateTime asDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toLocalDateTime();
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "…";
    }
}
