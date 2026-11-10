package org.example.helptreeservice.dto.chat;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMessageRequest {

    @Size(max = 5000, message = "Сообщение слишком длинное")
    private String content;

    /** Optional image URLs uploaded by the sender. At most three. */
    @Size(max = 3, message = "Можно прикрепить не более 3 изображений")
    private List<String> attachmentUrls;
}
