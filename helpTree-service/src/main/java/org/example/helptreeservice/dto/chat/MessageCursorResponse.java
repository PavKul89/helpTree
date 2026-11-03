package org.example.helptreeservice.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageCursorResponse {

    private List<MessageResponse> items;
    private Long nextCursor;
    private boolean hasMore;
}
