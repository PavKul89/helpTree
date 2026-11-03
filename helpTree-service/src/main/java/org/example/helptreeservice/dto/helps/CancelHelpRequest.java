package org.example.helptreeservice.dto.helps;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelHelpRequest {

    /**
     * Optional for participants, required for an administrative cancellation.
     * Stored on the help and in the status history.
     */
    @Size(max = 500, message = "Причина отмены не может быть длиннее 500 символов")
    private String reason;
}
