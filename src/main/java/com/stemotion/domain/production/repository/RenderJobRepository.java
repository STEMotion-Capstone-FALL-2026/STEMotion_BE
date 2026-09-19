package com.stemotion.domain.production.repository;

import com.stemotion.domain.production.entity.RenderJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RenderJobRepository extends JpaRepository<RenderJob, String> {

    List<RenderJob> findByProjectIdOrderByCreatedAtDesc(String projectId);

    Optional<RenderJob> findByExternalJobId(String externalJobId);
}
