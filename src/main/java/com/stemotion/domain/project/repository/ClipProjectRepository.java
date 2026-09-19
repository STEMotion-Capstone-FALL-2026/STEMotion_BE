package com.stemotion.domain.project.repository;

import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.entity.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClipProjectRepository extends JpaRepository<ClipProject, String> {

    List<ClipProject> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    List<ClipProject> findBySubjectIdOrderByCreatedAtDesc(String subjectId);

    List<ClipProject> findByStatus(ProjectStatus status);
}
