package org.example.helptreeservice.dto.reviews;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Author edit of an existing review. Both fields are optional, but at least one
 * must be present (enforced by the service so the error is a domain 400, not a
 * bean-validation message).
 */
@Data
public class UpdateReviewRequest {

    @Min(value = 1, message = "Рейтинг должен быть от 1 до 5")
    @Max(value = 5, message = "Рейтинг должен быть от 1 до 5")
    private Integer rating;

    @Size(max = 500, message = "Комментарий не должен превышать 500 символов")
    private String comment;
}
