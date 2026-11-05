package org.example.helptreeservice.dto.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One unified-search hit. Only public fields are exposed; private data such as
 * email, phone, coordinates and moderation flags never leaves the service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResultDto {

    /** POST, USER or REVIEW. */
    private String type;
    private Long id;
    private String title;
    private String snippet;
    private String category;
    private String city;
    private Double rating;
    private LocalDateTime createdAt;
    /** Author/recipient id used to build a public route for the hit. */
    private Long ownerId;
    /** Relative frontend route for the hit. */
    private String link;
}
