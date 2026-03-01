package com.example.helpTree.dto.helps;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HelpRequest {

    @NotNull(message = "ID поста обязателен")
    private Long postId;

    @NotNull(message = "ID помощника обязателен")
    private Long helperId;
}
