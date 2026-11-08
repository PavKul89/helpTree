package org.example.helptreeservice.dto.graph;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "HelpStats", description = "Public help statistics")
public class HelpStatsDto {
    /** Confirmed help acts: the same set used by the graph and achievements. */
    @Schema(description = "Confirmed help acts", example = "245")
    private int totalHelps;

    @Schema(example = "260")
    private int totalHelpRecords;
    @Schema(example = "12")
    private int activeHelps;
    @Schema(example = "3")
    private int cancelledHelps;

    @Schema(description = "Confirmed helps grouped by month (YYYY-MM)", example = "{\"2026-01\": 12}")
    private Map<String, Long> byMonth;

    @Schema(description = "Confirmed helps grouped by category", example = "{\"REPAIR\": 20}")
    private Map<String, Long> byCategory;

    @ArraySchema(arraySchema = @Schema(description = "Top helpers"))
    private List<TopHelper> topHelpers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "HelpStatsTopHelper")
    public static class TopHelper {
        private Long userId;
        private String name;
        @Schema(example = "31")
        private Long helpCount;
    }
}
