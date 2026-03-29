package org.example.helptreeservice.dto.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateUserRequest {

    @Size(min = 2, max = 100, message = "Имя должно быть от 2 до 100 символов")
    private String name;

    @Email(message = "Некорректный email")
    private String email;

    @Size(min = 10, max = 20, message = "Телефон должен быть от 10 до 20 символов")
    private String phone;

    private String city;

    private Double latitude;
    private Double longitude;

    private LocalDateTime birthDate;
}
