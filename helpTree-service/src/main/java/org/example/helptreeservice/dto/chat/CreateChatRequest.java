package org.example.helptreeservice.dto.chat;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateChatRequest {

    @NotNull(message = "ID пользователя обязательно")
    private Long participantId;
}
