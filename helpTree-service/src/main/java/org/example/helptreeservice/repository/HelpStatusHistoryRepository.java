package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.HelpStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HelpStatusHistoryRepository extends JpaRepository<HelpStatusHistory, Long> {

    @Query("SELECT h FROM HelpStatusHistory h WHERE h.help.id = :helpId ORDER BY h.createdAt ASC, h.id ASC")
    List<HelpStatusHistory> findByHelpIdOrderByCreatedAtAsc(@Param("helpId") Long helpId);
}
