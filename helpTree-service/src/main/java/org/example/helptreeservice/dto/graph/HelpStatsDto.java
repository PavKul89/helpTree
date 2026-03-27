package org.example.helptreeservice.dto.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HelpStatsDto {
    private int totalHelps;
    private Map<String, Long> byMonth;
    private Map<String, Long> byCategory;
    private List<TopHelper> topHelpers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopHelper {
        private Long userId;
        private String name;
        private Long helpCount;
    }
}
