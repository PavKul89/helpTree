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
public class BalanceAnomalyDto {
    private Long userId;
    private Long balance;
    private Long ledgerTotal;
    private Long difference;
    private LocalDateTime detectedAt;
}
