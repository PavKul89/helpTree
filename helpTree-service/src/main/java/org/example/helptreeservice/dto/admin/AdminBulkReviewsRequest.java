package org.example.helptreeservice.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** Bulk review removal (the only bulk operation currently supported). */
@Data
public class AdminBulkReviewsRequest {

    @NotEmpty(message = "Список отзывов не может быть пустым")
    private List<Long> ids;

    @NotBlank(message = "Причина обязательна")
    private String reason;
}
