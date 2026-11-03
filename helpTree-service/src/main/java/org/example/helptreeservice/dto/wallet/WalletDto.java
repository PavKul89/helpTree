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
public class WalletDto {
    private Long userId;
    private Long balance;
    private Long totalEarned;
    private Long totalSpent;
    private LocalDateTime vipUntil;
    private Boolean vipActive;
    private String nicknameColor;
}
