package com.stemotion.domain.project.repository;

import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.domain.project.entity.ClipProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClipProjectRepository extends JpaRepository<ClipProject, String> {

    List<ClipProject> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);

    List<ClipProject> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    List<ClipProject> findByScriptStatusOrderByCreatedAtDesc(ScriptStatus scriptStatus);

    List<ClipProject> findByVideoStatusOrderByCreatedAtDesc(VideoStatus videoStatus);

    long countByWorkspaceId(String workspaceId);
}
