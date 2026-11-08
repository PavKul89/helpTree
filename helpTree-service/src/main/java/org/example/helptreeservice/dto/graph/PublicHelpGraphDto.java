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
@Schema(name = "PublicHelpGraph", description = "Anonymised public graph projection")
public class PublicHelpGraphDto {
    @ArraySchema(arraySchema = @Schema(description = "Public graph nodes"))
    private List<Node> nodes;

    @ArraySchema(arraySchema = @Schema(description = "Public graph edges"))
    private List<Edge> edges;

    @Schema(example = "120")
    private int totalHelps;
    @Schema(example = "100")
    private int returnedHelps;
    @Schema(example = "42")
    private int totalUsers;
    @Schema(example = "true")
    private boolean truncated;
    @Schema(example = "18")
    private long buildTimeMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "PublicHelpGraphNode")
    public static class Node {
        @Schema(example = "17")
        private Long id;
        @Schema(example = "Анна")
        private String name;
        private String avatarUrl;
        @Schema(example = "12")
        private Integer helpedCount;
        @Schema(example = "4.8")
        private Double rating;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "PublicHelpGraphEdge")
    public static class Edge {
        /** Help record id. */
        @Schema(description = "Help record id", example = "900")
        private Long id;
        private Long fromUserId;
        private String fromUserName;
        private Long toUserId;
        private String toUserName;
        private LocalDateTime confirmedAt;
    }
}
