package org.example.helptreeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.search.SearchResultDto;
import org.example.helptreeservice.enums.SearchType;
import org.example.helptreeservice.service.SearchService;
import org.example.helptreeservice.util.PageRequests;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One endpoint for every searchable public category.
 *
 * <p>Limitations are explicit: only posts, public users and reviews exist in
 * this service; help acts are not a public catalogue and are therefore not
 * exposed here. Deep pagination is supported, but the database still sorts the
 * whole match set, so callers should narrow the query instead of walking many
 * pages.</p>
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search & Stats", description = "Поиск и публичная статистика")
public class SearchController {

    private final SearchService searchService;
    private final PaginationConfig paginationConfig;

    @GetMapping
    @Operation(summary = "Поиск по публичным сущностям (постранично)",
            description = "type=POSTS|USERS|REVIEWS|ALL. Ответ — PageResponse. "
                    + "Глубокая пагинация поддерживается, но лучше сужать запрос.")
    @SecurityRequirements
    public ResponseEntity<PageResponse<SearchResultDto>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "ALL") SearchType type,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(searchService.search(
                q,
                type,
                city,
                category,
                sort,
                PageRequests.of(page, size, paginationConfig, Sort.unsorted())));
    }
}
