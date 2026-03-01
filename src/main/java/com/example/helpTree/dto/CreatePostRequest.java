package com.example.helpTree.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePostRequest {

    @NotNull(message = "ID пользователя обязателен")
    private Long userId;

    @NotBlank(message = "Заголовок обязателен")
    @Size(min = 3, max = 200, message = "Заголовок должен быть от 3 до 200 символов")
    private String title;

    @Size(max = 2000, message = "Описание не может быть длиннее 2000 символов")
    private String description;

    @NotBlank(message = "Имя автора обязательно")
    @Size(min = 2, max = 100, message = "Имя должно быть от 2 до 100 символов")
    private String authorName;
}
