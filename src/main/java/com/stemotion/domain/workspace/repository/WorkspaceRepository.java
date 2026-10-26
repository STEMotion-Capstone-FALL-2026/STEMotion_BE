package com.stemotion.domain.workspace.repository;

import com.stemotion.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, String> {
    List<Workspace> findAllByOrderByNameAsc();

    /**
     * Row lock taken by every membership change, so two concurrent changes cannot both
     * pass the role coverage check and leave the workspace without a role.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select w from Workspace w where w.id = :id")
    java.util.Optional<Workspace> lockById(@org.springframework.data.repository.query.Param("id") String id);
}
