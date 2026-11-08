package org.example.helptreeservice.dto.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Administrative wallet overview row. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminWalletAccountDto {
    private Long userId;
    private String name;
    private String email;
    private Long balance;
    private Long debtCount;
    private LocalDateTime vipUntil;
    private boolean vipActive;
    private LocalDateTime blockedUntil;
    private boolean blocked;
    private String nicknameColor;
    private LocalDateTime createdAt;
}
