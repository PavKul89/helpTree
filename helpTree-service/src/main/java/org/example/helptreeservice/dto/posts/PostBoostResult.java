package org.example.helptreeservice.dto.posts;

import java.time.LocalDateTime;

public record PostBoostResult(
        PostDto post,
        long price,
        long balance,
        boolean applied,
        LocalDateTime boostedUntil
) {
}
