package com.example.helpTree.dto.posts;

import com.example.helpTree.enums.PostStatus;
import lombok.Data;
import jakarta.validation.constraints.Size;

@Data
public class UpdatePostRequest {

    @Size(min = 3, max = 200, message = "Заголовок должен быть от 3 до 200 символов")
    private String title;

    @Size(max = 2000, message = "Описание не может быть длиннее 2000 символов")
    private String description;

    @Size(min = 2, max = 100, message = "Имя должно быть от 2 до 100 символов")
    private String authorName;

    private PostStatus status;
}
