package org.example.helptreeservice.dto.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shared pagination envelope used by every large list endpoint in
 * Block 9: the six contract fields must always be populated from the Page.
 */
class PageResponseTest {

    @Test
    void fromExposesCanonicalContractFields() {
        Page<String> page = new PageImpl<>(
                List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertEquals(List.of("a", "b"), response.getContent());
        assertEquals(1, response.getNumber());
        assertEquals(2, response.getSize());
        assertEquals(5, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertTrue(response.isHasNext());
        assertEquals(2, response.getNumberOfElements());
        assertFalse(response.isFirst());
        assertFalse(response.isLast());
        assertFalse(response.isEmpty());
    }

    @Test
    void fromKeepsEmptyPageConsistent() {
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        PageResponse<String> response = PageResponse.from(page);

        assertTrue(response.getContent().isEmpty());
        assertEquals(0, response.getTotalElements());
        assertEquals(0, response.getTotalPages());
        assertFalse(response.isHasNext());
        assertTrue(response.isFirst());
        assertTrue(response.isLast());
        assertTrue(response.isEmpty());
    }
}
