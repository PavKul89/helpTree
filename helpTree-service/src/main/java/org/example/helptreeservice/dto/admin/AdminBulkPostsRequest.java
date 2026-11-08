package org.example.helptreeservice.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Bulk administrative operation over request ids. The server applies every
 * item independently and reports per-item results; one bad id never rolls back
 * the successful ones.
 */
@Data
public class AdminBulkPostsRequest {

    /** DELETE, RESTORE or STATUS. */
    @NotBlank(message = "Операция обязательна")
    private String action;

    @NotEmpty(message = "Список заявок не может быть пустым")
    private List<Long> ids;

    /** Only used with action=STATUS. */
    private String status;

    @NotBlank(message = "Причина обязательна")
    private String reason;
}
