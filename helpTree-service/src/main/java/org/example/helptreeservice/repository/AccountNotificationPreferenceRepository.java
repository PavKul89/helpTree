package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.AccountNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Account-wide notification settings from the legacy Block 14 API. */
@Repository
public interface AccountNotificationPreferenceRepository
        extends JpaRepository<AccountNotificationPreference, Long> {
}
