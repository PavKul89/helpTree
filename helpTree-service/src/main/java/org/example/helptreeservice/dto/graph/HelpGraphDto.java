package org.example.helptreeservice.dto.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Help graph contract.
 *
 * <p>Nodes are users, edges are confirmed non-deleted help acts directed from
 * the helper to the receiver. {@link Edge#getId()} is the help id, never a user
 * id: the same helper may have many separate edges.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HelpGraphDto {
    private List<Node> nodes;
    private List<Edge> edges;

    /** Confirmed help acts in the selected subgraph, before edge limiting. */
    private int totalHelps;

    /** Edges actually returned in {@link #edges}. */
    private int returnedHelps;

    private int totalUsers;

    /** True when {@code maxEdges} limited the response; never truncate silently. */
    private boolean truncated;

    /** Wall-clock time spent building the projection, in milliseconds. */
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
        private Integer debtCount;
        private Double rating;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Edge {
        /** Help record id. Guaranteed unique per help act. */
        private Long id;
        private Long fromUserId;
        private String fromUserName;
        private Long toUserId;
        private String toUserName;
        private Long postId;
        private String postTitle;
        private String status;
        private LocalDateTime confirmedAt;
    }
}
