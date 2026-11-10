package org.example.helptreeservice.dto.reviews;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReplyReviewRequest {

    @NotBlank(message = "Текст ответа обязателен")
    @Size(max = 1000, message = "Ответ не должен превышать 1000 символов")
    private String replyText;
}
