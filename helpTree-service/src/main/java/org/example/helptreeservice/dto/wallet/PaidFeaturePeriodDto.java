package org.example.helptreeservice.dto.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.PaidFeatureType;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaidFeaturePeriodDto {
    private Long id;
    private PaidFeatureType feature;
    private Long referenceId;
    private LocalDateTime startedAt;
    private LocalDateTime endsAt;
    private boolean active;
}
