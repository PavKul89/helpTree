package org.example.helptreeservice.dto.graph;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "HelpGraph", description = "Owner/admin graph projection with private fields")
public class HelpGraphDto {
    @ArraySchema(arraySchema = @Schema(description = "Graph nodes (users)"))
    private List<Node> nodes;

    @ArraySchema(arraySchema = @Schema(description = "Graph edges (confirmed help acts)"))
    private List<Edge> edges;

    /** Confirmed help acts in the selected subgraph, before edge limiting. */
    @Schema(description = "Confirmed help acts before maxEdges limiting", example = "120")
    private int totalHelps;

    /** Edges actually returned in {@link #edges}. */
    @Schema(description = "Edges actually returned", example = "100")
    private int returnedHelps;

    @Schema(description = "Distinct users in the subgraph", example = "42")
    private int totalUsers;

    /** True when {@code maxEdges} limited the response; never truncate silently. */
    @Schema(description = "True when maxEdges limited the response", example = "true")
    private boolean truncated;

    /** Wall-clock time spent building the projection, in milliseconds. */
    @Schema(description = "Projection build time in milliseconds", example = "18")
    private long buildTimeMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "HelpGraphNode")
    public static class Node {
        @Schema(example = "17")
        private Long id;
        @Schema(example = "Анна")
        private String name;
        private String avatarUrl;
        @Schema(example = "12")
        private Integer helpedCount;
        @Schema(example = "0")
        private Integer debtCount;
        @Schema(example = "4.8")
        private Double rating;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "HelpGraphEdge")
    public static class Edge {
        /** Help record id. Guaranteed unique per help act. */
        @Schema(description = "Help record id (unique per help act)", example = "900")
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
