package org.example.helptreeservice.dto.helps;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NewResponseDto {
    private Long helpId;
    private String postTitle;
    private Long postId;
    private String helperName;
    private String createdAt;
}
