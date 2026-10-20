package com.stemotion.domain.project.repository;

import com.stemotion.domain.project.entity.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatusHistoryRepository extends JpaRepository<StatusHistory, String> {
    List<StatusHistory> findByProjectIdOrderByCreatedAtDesc(String projectId);

    /** One kind of event of a project, newest first, with the user who made it. */
    @org.springframework.data.jpa.repository.Query("select h from StatusHistory h join fetch h.changedBy where h.project.id = :projectId "
        + "and h.eventType = :type order by h.createdAt desc")
    List<StatusHistory> findEventsWithActor(@org.springframework.data.repository.query.Param("projectId") String projectId,
                                            @org.springframework.data.repository.query.Param("type") StatusHistory.EventType type);
}
