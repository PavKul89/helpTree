package org.example.helptreeservice.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMessageWsRequest {

    @NotBlank(message = "Сообщение не может быть пустым")
    @Size(max = 5000, message = "Сообщение слишком длинное")
    private String content;
}
