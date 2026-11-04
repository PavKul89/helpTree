package org.example.helptreeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Global statistics. Every help-related field is derived from the same facts
 * as ratings, achievements and the graph:
 *
 * <ul>
 *   <li>{@code helpRecords} - every non-deleted help record;</li>
 *   <li>{@code activeHelps} - ACCEPTED or COMPLETED records;</li>
 *   <li>{@code confirmedHelps} - CONFIRMED acts (also the headline
 *       {@code totalHelps});</li>
 *   <li>{@code cancelledHelps} - CANCELLED records;</li>
 *   <li>{@code successfulReceipts} - confirmed acts that a receiver accepted.
 *       One confirmed act has exactly one receiver, so this matches
 *       {@code confirmedHelps} by definition.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsDto {
    private long totalUsers;

    /** Confirmed help acts. Kept for backwards compatibility with clients. */
    private long totalHelps;

    private long helpRecords;
    private long activeHelps;
    private long confirmedHelps;
    private long cancelledHelps;
    private long successfulReceipts;

    private long activePosts;
}
