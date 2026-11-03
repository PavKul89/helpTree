package org.example.helptreeservice.dto.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCoinAuditDto {
    private Long id;
    private Long adminId;
    private Long userId;
    private Long amount;
    private Long balanceBefore;
    private Long balanceAfter;
    private String reason;
    private Long transactionId;
    private LocalDateTime createdAt;
}
