package org.example.helptreeservice.dto.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Manual rating override with a mandatory audit reason. */
@Data
public class AdminRatingRequest {

    @NotNull(message = "Рейтинг обязателен")
    @DecimalMin(value = "0.0", message = "Рейтинг не может быть отрицательным")
    @DecimalMax(value = "5.0", message = "Рейтинг не может быть больше 5")
    private Double rating;

    @NotBlank(message = "Причина обязательна")
    private String reason;
}
