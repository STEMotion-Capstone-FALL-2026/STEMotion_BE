package com.stemotion.domain.collab.repository;

import com.stemotion.domain.collab.entity.CollabEnums.ProposalStatus;
import com.stemotion.domain.collab.entity.ProjectProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectProposalRepository extends JpaRepository<ProjectProposal, String> {
    List<ProjectProposal> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);

    List<ProjectProposal> findByWorkspaceIdInAndStatusOrderByCreatedAtAsc(Collection<String> workspaceIds, ProposalStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProjectProposal p where p.id = :id")
    Optional<ProjectProposal> lockById(@Param("id") String id);
}
