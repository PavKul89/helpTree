package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.ReportEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportEventRepository extends JpaRepository<ReportEvent, Long> {

    @EntityGraph(attributePaths = {"actor"})
    List<ReportEvent> findByReportIdOrderByCreatedAtAscIdAsc(Long reportId);
}
