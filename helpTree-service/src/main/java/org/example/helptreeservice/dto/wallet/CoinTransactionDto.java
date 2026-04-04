package org.example.helptreeservice.dto.wallet;

import org.example.helptreeservice.enums.TransactionType;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoinTransactionDto {
    private Long id;
    private TransactionType type;
    private Long amount;
    private String description;
    private Long relatedUserId;
    private Long relatedPostId;
    private LocalDateTime createdAt;
}
