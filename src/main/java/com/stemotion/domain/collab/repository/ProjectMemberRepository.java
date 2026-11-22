package com.stemotion.domain.collab.repository;

import com.stemotion.domain.collab.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, String> {
    List<ProjectMember> findByProjectIdOrderByCreatedAtAsc(String projectId);

    Optional<ProjectMember> findByProjectIdAndUserId(String projectId, String userId);

    boolean existsByProjectId(String projectId);
}
