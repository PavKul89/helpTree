package org.example.helptreeservice.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Result of a bulk administrative operation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBulkResultDto {
    private String action;
    private int requested;
    private int succeeded;
    private int failed;
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long id;
        private boolean success;
        private String error;
    }
}
