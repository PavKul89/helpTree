package org.example.helptreeservice.dto.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.TransactionType;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletOperationResult {
    private TransactionType operation;
    private Long price;
    private Long balance;
    /** False when an idempotent retry returned an already committed operation. */
    private Boolean applied;
    private LocalDateTime expiresAt;
    private String nicknameColor;
    private Integer debtCount;
    private Boolean blocked;
    private Long transactionId;
}
