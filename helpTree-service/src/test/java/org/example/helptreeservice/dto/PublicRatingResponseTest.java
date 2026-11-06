package org.example.helptreeservice.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PublicRatingResponseTest {

    @Test
    void publicProjectionDropsInternalComponentsAndRawCounters() {
        RatingResponse full = RatingResponse.builder()
                .userId(7L)
                .userName("Helper")
                .overallRating(4.5)
                .ratingComponents(Map.of("avgResponseTime", 1.0))
                .level("EXCELLENT")
                .totalHelps(10L)
                .totalHelpRecords(12L)
                .activeHelps(1L)
                .cancelledHelps(1L)
                .totalReceivedHelps(2L)
                .reviewCount(3L)
                .averageReviewRating(4.7)
                .successRate(90.0)
                .build();

        PublicRatingResponse publicRating = PublicRatingResponse.from(full);

        assertEquals(7L, publicRating.getUserId());
        assertEquals("Helper", publicRating.getUserName());
        assertEquals(4.5, publicRating.getOverallRating());
        assertEquals(10L, publicRating.getTotalHelps());
        assertEquals(2L, publicRating.getTotalReceivedHelps());
        assertEquals(3L, publicRating.getReviewCount());
        assertEquals(4.7, publicRating.getAverageReviewRating());
        assertEquals(90.0, publicRating.getSuccessRate());
        assertNull(PublicRatingResponse.from(null));
    }
}
