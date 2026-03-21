package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    List<User> findByDeletedFalse();

    @Modifying
    @Query("UPDATE User u SET u.helpedCount = u.helpedCount + 1, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :userId")
    void incrementHelpedCount(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE User u SET u.debtCount = u.debtCount + :delta, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :userId")
    void updateDebtCount(@Param("userId") Long userId, @Param("delta") int delta);
}
