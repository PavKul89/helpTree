package org.example.helptreeservice.dto.posts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePostRequest {

    @NotBlank(message = "Заголовок обязателен")
    @Size(min = 3, max = 200, message = "Заголовок должен быть от 3 до 200 символов")
    private String title;

    @Size(max = 2000, message = "Описание не может быть длиннее 2000 символов")
    private String description;
}
