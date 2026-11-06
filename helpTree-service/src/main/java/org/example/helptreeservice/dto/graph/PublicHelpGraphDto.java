package org.example.helptreeservice.dto.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Public help graph projection.
 *
 * <p>Only social-capital fields are exposed: display name, avatar, confirmed
 * help count and rating. The debt counter, the specific post and the internal
 * help status are private and are returned only through
 * {@link HelpGraphDto} for the graph owner or an admin.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicHelpGraphDto {
    private List<Node> nodes;
    private List<Edge> edges;

    private int totalHelps;
    private int returnedHelps;
    private int totalUsers;
    private boolean truncated;
    private long buildTimeMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Node {
        private Long id;
        private String name;
        private String avatarUrl;
        private Integer helpedCount;
        private Double rating;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Edge {
        /** Help record id. */
        private Long id;
        private Long fromUserId;
        private String fromUserName;
        private Long toUserId;
        private String toUserName;
        private LocalDateTime confirmedAt;
    }
}
