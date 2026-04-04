package org.example.helptreeservice.dto.wallet;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletDto {
    private Long userId;
    private Long balance;
    private Long totalEarned;
    private Long totalSpent;
}
