package com.stemotion.domain.collab.repository;

import com.stemotion.domain.collab.entity.CollabEnums.JoinRequestStatus;
import com.stemotion.domain.collab.entity.ProjectJoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectJoinRequestRepository extends JpaRepository<ProjectJoinRequest, String> {
    List<ProjectJoinRequest> findByProjectIdOrderByCreatedAtDesc(String projectId);

    List<ProjectJoinRequest> findByProjectIdInAndStatusOrderByCreatedAtAsc(Collection<String> projectIds, JoinRequestStatus status);

    boolean existsByProjectIdAndUserIdAndStatus(String projectId, String userId, JoinRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ProjectJoinRequest r where r.id = :id")
    Optional<ProjectJoinRequest> lockById(@Param("id") String id);
}
