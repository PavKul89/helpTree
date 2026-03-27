package org.example.helptreeservice.dto.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HelpGraphDto {
    private List<Node> nodes;
    private List<Edge> edges;
    private int totalHelps;
    private int totalUsers;

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
        private Long id;
        private Long fromUserId;
        private String fromUserName;
        private Long toUserId;
        private String toUserName;
        private String postTitle;
        private String status;
        private LocalDateTime confirmedAt;
    }
}
